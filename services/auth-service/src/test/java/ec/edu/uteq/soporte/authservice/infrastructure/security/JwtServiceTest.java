package ec.edu.uteq.soporte.authservice.infrastructure.security;

import ec.edu.uteq.soporte.authservice.application.InvalidTokenException;
import ec.edu.uteq.soporte.authservice.application.IssuedAccessToken;
import ec.edu.uteq.soporte.authservice.domain.PermissionCatalog;
import ec.edu.uteq.soporte.authservice.domain.Role;
import ec.edu.uteq.soporte.authservice.domain.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-1234";

    @Test
    void encodeThenDecodeRoundtripPreservesClaims() {
        JwtService jwtService = new JwtService(SECRET, 15, 7);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("cliente@test.com")
                .role(Role.CLIENTE)
                .fullName("Cliente Test")
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();

        IssuedAccessToken issued = jwtService.generateAccessToken(user);
        Claims claims = jwtService.parseAndValidate(issued.token());

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("email", String.class)).isEqualTo("cliente@test.com");
        assertThat(claims.get("role", String.class)).isEqualTo("CLIENTE");
        assertThat(claims.get("permissions", List.class)).isEqualTo(PermissionCatalog.permissionsFor(Role.CLIENTE));
        assertThat(claims.getExpiration()).isAfter(new java.util.Date());
    }

    @Test
    void includesZoneClaimForTecnicoButOmitsItForOtherRoles() {
        JwtService jwtService = new JwtService(SECRET, 15, 7);
        User tecnico = User.builder()
                .id(UUID.randomUUID())
                .email("tec@test.com")
                .role(Role.TECNICO)
                .zone("QUEVEDO_NORTE")
                .fullName("Tecnico Test")
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();
        User cliente = User.builder()
                .id(UUID.randomUUID())
                .email("cliente@test.com")
                .role(Role.CLIENTE)
                .fullName("Cliente Test")
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();

        Claims tecnicoClaims = jwtService.parseAndValidate(jwtService.generateAccessToken(tecnico).token());
        Claims clienteClaims = jwtService.parseAndValidate(jwtService.generateAccessToken(cliente).token());

        assertThat(tecnicoClaims.get("zone", String.class)).isEqualTo("QUEVEDO_NORTE");
        assertThat(clienteClaims.get("zone", String.class)).isNull();
    }

    @Test
    void expiredTokenFailsValidation() {
        JwtService jwtService = new JwtService(SECRET, -1, 7);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("cliente@test.com")
                .role(Role.CLIENTE)
                .fullName("Cliente Test")
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();

        IssuedAccessToken issued = jwtService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.parseAndValidate(issued.token()))
                .isInstanceOf(InvalidTokenException.class);
    }
}
