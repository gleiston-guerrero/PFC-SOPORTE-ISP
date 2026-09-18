package ec.edu.uteq.soporte.apigateway.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HttpMetricsGlobalFilter no tenia ninguna prueba (Entregable de reproducibilidad de
 * pruebas de la guia de cierre): su unica logica no trivial, routeOf(), reduce
 * "/api/v1/tickets/<uuid>" a "/api/v1/tickets/{id}" para no reventar la cardinalidad de
 * Prometheus, y era codigo sin ejercitar. Se prueba a traves del comportamiento publico
 * (filter()), no invocando el metodo privado por reflexion, para que la prueba siga
 * siendo valida si la implementacion interna cambia.
 */
class HttpMetricsGlobalFilterTest {

    private SimpleMeterRegistry registry;
    private HttpMetricsGlobalFilter filter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        filter = new HttpMetricsGlobalFilter(registry);
    }

    @Test
    void normalizaUnUuidEnLaRutaAUnMarcadorDePosicion() {
        ServerWebExchange exchange = exchangeCon(
                "/api/v1/tickets/0fb2c5be-3e80-46aa-af79-f2aa597f8e4a", HttpStatus.OK);

        ejecutar(exchange);

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/tickets/{id}")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void normalizaUnUuidEnMayusculasIgualQueUnoEnMinusculas() {
        ServerWebExchange exchange = exchangeCon(
                "/api/v1/tickets/0FB2C5BE-3E80-46AA-AF79-F2AA597F8E4A", HttpStatus.OK);

        ejecutar(exchange);

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/tickets/{id}")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void normalizaDosUuidsEnLaMismaRuta() {
        ServerWebExchange exchange = exchangeCon(
                "/api/v1/tickets/0fb2c5be-3e80-46aa-af79-f2aa597f8e4a/tecnicos/"
                        + "043eec99-4fe7-4ae1-9f93-77b30885c0ff",
                HttpStatus.OK);

        ejecutar(exchange);

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/tickets/{id}/tecnicos/{id}")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void dejaIntactaUnaRutaSinUuid() {
        ServerWebExchange exchange = exchangeCon("/api/v1/auth/login", HttpStatus.OK);

        ejecutar(exchange);

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/auth/login")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void noConfundeUnIdentificadorNumericoConUnUuid() {
        // Guarda contra una regresion donde el patron se relaje demasiado y empiece a
        // aceptar cualquier segmento como "un id", devolviendo la cardinalidad sin
        // limite que este filtro existe para evitar.
        ServerWebExchange exchange = exchangeCon("/api/v1/reports/2026", HttpStatus.OK);

        ejecutar(exchange);

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/reports/2026")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void etiquetaElMetodoYElCodigoDeEstadoReales() {
        ServerWebExchange exchange = exchangeCon(
                "/api/v1/notifications", HttpStatus.CREATED, "POST");

        ejecutar(exchange);

        assertThat(registry.get("http_requests_total")
                .tag("route", "/api/v1/notifications")
                .tag("method", "POST")
                .tag("status", "201")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void registraElOrdenMasBajoDePrecedenciaParaCorrerDespuesDeLosDemasFiltros() {
        assertThat(filter.getOrder()).isEqualTo(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
    }

    private ServerWebExchange exchangeCon(String path, HttpStatus status) {
        return exchangeCon(path, status, "GET");
    }

    private ServerWebExchange exchangeCon(String path, HttpStatus status, String method) {
        MockServerHttpRequest request = MockServerHttpRequest.method(
                org.springframework.http.HttpMethod.valueOf(method), path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(status);
        return exchange;
    }

    private void ejecutar(ServerWebExchange exchange) {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, chain).block();
    }
}
