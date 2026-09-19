package ec.edu.uteq.soporte.ticketservice.presentation;

import ec.edu.uteq.soporte.ticketservice.application.ForbiddenException;
import ec.edu.uteq.soporte.ticketservice.application.TicketNotFoundException;
import ec.edu.uteq.soporte.ticketservice.presentation.dto.ApiResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Prueba el traductor central de excepciones a respuestas HTTP (@RestControllerAdvice) --
 * cada handler traduce una excepcion de dominio/aplicacion al codigo de estado correcto,
 * nunca deja pasar una excepcion sin envolver en el formato estandar ApiResponse.
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
        java.util.UUID id = java.util.UUID.randomUUID();

        ResponseEntity<ApiResponse<Object>> response =
                handler.handleNotFound(new TicketNotFoundException(id));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("No se encontro el ticket " + id);
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void handleForbidden_devuelve403ConElMensajeDeLaExcepcion() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleForbidden(new ForbiddenException("No autorizado para esta zona"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("No autorizado para esta zona");
    }

    @Test
    void handleValidation_devuelve400ConLosCamposInvalidosUnidos() {
        FieldError error1 = new FieldError("ticket", "zone", "no debe estar vacio");
        FieldError error2 = new FieldError("ticket", "title", "no debe estar vacio");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ApiResponse<Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message())
                .isEqualTo("zone: no debe estar vacio; title: no debe estar vacio");
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
    void handleIllegalArgument_devuelve400ConElMensajeDeLaExcepcion() {
        // Antes de este handler, un evidencePhotoBase64 malformado (Base64.getDecoder().decode
        // en TicketController, Entregable 10) caia en handleGeneric y devolvia 500 por un dato
        // de entrada invalido, no por un fallo real del servidor.
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Illegal base64 character"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Solicitud invalida: Illegal base64 character");
    }

    @Test
    void handleGeneric_devuelve500ConElMensajeDeLaExcepcionOriginal() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleGeneric(new RuntimeException("fallo inesperado en la base de datos"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Error interno: fallo inesperado en la base de datos");
    }
}
