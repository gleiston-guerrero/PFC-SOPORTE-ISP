package ec.edu.uteq.soporte.authservice.presentation;

import ec.edu.uteq.soporte.authservice.application.AuthService;
import ec.edu.uteq.soporte.authservice.application.InvalidTokenException;
import ec.edu.uteq.soporte.authservice.application.RegisterCommand;
import ec.edu.uteq.soporte.authservice.application.TokenPair;
import ec.edu.uteq.soporte.authservice.application.TokenValidation;
import ec.edu.uteq.soporte.authservice.domain.Role;
import ec.edu.uteq.soporte.authservice.domain.User;
import ec.edu.uteq.soporte.authservice.presentation.dto.LoginRequest;
import ec.edu.uteq.soporte.authservice.presentation.dto.LogoutRequest;
import ec.edu.uteq.soporte.authservice.presentation.dto.RefreshRequest;
import ec.edu.uteq.soporte.authservice.presentation.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthController no tenia ninguna prueba propia (a diferencia de TicketController en
 * svc-principal, ya cubierto). Se prueba con AuthService mockeado, sin MockMvc ni
 * contexto de Spring -- lo que es logica propia de este controlador es el parseo del
 * header Authorization en validate() (unico endpoint que no delega el 401 a Spring
 * Security, ver comentario en la clase), el armado del ApiResponse con su mensaje, y
 * -- desde el Entregable 1 (extendido a auth-service) -- el mapeo entre los tipos de
 * application (RegisterCommand/User/TokenPair/TokenValidation) y los DTO de presentation
 * (RegisterRequest/UserResponse/AuthResponse/ValidateResponse), mismo patron que
 * TicketController mapea Ticket a TicketResponse.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController controller() {
        return new AuthController(authService);
    }

    private static User user(String email, String fullName, Role role, String zone) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .fullName(fullName)
                .role(role)
                .zone(zone)
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void registerMapeaElRequestAUnComandoYElUsuarioCreadoAUserResponse() {
        RegisterRequest request = new RegisterRequest("cliente@correo.com", "12345678", "Cliente Uno");
        User created = user("cliente@correo.com", "Cliente Uno", Role.CLIENTE, null);
        when(authService.register(new RegisterCommand("cliente@correo.com", "12345678", "Cliente Uno")))
                .thenReturn(created);

        var respuesta = controller().register(request);

        assertThat(respuesta.data().email()).isEqualTo("cliente@correo.com");
        assertThat(respuesta.data().role()).isEqualTo("CLIENTE");
        assertThat(respuesta.message()).isEqualTo("Usuario registrado");
    }

    @Test
    void loginExtraeEmailYPasswordDelRequestYMapeaElTokenPairAAuthResponse() {
        LoginRequest request = new LoginRequest("user@correo.com", "clave123");
        TokenPair pair = new TokenPair("access", "refresh", OffsetDateTime.now().plusMinutes(15));
        when(authService.login("user@correo.com", "clave123")).thenReturn(pair);

        var respuesta = controller().login(request);

        verify(authService).login("user@correo.com", "clave123");
        assertThat(respuesta.data().accessToken()).isEqualTo("access");
        assertThat(respuesta.data().refreshToken()).isEqualTo("refresh");
        assertThat(respuesta.message()).isEqualTo("Sesion iniciada");
    }

    @Test
    void refreshDelegaSoloElRefreshTokenDelRequest() {
        RefreshRequest request = new RefreshRequest("token-crudo");
        TokenPair pair = new TokenPair("access2", "refresh2", OffsetDateTime.now().plusMinutes(15));
        when(authService.refresh("token-crudo")).thenReturn(pair);

        var respuesta = controller().refresh(request);

        verify(authService).refresh("token-crudo");
        assertThat(respuesta.data().accessToken()).isEqualTo("access2");
        assertThat(respuesta.message()).isEqualTo("Token renovado");
    }

    @Test
    void logoutDelegaElRefreshTokenYRespondeSinData() {
        LogoutRequest request = new LogoutRequest("token-a-cerrar");

        var respuesta = controller().logout(request);

        verify(authService).logout("token-a-cerrar");
        assertThat(respuesta.data()).isNull();
        assertThat(respuesta.message()).isEqualTo("Sesion cerrada");
    }

    @Test
    void validateConHeaderBearerValidoLePasaSoloElTokenSinElPrefijoYMapeaAValidateResponse() {
        TokenValidation validation = new TokenValidation(
                "user-1", "user@correo.com", "TECNICO", "QUEVEDO_NORTE", List.of("tickets:read"),
                OffsetDateTime.now().plusMinutes(5));
        when(authService.validate("token-sin-prefijo")).thenReturn(validation);

        var respuesta = controller().validate("Bearer token-sin-prefijo");

        verify(authService).validate("token-sin-prefijo");
        assertThat(respuesta.data().userId()).isEqualTo("user-1");
        assertThat(respuesta.data().role()).isEqualTo("TECNICO");
        assertThat(respuesta.data().permissions()).containsExactly("tickets:read");
        assertThat(respuesta.message()).isEqualTo("Token valido");
    }

    @Test
    void validateConHeaderNuloLanzaInvalidTokenSinLlamarAlServicio() {
        assertThatThrownBy(() -> controller().validate(null))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void validateConHeaderSinPrefijoBearerLanzaInvalidToken() {
        assertThatThrownBy(() -> controller().validate("token-sin-prefijo-bearer"))
                .isInstanceOf(InvalidTokenException.class);
    }
}
