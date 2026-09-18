package ec.edu.uteq.soporte.authservice.presentation;

import ec.edu.uteq.soporte.authservice.application.DuplicateEmailException;
import ec.edu.uteq.soporte.authservice.application.InvalidCredentialsException;
import ec.edu.uteq.soporte.authservice.application.InvalidRequestException;
import ec.edu.uteq.soporte.authservice.application.InvalidTokenException;
import ec.edu.uteq.soporte.authservice.application.TokenReuseDetectedException;
import ec.edu.uteq.soporte.authservice.application.UserNotFoundException;
import ec.edu.uteq.soporte.authservice.presentation.dto.ApiResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler de auth-service no tenia ninguna prueba (a diferencia de su
 * equivalente en svc-principal, ver GlobalExceptionHandlerTest de ese modulo) -- es el
 * traductor central de seis excepciones distintas a la respuesta HTTP correcta, incluida
 * la unica logica real del archivo: unir varios errores de campo con "; " o caer a un
 * mensaje generico cuando no hay ninguno.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private MethodParameter methodParameter;

    @Mock
    private BindingResult bindingResult;

    @Test
    void handleNotFound_devuelve404ConElMensajeDeLaExcepcion() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleNotFound(new UserNotFoundException("Usuario no encontrado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Usuario no encontrado");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void handleBadRequest_conCorreoDuplicado_devuelve400() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleBadRequest(new DuplicateEmailException("El correo ya esta registrado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("El correo ya esta registrado");
    }

    @Test
    void handleBadRequest_conSolicitudInvalida_devuelve400() {
        // El mismo handler cubre dos excepciones distintas (@ExceptionHandler con array) --
        // confirmar que la segunda tambien cae aqui, no solo DuplicateEmailException.
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleBadRequest(new InvalidRequestException("TECNICO requiere una zona valida"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("TECNICO requiere una zona valida");
    }

    @Test
    void handleUnauthorized_cubreLasTresExcepcionesDeSesionConEl401() {
        // Un solo handler para tres excepciones (credenciales invalidas, token invalido,
        // reuso de token detectado) -- las tres deben caer en 401, no solo la primera.
        assertThat(handler.handleUnauthorized(new InvalidCredentialsException("Credenciales invalidas"))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleUnauthorized(new InvalidTokenException("Refresh token invalido"))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ApiResponse<Object>> reuso =
                handler.handleUnauthorized(new TokenReuseDetectedException("Se detecto reuso de un refresh token"));
        assertThat(reuso.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reuso.getBody().message()).isEqualTo("Se detecto reuso de un refresh token");
    }

    @Test
    void handleAccessDenied_devuelve403ConMensajeFijoNoElDeLaExcepcion() {
        // A diferencia de los demas handlers, este NO reenvia ex.getMessage() -- usa un
        // mensaje propio fijo. Vale la pena confirmarlo explicitamente: si alguien lo
        // cambiara a ex.getMessage() por error, podria filtrar detalles internos de
        // Spring Security en la respuesta.
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleAccessDenied(new AccessDeniedException("Access is denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("No tiene permisos para esta operacion");
    }

    @Test
    void handleValidation_devuelve400ConLosCamposInvalidosUnidos() {
        FieldError error1 = new FieldError("registerRequest", "email", "no debe estar vacio");
        FieldError error2 = new FieldError("registerRequest", "password", "no debe estar vacio");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ApiResponse<Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message())
                .isEqualTo("email: no debe estar vacio; password: no debe estar vacio");
    }

    @Test
    void handleValidation_sinErroresDeCampoUsaMensajeGenerico() {
        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ApiResponse<Object>> response = handler.handleValidation(ex);

        assertThat(response.getBody().message()).isEqualTo("Solicitud invalida");
    }

    @Test
    void handleGeneric_devuelve500ConElMensajeDeLaExcepcionOriginal() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleGeneric(new RuntimeException("fallo inesperado en la base de datos"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Error interno: fallo inesperado en la base de datos");
    }
}
