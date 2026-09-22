package ec.edu.uteq.soporte.authservice.application;

import ec.edu.uteq.soporte.authservice.domain.RefreshToken;
import ec.edu.uteq.soporte.authservice.domain.RefreshTokenRepository;
import ec.edu.uteq.soporte.authservice.domain.Role;
import ec.edu.uteq.soporte.authservice.domain.User;
import ec.edu.uteq.soporte.authservice.domain.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias puras (sin contexto de Spring ni base de datos real), igual que
 * TicketServiceTest en ticket-service: puertos de application (TokenIssuer,
 * TechnicianCreatedNotifier) mockeados, BCryptPasswordEncoder real (es barato y no vale la pena
 * mockearlo). Desde el Entregable 1 (extendido a auth-service) ya no mockea JwtService ni
 * TechnicianEventPublisher directamente -- esta prueba en si formaba parte de las 28
 * violaciones que una revision externa encontro al aplicar la regla de capas de ticket-service
 * a este servicio, porque importaba infrastructure.security.JwtService e
 * infrastructure.messaging.TechnicianEventPublisher desde application/ (el paquete de este test).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private TechnicianCreatedNotifier technicianCreatedNotifier;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, refreshTokenRepository, tokenIssuer, passwordEncoder, technicianCreatedNotifier);

        // Simulan lo que Hibernate hace de verdad al persistir (asignar el id generado);
        // lenient() porque no todos los tests ejercitan ambos repositorios.
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(UUID.randomUUID());
            }
            return token;
        });
    }

    @Test
    void registerHashesPasswordAndForcesClienteRole() {
        when(userRepository.existsByEmail("nuevo@test.com")).thenReturn(false);

        User created = authService.register(new RegisterCommand("nuevo@test.com", "Passw0rd!", "Nuevo Usuario"));

        assertThat(created.getRole()).isEqualTo(Role.CLIENTE);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("Passw0rd!");
        assertThat(passwordEncoder.matches("Passw0rd!", captor.getValue().getPasswordHash())).isTrue();
        assertThat(captor.getValue().getRole()).isEqualTo(Role.CLIENTE);
    }

    @Test
    void listUsersReturnsEveryUser() {
        User cliente = activeUser("cliente@test.com", "Passw0rd!");
        User tecnico = User.builder()
                .id(UUID.randomUUID())
                .email("tec@test.com")
                .role(Role.TECNICO)
                .zone("QUEVEDO_NORTE")
                .fullName("Tecnico Test")
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();
        when(userRepository.findAll()).thenReturn(List.of(cliente, tecnico));

        List<User> users = authService.listUsers();

        assertThat(users).hasSize(2);
        assertThat(users).extracting(User::getEmail).containsExactlyInAnyOrder("cliente@test.com", "tec@test.com");
        assertThat(users).filteredOn(u -> u.getRole() == Role.TECNICO).extracting(User::getZone)
                .containsExactly("QUEVEDO_NORTE");
    }

    @Test
    void createUserAsAdminRequiresValidZoneForTecnico() {
        when(userRepository.existsByEmail("tec@test.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.createUserAsAdmin(
                new CreateUserCommand("tec@test.com", "Passw0rd!", "Tecnico Uno", Role.TECNICO, null)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> authService.createUserAsAdmin(
                new CreateUserCommand("tec@test.com", "Passw0rd!", "Tecnico Uno", Role.TECNICO, "ZONA_INVENTADA")))
                .isInstanceOf(InvalidRequestException.class);

        User created = authService.createUserAsAdmin(
                new CreateUserCommand("tec@test.com", "Passw0rd!", "Tecnico Uno", Role.TECNICO, "QUEVEDO_NORTE"));
        assertThat(created.getZone()).isEqualTo("QUEVEDO_NORTE");
    }

    @Test
    void createUserAsAdminRejectsZoneForNonTecnicoRoles() {
        assertThatThrownBy(() -> authService.createUserAsAdmin(
                new CreateUserCommand("otro@test.com", "Passw0rd!", "Otro", Role.CLIENTE, "QUEVEDO_NORTE")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createUserAsAdminPublishesTechnicianCreatedOnlyForTecnicoRole() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        authService.createUserAsAdmin(
                new CreateUserCommand("tec2@test.com", "Passw0rd!", "Tecnico Dos", Role.TECNICO, "QUEVEDO_SUR"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(technicianCreatedNotifier).publishCreated(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("tec2@test.com");

        authService.createUserAsAdmin(
                new CreateUserCommand("cliente2@test.com", "Passw0rd!", "Cliente Dos", Role.CLIENTE, null));

        // Sigue habiendo una sola invocacion (la del TECNICO de arriba) -- CLIENTE/ADMIN
        // nunca disparan la sincronizacion con ticket-service.
        verify(technicianCreatedNotifier, org.mockito.Mockito.times(1)).publishCreated(any(User.class));
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        User user = activeUser("cliente@test.com", "Passw0rd!");
        when(userRepository.findByEmail("cliente@test.com")).thenReturn(Optional.of(user));
        stubTokenIssuance();

        TokenPair pair = authService.login("cliente@test.com", "Passw0rd!");

        assertThat(pair.accessToken()).isEqualTo("fake-access-token");
        assertThat(pair.refreshToken()).isNotBlank();
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = activeUser("cliente@test.com", "Passw0rd!");
        when(userRepository.findByEmail("cliente@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("cliente@test.com", "otra-clave"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginFailsWhenUserIsInactive() {
        User user = activeUser("cliente@test.com", "Passw0rd!");
        user.setActive(false);
        when(userRepository.findByEmail("cliente@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("cliente@test.com", "Passw0rd!"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshRotatesTokenAndRevokesThePreviousOne() {
        User user = activeUser("cliente@test.com", "Passw0rd!");
        UUID userId = user.getId();
        RefreshToken existing = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("hash-viejo")
                .issuedAt(OffsetDateTime.now().minusDays(1))
                .expiresAt(OffsetDateTime.now().plusDays(6))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        stubTokenIssuance();

        TokenPair pair = authService.refresh("token-crudo-viejo");

        assertThat(pair.accessToken()).isEqualTo("fake-access-token");
        assertThat(existing.isRevoked()).isTrue();
        assertThat(existing.getReplacedBy()).isNotNull();
    }

    @Test
    void refreshWithAlreadyRevokedTokenTriggersMassRevocation() {
        UUID userId = UUID.randomUUID();
        RefreshToken alreadyRevoked = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("hash-robado")
                .issuedAt(OffsetDateTime.now().minusDays(2))
                .expiresAt(OffsetDateTime.now().plusDays(5))
                .revoked(true)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(alreadyRevoked));

        RefreshToken otherActiveToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("otro-hash-activo")
                .issuedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findAllByUserIdAndRevokedFalse(userId)).thenReturn(List.of(otherActiveToken));

        assertThatThrownBy(() -> authService.refresh("token-robado"))
                .isInstanceOf(TokenReuseDetectedException.class);

        assertThat(otherActiveToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).saveAll(List.of(otherActiveToken));
    }

    @Test
    void registerRejectsAnEmailThatAlreadyExists() {
        // createUser() es compartida por register/createUserAsAdmin; esta prueba solo cubria
        // el camino de createUserAsAdmin (zona invalida) hasta ahora, nunca el chequeo real de
        // correo duplicado que hace la funcion de creacion en si.
        when(userRepository.existsByEmail("existente@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterCommand("existente@test.com", "Passw0rd!", "Alguien")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void loginFailsWhenTheEmailDoesNotExist() {
        // Distinto del caso "contrasena incorrecta": aqui el usuario ni siquiera existe. Debe
        // fallar con el mismo InvalidCredentialsException generico -- nunca un mensaje que
        // revele si el correo esta o no registrado.
        when(userRepository.findByEmail("nadie@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nadie@test.com", "cualquiera"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshWithAnUnknownTokenHashFails() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("token-que-nunca-se-emitio"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshWithAnExpiredButNotRevokedTokenFails() {
        RefreshToken vencido = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash-vencido")
                .issuedAt(OffsetDateTime.now().minusDays(10))
                .expiresAt(OffsetDateTime.now().minusMinutes(1))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(vencido));

        assertThatThrownBy(() -> authService.refresh("token-vencido"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void logoutRevokesAnActiveRefreshToken() {
        RefreshToken activo = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash-activo")
                .issuedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(activo));

        authService.logout("token-activo");

        assertThat(activo.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(activo);
    }

    @Test
    void logoutOnAnAlreadyRevokedTokenIsIdempotentAndDoesNotSaveAgain() {
        // Cerrar sesion dos veces (o cerrar sesion despues de un refresh que ya lo revoco) no
        // debe volver a escribir en la base -- solo evitar el trabajo redundante, no fallar.
        RefreshToken yaRevocado = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash-ya-revocado")
                .issuedAt(OffsetDateTime.now().minusDays(1))
                .expiresAt(OffsetDateTime.now().plusDays(6))
                .revoked(true)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(yaRevocado));

        authService.logout("token-ya-revocado");

        verify(refreshTokenRepository, org.mockito.Mockito.never()).save(any(RefreshToken.class));
    }

    @Test
    void logoutWithAnUnknownTokenHashFails() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout("token-desconocido"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void validateParsesClaimsIntoTokenValidation() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn("user-id-123");
        when(claims.get("email", String.class)).thenReturn("cliente@test.com");
        when(claims.get("role", String.class)).thenReturn("TECNICO");
        when(claims.get("zone", String.class)).thenReturn("QUEVEDO_NORTE");
        when(claims.get("permissions", List.class)).thenReturn(List.of("ticket:read:zone"));
        when(claims.getExpiration()).thenReturn(Date.from(OffsetDateTime.now().plusMinutes(15).toInstant()));
        when(tokenIssuer.parseAndValidate("Bearer abc.def.ghi")).thenReturn(claims);

        TokenValidation validation = authService.validate("Bearer abc.def.ghi");

        assertThat(validation.userId()).isEqualTo("user-id-123");
        assertThat(validation.role()).isEqualTo("TECNICO");
        assertThat(validation.zone()).isEqualTo("QUEVEDO_NORTE");
        assertThat(validation.permissions()).containsExactly("ticket:read:zone");
    }

    private void stubTokenIssuance() {
        when(tokenIssuer.generateAccessToken(any(User.class)))
                .thenReturn(new IssuedAccessToken("fake-access-token", OffsetDateTime.now().plusMinutes(15)));
        when(tokenIssuer.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
    }

    private User activeUser(String email, String rawPassword) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName("Test User")
                .role(Role.CLIENTE)
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
