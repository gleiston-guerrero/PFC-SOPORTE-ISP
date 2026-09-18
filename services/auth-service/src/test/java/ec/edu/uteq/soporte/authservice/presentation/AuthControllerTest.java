package ec.edu.uteq.soporte.authservice.presentation;

import ec.edu.uteq.soporte.authservice.application.AuthService;
import ec.edu.uteq.soporte.authservice.application.InvalidTokenException;
import ec.edu.uteq.soporte.authservice.presentation.dto.AuthResponse;
import ec.edu.uteq.soporte.authservice.presentation.dto.LoginRequest;
import ec.edu.uteq.soporte.authservice.presentation.dto.LogoutRequest;
import ec.edu.uteq.soporte.authservice.presentation.dto.RefreshRequest;
import ec.edu.uteq.soporte.authservice.presentation.dto.RegisterRequest;
import ec.edu.uteq.soporte.authservice.presentation.dto.UserResponse;
import ec.edu.uteq.soporte.authservice.presentation.dto.ValidateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthController no tenia ninguna prueba propia (a diferencia de TicketController en
 * svc-principal, ya cubierto). Se prueba con AuthService mockeado, sin MockMvc ni
 * contexto de Spring -- lo que es logica propia de este controlador es el parseo del
 * header Authorization en validate() (unico endpoint que no delega el 401 a Spring
 * Security, ver comentario en la clase) y el armado del ApiResponse con su mensaje.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController controller() {
        return new AuthController(authService);
    }

    @Test
    void registerDelegaElRequestCompletoYEnvuelveElResultado() {
        RegisterRequest request = new RegisterRequest("cliente@correo.com", "12345678", "Cliente Uno");
        UserResponse created = new UserResponse("id-1", "cliente@correo.com", "Cliente Uno", "CLIENTE", null, true, OffsetDateTime.now());
        when(authService.register(request)).thenReturn(created);

        var respuesta = controller().register(request);

        assertThat(respuesta.data()).isEqualTo(created);
        assertThat(respuesta.message()).isEqualTo("Usuario registrado");
    }

    @Test
    void loginExtraeEmailYPasswordDelRequestEnVezDePasarloEntero() {
        LoginRequest request = new LoginRequest("user@correo.com", "clave123");
        AuthResponse tokens = new AuthResponse("access", "refresh", OffsetDateTime.now().plusMinutes(15));
        when(authService.login("user@correo.com", "clave123")).thenReturn(tokens);

        var respuesta = controller().login(request);

        verify(authService).login("user@correo.com", "clave123");
        assertThat(respuesta.data()).isEqualTo(tokens);
        assertThat(respuesta.message()).isEqualTo("Sesion iniciada");
    }

    @Test
    void refreshDelegaSoloElRefreshTokenDelRequest() {
        RefreshRequest request = new RefreshRequest("token-crudo");
        AuthResponse tokens = new AuthResponse("access2", "refresh2", OffsetDateTime.now().plusMinutes(15));
        when(authService.refresh("token-crudo")).thenReturn(tokens);

        var respuesta = controller().refresh(request);

        verify(authService).refresh("token-crudo");
        assertThat(respuesta.data()).isEqualTo(tokens);
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
    void validateConHeaderBearerValidoLePasaSoloElTokenSinElPrefijo() {
        ValidateResponse validated = new ValidateResponse(
                "user-1", "user@correo.com", "TECNICO", "QUEVEDO_NORTE", List.of("tickets:read"), OffsetDateTime.now().plusMinutes(5));
        when(authService.validate("token-sin-prefijo")).thenReturn(validated);

        var respuesta = controller().validate("Bearer token-sin-prefijo");

        verify(authService).validate("token-sin-prefijo");
        assertThat(respuesta.data()).isEqualTo(validated);
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
