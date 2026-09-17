package ec.edu.uteq.soporte.reportservice.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
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
 * AuthGatewayFilter de report-service no tenia ninguna prueba. No es una copia
 * mecanica del de ticket-service: agrega una restriccion propia (solo ADMIN puede
 * consultar reportes, aunque el token sea valido) que no existe en ningun otro
 * servicio, asi que necesita su propia prueba, no la del hermano de svc-principal.
 * Mismo enfoque que ese hermano: un auth-service real de doble (HttpServer local), no
 * un mock del cliente HTTP.
 */
class AuthGatewayFilterTest {

    private HttpServer authServiceDouble;
    private AtomicReference<String> proximaRespuesta;
    private AtomicReference<Integer> proximoCodigoDeEstado;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        proximaRespuesta = new AtomicReference<>("{}");
        proximoCodigoDeEstado = new AtomicReference<>(200);

        authServiceDouble = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        authServiceDouble.createContext("/validate", exchange -> {
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
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/reports/summary");

        assertThat(filtro().shouldNotFilter(request)).isTrue();
    }

    @Test
    void dejaPasarRutasQueNoEmpiezanConApiV1Reports() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");

        assertThat(filtro().shouldNotFilter(request)).isTrue();
    }

    @Test
    void rechazaConCuandoFaltaElEncabezadoAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filtro().doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void unTecnicoConTokenValidoRecibeProhibidoNoAutorizado() throws Exception {
        // La restriccion propia de este filtro: un token valido no basta, el rol tiene
        // que ser ADMIN. Un TECNICO con sesion real debe recibir 403, no 401 -- son
        // errores distintos (no autenticado vs. autenticado pero sin permiso) y un
        // cliente que solo mira el codigo de estado necesita distinguirlos.
        proximaRespuesta.set(respuestaValidaJson("TECNICO"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports/summary");
        request.addHeader("Authorization", "Bearer token-de-tecnico-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filtro().doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("requiere el rol ADMIN");
    }

    @Test
    void unClienteConTokenValidoTambienRecibeProhibido() throws Exception {
        proximaRespuesta.set(respuestaValidaJson("CLIENTE"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports/tickets");
        request.addHeader("Authorization", "Bearer token-de-cliente-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filtro().doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void unAdminConTokenValidoDejaPasar() throws Exception {
        proximaRespuesta.set(respuestaValidaJson("ADMIN"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports/summary");
        request.addHeader("Authorization", "Bearer token-de-admin-valido");
        MockFilterChain chain = new MockFilterChain();

        filtro().doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void rechazaCuandoAuthServiceRespondeSinData() throws Exception {
        proximaRespuesta.set("{\"data\":null,\"message\":\"Token invalido\"}");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports/summary");
        request.addHeader("Authorization", "Bearer token-vencido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filtro().doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rechazaCuandoAuthServiceNoResponde() throws Exception {
        authServiceDouble.stop(0);
        AuthGatewayFilter filtroConAuthServiceCaido = filtro();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reports/summary");
        request.addHeader("Authorization", "Bearer token-cualquiera");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filtroConAuthServiceCaido.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    private String respuestaValidaJson(String role) {
        return "{\"data\":{\"userId\":\"" + UUID.randomUUID() + "\",\"email\":\"t@uteq.edu.ec\",\"role\":\""
                + role + "\",\"zone\":null},\"message\":\"OK\"}";
    }
}
