package ec.edu.uteq.soporte.reportservice.infrastructure.metrics;

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
 * HttpMetricsFilter no tenia ninguna prueba en ninguno de los tres servicios que lo
 * copian (ver el comentario "Misma implementacion que auth-service/ticket-service" en
 * esta misma clase); svc-principal tiene la prueba hermana con el hallazgo completo
 * documentado (el fallback a la URI cruda sin normalizar cuando una peticion no
 * matchea ninguna ruta). Aqui se ejercita la misma logica compartida con ejemplos
 * reales del API de report-service.
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
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports/summary");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/reports/summary");
        MockHttpServletResponse response = respuestaCon(200);

        filter.doFilter(request, response, cadenaQueNoHaceNada());

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/reports/summary")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void cuandoLaPeticionNoMatcheaNingunaRutaCaeALaUriCrudaSinNormalizar() throws Exception {
        // Mismo hallazgo que en svc-principal/HttpMetricsFilterTest: sin un handler
        // resuelto, la ruta cruda entra sin normalizar a la etiqueta de Prometheus.
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/v1/reports/no-existe-este-endpoint");
        MockHttpServletResponse response = respuestaCon(404);

        filter.doFilter(request, response, cadenaQueNoHaceNada());

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/reports/no-existe-este-endpoint")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void registraLaMetricaAunSiLaCadenaDeFiltrosLanzaUnaExcepcion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports/export.csv");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/reports/export.csv");
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
                .tag("route", "/api/v1/reports/export.csv")
                .tag("status", "500")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void etiquetaElMetodoHttpReal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports/tickets");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/reports/tickets");
        MockHttpServletResponse response = respuestaCon(200);

        filter.doFilter(request, response, cadenaQueNoHaceNada());

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/reports/tickets")
                .tag("method", "GET")
                .tag("status", "200")
                .counter().count()).isEqualTo(1.0);
    }

    private MockHttpServletResponse respuestaCon(int status) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        return response;
    }

    private FilterChain cadenaQueNoHaceNada() {
        return (req, res) -> { };
    }
}
