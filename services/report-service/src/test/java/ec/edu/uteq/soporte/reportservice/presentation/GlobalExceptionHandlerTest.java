package ec.edu.uteq.soporte.reportservice.presentation;

import ec.edu.uteq.soporte.reportservice.application.ForbiddenException;
import ec.edu.uteq.soporte.reportservice.presentation.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler de report-service no tenia ninguna prueba: el traductor central de
 * ForbiddenException (un CLIENTE/TECNICO sin rol ADMIN llegando a /api/v1/reports/**, ver
 * AuthGatewayFilter de este mismo servicio) y de cualquier otra excepcion no anticipada.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleForbidden_devuelve403ConElMensajeDeLaExcepcion() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleForbidden(new ForbiddenException("Solo ADMIN puede consultar reportes"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("Solo ADMIN puede consultar reportes");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void handleGeneric_devuelve500ConElMensajeDeLaExcepcionOriginal() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleGeneric(new RuntimeException("fallo inesperado consultando ticket_summary"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message())
                .isEqualTo("Error interno: fallo inesperado consultando ticket_summary");
    }
}
