package ec.edu.uteq.soporte.authservice.infrastructure.persistence;

import ec.edu.uteq.soporte.authservice.domain.RefreshToken;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RefreshTokenMapper no tenia ninguna prueba: el mismo tipo de mapeo campo a campo entre
 * dominio puro y entidad JPA que ya causo un bug real en ticket-service (ver TicketMapper,
 * Entregable 10), pero aqui el riesgo es de seguridad, no solo de datos en blanco --
 * `revoked` es el unico mecanismo que impide reusar un refresh token ya rotado o robado
 * (ver TokenReuseDetectedException); si este campo se perdiera en el mapeo, un token
 * revocado se recargaria desde la base como si nunca lo hubiera sido.
 */
class RefreshTokenMapperTest {

    private final RefreshTokenMapper mapper = new RefreshTokenMapper();

    @Test
    void toEntityLuegoToDomainPreservaTodosLosCamposSinPerdida() {
        RefreshToken original = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("a1b2c3d4e5f6")
                .issuedAt(OffsetDateTime.parse("2026-09-17T05:18:52.933Z"))
                .expiresAt(OffsetDateTime.parse("2026-09-24T05:18:52.933Z"))
                .revoked(true)
                .replacedBy(UUID.randomUUID())
                .build();

        RefreshToken recuperado = mapper.toDomain(mapper.toEntity(original));

        assertThat(recuperado).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void unTokenRevocadoSigueRevocadoDespuesDelViajeDeIdaYVuelta() {
        // La prueba de arriba ya lo cubre indirectamente, pero esta lo hace explicito: es
        // el escenario de seguridad real, no un detalle incidental del mapeo.
        RefreshToken revocado = tokenBase(true, UUID.randomUUID());

        RefreshToken recuperado = mapper.toDomain(mapper.toEntity(revocado));

        assertThat(recuperado.isRevoked()).isTrue();
    }

    @Test
    void unTokenSinRotarMantieneReplacedByNulo() {
        RefreshToken sinRotar = tokenBase(false, null);

        RefreshToken recuperado = mapper.toDomain(mapper.toEntity(sinRotar));

        assertThat(recuperado.getReplacedBy()).isNull();
        assertThat(recuperado.isRevoked()).isFalse();
    }

    @Test
    void toDomainConNullDevuelveNullEnVezDeLanzar() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    void toEntityConNullDevuelveNullEnVezDeLanzar() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    private RefreshToken tokenBase(boolean revoked, UUID replacedBy) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash-de-prueba")
                .issuedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(revoked)
                .replacedBy(replacedBy)
                .build();
    }
}
