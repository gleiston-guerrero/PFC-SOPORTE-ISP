package ec.edu.uteq.soporte.authservice.infrastructure.security;

import ec.edu.uteq.soporte.authservice.domain.Role;
import ec.edu.uteq.soporte.authservice.domain.User;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * JwtAuthenticationFilter no tenia ninguna prueba: JwtService (a quien delega el
 * parseo) si estaba probado, pero no el filtro que puebla el SecurityContext a partir
 * de sus claims -- la pieza real que hace que @PreAuthorize("hasRole('ADMIN')")
 * funcione en AdminUserController. Se usa un JwtService real (mismo patron que
 * JwtServiceTest), no uno simulado, para no probar contra una version idealizada del
 * colaborador.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-1234";

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 15, 7);
        filter = new JwtAuthenticationFilter(jwtService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        // SecurityContextHolder usa un ThreadLocal: si una prueba queda con contexto
        // puesto, contamina la siguiente que corra en el mismo hilo.
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinEncabezadoAuthorizationNoAutenticaYDejaContinuarLaCadena() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void unEncabezadoSinBearerNoAutentica() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/admin/users");
        request.addHeader("Authorization", "Basic dXN1YXJpbzpjbGF2ZQ==");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void unTokenValidoPueblaElContextoConElRolCorrecto() throws Exception {
        User admin = usuario(Role.ADMIN);
        String token = jwtService.generateAccessToken(admin).token();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/admin/users");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo(admin.getId().toString());
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        verify(chain).doFilter(request, response);
    }

    @Test
    void unTokenDeTecnicoObtieneRoleTecnicoNoAdmin() throws Exception {
        // Guarda contra una regresion donde el rol se lea mal y cualquier usuario
        // autenticado termine con privilegios de administrador.
        User tecnico = usuario(Role.TECNICO);
        String token = jwtService.generateAccessToken(tecnico).token();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilterInternal(request, new MockHttpServletResponse(), mock(FilterChain.class));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_TECNICO");
    }

    @Test
    void unTokenInvalidoNoAutenticaYNoPropagaLaExcepcion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/admin/users");
        request.addHeader("Authorization", "Bearer esto-no-es-un-jwt-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void unTokenExpiradoNoAutentica() throws Exception {
        JwtService jwtServiceQueEmiteVencidos = new JwtService(SECRET, -1, 7);
        String tokenVencido = jwtServiceQueEmiteVencidos.generateAccessToken(usuario(Role.CLIENTE)).token();
        JwtAuthenticationFilter filtroConMismoSecreto = new JwtAuthenticationFilter(jwtService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
        request.addHeader("Authorization", "Bearer " + tokenVencido);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filtroConMismoSecreto.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void unTokenFirmadoConOtroSecretoNoAutentica() throws Exception {
        // El escenario que este filtro mas necesita rechazar: alguien fabrica un JWT
        // con la forma correcta pero sin conocer el secreto real de firma.
        JwtService jwtServiceConOtroSecreto = new JwtService(
                "otro-secreto-distinto-de-al-menos-32-bytes-de-largo", 15, 7);
        String tokenFalsificado = jwtServiceConOtroSecreto.generateAccessToken(usuario(Role.ADMIN)).token();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/admin/users");
        request.addHeader("Authorization", "Bearer " + tokenFalsificado);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    private User usuario(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("prueba@uteq.edu.ec")
                .role(role)
                .fullName("Usuario de prueba")
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
