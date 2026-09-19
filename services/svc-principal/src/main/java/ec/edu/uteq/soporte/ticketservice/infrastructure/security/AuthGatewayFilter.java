package ec.edu.uteq.soporte.ticketservice.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.soporte.ticketservice.domain.Zone;
import ec.edu.uteq.soporte.ticketservice.presentation.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Exige un access token valido (emitido por auth-service) en cada llamada a
 * /api/v1/tickets/**. En vez de verificar la firma del JWT aqui mismo, delega la
 * validacion a auth-service llamando a su endpoint GET /validate -- asi el secreto
 * de firma nunca sale de auth-service y la logica de validacion vive en un solo
 * lugar. Trade-off aceptado: ticket-service ahora depende en tiempo real de que
 * auth-service este arriba.
 *
 * Vive en infrastructure/security porque es un detalle de transporte HTTP (un
 * Servlet Filter), no logica de dominio ni de aplicacion.
 */
@Component
public class AuthGatewayFilter extends OncePerRequestFilter {

    // Entregable 11 de la guia de cierre: la evidencia de integracion movil-backend no
    // emparejaba la captura de la app con ninguna linea de acceso real (metodo, ruta, codigo
    // de estado de la peticion HTTP) -- solo con la sentencia SQL que esa peticion disparaba
    // rio abajo dentro de ticket-service. Este logger cierra ese hueco: al usar SLF4J con
    // logstash-logback-encoder (ya configurado en logback-spring.xml), la linea JSON que
    // produce lleva el mismo campo "trace_id" del MDC que ya llevaba la linea de Hibernate SQL
    // -- ambas lineas de un mismo request quedan correlacionables por trace_id, no solo por
    // cercania temporal.
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ticket-service.access");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String validateUrl;

    public AuthGatewayFilter(ObjectMapper objectMapper,
                              @Value("${auth.service.base-url}") String authServiceBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
        this.validateUrl = authServiceBaseUrl + "/validate";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith("/api/v1/tickets");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "Se requiere iniciar sesion (encabezado Authorization ausente)");
            return;
        }

        try {
            ApiResponse<ValidateResponse> validated = restClient.get()
                    .uri(validateUrl)
                    .header("Authorization", header)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<ValidateResponse>>() {
                    });

            if (validated == null || validated.data() == null) {
                writeUnauthorized(response, "Token invalido");
                return;
            }

            request.setAttribute("authUserId", UUID.fromString(validated.data().userId()));
            request.setAttribute("authRole", validated.data().role());
            request.setAttribute("authZone", parseZone(validated.data().zone()));
            try {
                filterChain.doFilter(request, response);
            } finally {
                ACCESS_LOG.info("{} {} -> {} (userId={}, role={})",
                        request.getMethod(), request.getRequestURI(), response.getStatus(),
                        validated.data().userId(), validated.data().role());
            }
        } catch (RestClientException e) {
            writeUnauthorized(response, "Token invalido o auth-service no disponible: " + e.getMessage());
        }
    }

    // Valor invalido/desconocido se trata como "sin zona" (fail-closed): un TECNICO
    // sin zona reconocible no obtiene acceso amplio, ver TicketService.
    private Zone parseZone(String zoneClaim) {
        if (zoneClaim == null || zoneClaim.isBlank()) {
            return null;
        }
        try {
            return Zone.valueOf(zoneClaim);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), ApiResponse.of(null, message));
    }
}
