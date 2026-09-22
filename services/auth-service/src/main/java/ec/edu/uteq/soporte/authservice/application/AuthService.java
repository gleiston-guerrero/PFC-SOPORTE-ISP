package ec.edu.uteq.soporte.authservice.application;

import ec.edu.uteq.soporte.authservice.domain.RefreshToken;
import ec.edu.uteq.soporte.authservice.domain.RefreshTokenRepository;
import ec.edu.uteq.soporte.authservice.domain.Role;
import ec.edu.uteq.soporte.authservice.domain.User;
import ec.edu.uteq.soporte.authservice.domain.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orquesta todos los casos de uso de autenticacion y gestion de cuentas. A diferencia
 * de ticket-service (un handler de aplicacion por comando, ver ADR-0005), aqui se
 * mantiene un unico servicio de aplicacion: el numero real de casos de uso de este
 * microservicio (7, todos sobre el mismo agregado User/RefreshToken, sin cadena de
 * escalado ni observadores) es mucho menor que en ticket-service, y dividirlo en 7
 * clases de un solo metodo no habria aportado una separacion de responsabilidades
 * real -- es una decision de diseno declarada explicitamente (Seccion
 * "Arquitectura hexagonal extendida" del manuscrito), no una capa omitida por
 * descuido. Lo que si se aplico, igual que en ticket-service (Entregable 1 de la guia de
 * cierre, extendido aqui): el dominio (User, RefreshToken) ya no conoce JPA; este servicio ya
 * no depende de Spring Data directamente, solo de los puertos domain/UserRepository y
 * domain/RefreshTokenRepository; y ya no depende de infrastructure (TokenIssuer/
 * TechnicianCreatedNotifier son puertos de application, implementados por
 * JwtService/TechnicianEventPublisher) ni de presentation (recibe y devuelve tipos propios de
 * application -- RegisterCommand/CreateUserCommand/TokenPair/TokenValidation/User -- y el
 * controlador mapea hacia/desde los DTO de presentation, mismo patron que TicketController).
 */
@Service
public class AuthService {

    // Debe coincidir exactamente con el enum Zone de ticket-service.
    private static final Set<String> VALID_ZONES = Set.of("QUEVEDO_CENTRO", "QUEVEDO_NORTE", "QUEVEDO_SUR");

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final TechnicianCreatedNotifier technicianCreatedNotifier;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        TokenIssuer tokenIssuer,
                        PasswordEncoder passwordEncoder,
                        TechnicianCreatedNotifier technicianCreatedNotifier) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenIssuer = tokenIssuer;
        this.passwordEncoder = passwordEncoder;
        this.technicianCreatedNotifier = technicianCreatedNotifier;
    }

    @Transactional
    public User register(RegisterCommand command) {
        return createUser(command.email(), command.password(), command.fullName(), Role.CLIENTE, null);
    }

    @Transactional
    public User createUserAsAdmin(CreateUserCommand command) {
        validateZoneForRole(command.role(), command.zone());
        User created = createUser(
                command.email(), command.password(), command.fullName(), command.role(), command.zone());
        if (command.role() == Role.TECNICO) {
            // Publicar DESPUES de que la transaccion de creacion ya completo en memoria --
            // si Kafka falla, technicianCreatedNotifier.publishCreated ya absorbe el error
            // (ver su implementacion) y el alta del usuario en auth_db de todas formas es valida.
            technicianCreatedNotifier.publishCreated(created);
        }
        return created;
    }

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    // TECNICO necesita una zona valida (para poder filtrar "tickets de mi zona" en
    // ticket-service via el claim del JWT); los demas roles no deben traer una,
    // para no dejar datos ambiguos/inconsistentes en la fila del usuario.
    private void validateZoneForRole(Role role, String zone) {
        if (role == Role.TECNICO) {
            if (zone == null || zone.isBlank() || !VALID_ZONES.contains(zone)) {
                throw new InvalidRequestException(
                        "TECNICO requiere una zona valida: " + String.join(", ", VALID_ZONES));
            }
        } else if (zone != null && !zone.isBlank()) {
            throw new InvalidRequestException("Solo TECNICO puede tener una zona asignada");
        }
    }

    private User createUser(String email, String rawPassword, String fullName, Role role, String zone) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("El correo ya esta registrado: " + email);
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .role(role)
                .zone(zone)
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();
        return userRepository.save(user);
    }

    @Transactional
    public TokenPair login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales invalidas"));
        if (!user.isActive()) {
            throw new InvalidCredentialsException("La cuenta esta inactiva");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciales invalidas");
        }
        return issueTokenPair(user);
    }

    // noRollbackFor: si se detecta reuso, SI se debe conservar la revocacion masiva
    // hecha en revokeAllForUser() aunque el metodo termine lanzando una excepcion --
    // de lo contrario Spring revertiria toda la transaccion (comportamiento por
    // defecto ante un RuntimeException) y la revocacion nunca llegaria a la base.
    @Transactional(noRollbackFor = TokenReuseDetectedException.class)
    public TokenPair refresh(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token invalido"));

        if (existing.isRevoked()) {
            // El token ya fue rotado/revocado antes: esto es reuso, tratar como robo.
            revokeAllForUser(existing.getUserId());
            throw new TokenReuseDetectedException(
                    "Se detecto reuso de un refresh token ya revocado; se cerraron todas las sesiones del usuario");
        }
        if (existing.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException("Refresh token expirado");
        }

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado"));

        IssuedPair issued = issueTokenPairInternal(user);

        existing.setRevoked(true);
        existing.setReplacedBy(issued.refreshTokenId());
        refreshTokenRepository.save(existing);

        return issued.pair();
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token invalido"));
        if (!existing.isRevoked()) {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
        }
    }

    public TokenValidation validate(String bearerToken) {
        Claims claims = tokenIssuer.parseAndValidate(bearerToken);
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions", List.class);
        return new TokenValidation(
                claims.getSubject(),
                claims.get("email", String.class),
                claims.get("role", String.class),
                claims.get("zone", String.class),
                permissions,
                OffsetDateTime.ofInstant(claims.getExpiration().toInstant(), java.time.ZoneOffset.UTC));
    }

    private TokenPair issueTokenPair(User user) {
        return issueTokenPairInternal(user).pair();
    }

    private IssuedPair issueTokenPairInternal(User user) {
        IssuedAccessToken accessToken = tokenIssuer.generateAccessToken(user);
        String rawRefreshToken = generateOpaqueToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hashToken(rawRefreshToken))
                .issuedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plus(tokenIssuer.refreshTokenTtl()))
                .revoked(false)
                .build();
        refreshToken = refreshTokenRepository.save(refreshToken);

        TokenPair pair = new TokenPair(accessToken.token(), rawRefreshToken, accessToken.expiresAt());
        return new IssuedPair(pair, refreshToken.getId());
    }

    private record IssuedPair(TokenPair pair, UUID refreshTokenId) {
    }

    private void revokeAllForUser(UUID userId) {
        List<RefreshToken> active = refreshTokenRepository.findAllByUserIdAndRevokedFalse(userId);
        active.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(active);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
