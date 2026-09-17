package ec.edu.uteq.soporte.ticketservice.infrastructure.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * HttpMetricsFilter no tenia ninguna prueba (mismo hueco que HttpMetricsGlobalFilter en
 * api-gateway, cerrado en un commit hermano de este). Se prueba contra su comportamiento
 * publico (doFilter) con un SimpleMeterRegistry real, no un mock del registro.
 */
class HttpMetricsFilterTest {

    private SimpleMeterRegistry registry;
    private HttpMetricsFilter filter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        filter = new HttpMetricsFilter(registry);
    }

    @Test
    void usaElPatronDeLaRutaCuandoElHandlerLoEstablece() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/v1/tickets/0fb2c5be-3e80-46aa-af79-f2aa597f8e4a");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/tickets/{id}");
        MockHttpServletResponse response = respuestaCon(200);

        filter.doFilter(request, response, cadenaQueNoHaceNada());

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/tickets/{id}")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void cuandoLaPeticionNoMatcheaNingunaRutaCaeALaUriCrudaSinNormalizar() throws Exception {
        // Documenta el hallazgo, no lo oculta: a diferencia de HttpMetricsGlobalFilter (que
        // normaliza CUALQUIER UUID en la ruta via regex, matchee o no), este filtro solo
        // normaliza cuando Spring MVC ya resolvio un handler. Una peticion que no matchea
        // nada (404, o un intento de sondeo con ids al azar) cae a getRequestURI() sin
        // normalizar -- exactamente la cardinalidad sin limite que el filtro dice evitar.
        // Se deja como caracterizacion del comportamiento real, no como aserto de que sea
        // deseable; si se corrige, esta prueba debe actualizarse junto con el fix.
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/v1/tickets/no-existe-esta-ruta");
        MockHttpServletResponse response = respuestaCon(404);

        filter.doFilter(request, response, cadenaQueNoHaceNada());

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/tickets/no-existe-esta-ruta")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void registraLaMetricaAunSiLaCadenaDeFiltrosLanzaUnaExcepcion() throws Exception {
        // El registro de metricas vive en el bloque finally: una peticion que termina en
        // error real (excepcion no controlada corriente abajo) tiene que seguir contando
        // para las metricas -- si no, los picos de error serian invisibles justo cuando
        // mas importa verlos.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tickets");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/tickets");
        MockHttpServletResponse response = respuestaCon(500);
        FilterChain cadenaQueFalla = mock(FilterChain.class);
        doAnswer(inv -> { throw new IllegalStateException("fallo simulado corriente abajo"); })
                .when(cadenaQueFalla).doFilter(request, response);

        try {
            filter.doFilter(request, response, cadenaQueFalla);
        } catch (IllegalStateException esperada) {
            // se espera: el filtro no debe tragarse la excepcion, solo medir alrededor de ella
        }

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/tickets")
                .tag("status", "500")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void etiquetaElMetodoHttpReal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/tickets/x");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/tickets/{id}");
        MockHttpServletResponse response = respuestaCon(204);

        filter.doFilter(request, response, cadenaQueNoHaceNada());

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/tickets/{id}")
                .tag("method", "DELETE")
                .tag("status", "204")
                .counter().count()).isEqualTo(1.0);
    }

    private MockHttpServletResponse respuestaCon(int status) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        return response;
    }

    private FilterChain cadenaQueNoHaceNada() {
        // La respuesta ya trae el status que fija cada prueba; no hace falta simular nada
        // corriente abajo.
        return (req, res) -> { };
    }
}
