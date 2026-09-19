package ec.edu.uteq.soporte.ticketservice.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import ec.edu.uteq.soporte.ticketservice.domain.Zone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthGatewayFilter no tenia ninguna prueba a pesar de ser el unico punto que exige
 * autenticacion sobre /api/v1/tickets/** (Entregable de reproducibilidad de pruebas de
 * la guia de cierre). Se prueba contra un auth-service real, no un mock de RestClient:
 * un com.sun.net.httpserver.HttpServer local hace de doble de auth-service (mismo
 * contrato HTTP que production, sin bibliotecas adicionales), y el filtro se construye
 * apuntando a su puerto -- el constructor de AuthGatewayFilter ya acepta la URL base
 * como parametro, asi que no hizo falta tocar el codigo de produccion para volverlo
 * comprobable.
 */
class AuthGatewayFilterTest {

    private HttpServer authServiceDouble;
    private AtomicReference<String> ultimoAuthorizationRecibido;
    private AtomicReference<String> proximaRespuesta;
    private AtomicReference<Integer> proximoCodigoDeEstado;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        ultimoAuthorizationRecibido = new AtomicReference<>();
        proximaRespuesta = new AtomicReference<>("{}");
        proximoCodigoDeEstado = new AtomicReference<>(200);

        authServiceDouble = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        authServiceDouble.createContext("/validate", exchange -> {
            ultimoAuthorizationRecibido.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = proximaRespuesta.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(proximoCodigoDeEstado.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        authServiceDouble.start();
    }

    @AfterEach
    void tearDown() {
        authServiceDouble.stop(0);
    }

    private AuthGatewayFilter filtro() {
        String baseUrl = "http://localhost:" + authServiceDouble.getAddress().getPort();
        return new AuthGatewayFilter(objectMapper, baseUrl);
    }

    @Test
    void dejaPasarUnaPeticionOptionsSinExigirToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/tickets");

        assertThat(filtro().shouldNotFilter(request)).isTrue();
    }

    @Test
    void dejaPasarRutasQueNoEmpiezanConApiV1Tickets() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");

        assertThat(filtro().shouldNotFilter(request)).isTrue();
    }

    @Test
    void noDejaPasarUnGetATickets() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");

        assertThat(filtro().shouldNotFilter(request)).isFalse();
    }

    @Test
    void rechazaConCuandoFaltaElEncabezadoAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filtro().doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("Se requiere iniciar sesion");
    }

    @Test
    void rechazaCuandoElEncabezadoNoEmpiezaConBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
        request.addHeader("Authorization", "Basic dXN1YXJpbzpjbGF2ZQ==");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filtro().doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void reenviaElEncabezadoAuthorizationExactoAAuthService() throws Exception {
        proximaRespuesta.set(respuestaValidaJson(Zone.QUEVEDO_NORTE));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
        request.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.token-de-prueba");

        filtro().doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(ultimoAuthorizationRecibido.get()).isEqualTo("Bearer eyJhbGciOiJIUzI1NiJ9.token-de-prueba");
    }

    @Test
    void conUnTokenValidoDejaPasarYPoblaLosAtributosDeAutenticacion() throws Exception {
        UUID userId = UUID.randomUUID();
        proximaRespuesta.set(respuestaValidaJson(userId, "TECNICO", "QUEVEDO_SUR"));
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/tickets/" + UUID.randomUUID());
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filtro().doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute("authUserId")).isEqualTo(userId);
        assertThat(request.getAttribute("authRole")).isEqualTo("TECNICO");
        assertThat(request.getAttribute("authZone")).isEqualTo(Zone.QUEVEDO_SUR);
    }

    @Test
    void unaPeticionAutorizadaEscribeLaLineaDeAccesoConMetodoRutaEstadoYUsuario() throws Exception {
        // Entregable 11 de la guia de cierre: la evidencia de integracion movil-backend no
        // tenia ninguna linea de acceso real (metodo/ruta/codigo de estado) que emparejar con
        // la sentencia SQL disparada rio abajo -- solo cercania temporal. Esta prueba confirma
        // que esa linea ahora existe de verdad y lleva los campos que hacen falta para
        // reconstruir que peticion la produjo.
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("ticket-service.access");
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            UUID userId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            proximaRespuesta.set(respuestaValidaJson(userId, "TECNICO", "QUEVEDO_SUR"));
            MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/tickets/" + ticketId);
            request.addHeader("Authorization", "Bearer token-valido");
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setStatus(200);

            filtro().doFilterInternal(request, response, new MockFilterChain());

            assertThat(appender.list).hasSize(1);
            String linea = appender.list.get(0).getFormattedMessage();
            assertThat(linea)
                    .contains("PATCH")
                    .contains("/api/v1/tickets/" + ticketId)
                    .contains("200")
                    .contains(userId.toString())
                    .contains("TECNICO");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void unaZonaDesconocidaSeTrataComoSinZonaEnVezDeFallar() throws Exception {
        // Fail-closed deliberado (ver comentario de parseZone en produccion): una zona que
        // el enum no reconoce no debe tumbar la peticion con un error de deserializacion,
        // debe degradar a "sin zona", que TicketService trata como el acceso mas restringido.
        UUID userId = UUID.randomUUID();
        proximaRespuesta.set(respuestaValidaJson(userId, "TECNICO", "ZONA_QUE_YA_NO_EXISTE"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
        request.addHeader("Authorization", "Bearer token-valido");
        MockFilterChain chain = new MockFilterChain();

        filtro().doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute("authZone")).isNull();
    }

    @Test
    void unClienteSinZonaNoRompeLaAutenticacion() throws Exception {
        UUID userId = UUID.randomUUID();
        proximaRespuesta.set(respuestaValidaJson(userId, "CLIENTE", null));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
        request.addHeader("Authorization", "Bearer token-valido");
        MockFilterChain chain = new MockFilterChain();

        filtro().doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute("authRole")).isEqualTo("CLIENTE");
        assertThat(request.getAttribute("authZone")).isNull();
    }

    @Test
    void rechazaCuandoAuthServiceRespondeSinData() throws Exception {
        proximaRespuesta.set("{\"data\":null,\"message\":\"Token invalido\"}");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
        request.addHeader("Authorization", "Bearer token-vencido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filtro().doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rechazaCuandoAuthServiceRespondeUnErrorHttp() throws Exception {
        proximoCodigoDeEstado.set(500);
        proximaRespuesta.set("{\"data\":null,\"message\":\"error interno\"}");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
        request.addHeader("Authorization", "Bearer token-cualquiera");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filtro().doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("Token invalido o auth-service no disponible");
    }

    @Test
    void rechazaCuandoAuthServiceNoResponde() throws Exception {
        // auth-service caido por completo (no solo un error HTTP): la conexion misma
        // falla. El trade-off documentado en el filtro ("depende en tiempo real de que
        // auth-service este arriba") debe fallar cerrado (401), nunca dejar pasar.
        authServiceDouble.stop(0);
        AuthGatewayFilter filtroConAuthServiceCaido = filtro();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
        request.addHeader("Authorization", "Bearer token-cualquiera");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filtroConAuthServiceCaido.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    private String respuestaValidaJson(Zone zone) {
        return respuestaValidaJson(UUID.randomUUID(), "TECNICO", zone.name());
    }

    private String respuestaValidaJson(UUID userId, String role, String zone) {
        String zoneJson = zone == null ? "null" : "\"" + zone + "\"";
        return "{\"data\":{\"userId\":\"" + userId + "\",\"email\":\"t@uteq.edu.ec\",\"role\":\""
                + role + "\",\"zone\":" + zoneJson + "},\"message\":\"OK\"}";
    }
}
