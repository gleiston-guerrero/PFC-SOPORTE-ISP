package ec.edu.uteq.soporte.ticketservice.infrastructure.persistence;

import ec.edu.uteq.soporte.ticketservice.domain.Category;
import ec.edu.uteq.soporte.ticketservice.domain.Priority;
import ec.edu.uteq.soporte.ticketservice.domain.Ticket;
import ec.edu.uteq.soporte.ticketservice.domain.TicketStatus;
import ec.edu.uteq.soporte.ticketservice.domain.Zone;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TicketMapper no tenia ninguna prueba: un mapeo campo a campo entre el dominio puro y su
 * entidad JPA es exactamente el tipo de codigo que se rompe en silencio cuando se agrega un
 * campo nuevo a un lado y se olvida el otro -- ya paso una vez de verdad con los campos de
 * evidencia (evidencePhoto/evidenceLatitude/evidenceLongitude, Entregable 10 de la guia de
 * cierre), que se agregaron a Ticket y TicketJpaEntity por separado.
 */
class TicketMapperTest {

    private final TicketMapper mapper = new TicketMapper();

    @Test
    void toEntityLuegoToDomainPreservaTodosLosCamposSinPerdida() {
        Ticket original = Ticket.builder()
                .id(UUID.randomUUID())
                .createdAt(OffsetDateTime.parse("2026-09-16T05:18:52.933Z"))
                .zone(Zone.QUEVEDO_NORTE)
                .clientId(UUID.randomUUID())
                .technicianId(UUID.randomUUID())
                .category(Category.HARDWARE)
                .priority(Priority.MEDIO)
                .status(TicketStatus.RESUELTO)
                .description("Router sin luz de enlace")
                .slaDeadline(OffsetDateTime.parse("2026-09-17T05:18:52.933Z"))
                .resolvedAt(OffsetDateTime.parse("2026-09-16T05:22:23.950Z"))
                .slaBreached(false)
                .evidencePhoto(new byte[]{1, 2, 3, 4, 5})
                .evidenceLatitude(-1.0123905)
                .evidenceLongitude(-79.4651736)
                .build();

        Ticket recuperado = mapper.toDomain(mapper.toEntity(original));

        assertThat(recuperado).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void unTicketSinCampoDeEvidenciaLosMantieneNulos() {
        // El caso normal (cualquier transicion que no sea el cierre en sitio): los tres
        // campos de evidencia deben seguir siendo null a traves del mapeo, no un array
        // vacio ni un 0.0 que parezca un dato real.
        Ticket sinEvidencia = Ticket.builder()
                .id(UUID.randomUUID())
                .createdAt(OffsetDateTime.now())
                .zone(Zone.QUEVEDO_SUR)
                .clientId(UUID.randomUUID())
                .status(TicketStatus.NUEVO)
                .description("Sin evidencia todavia")
                .slaDeadline(OffsetDateTime.now().plusHours(24))
                .slaBreached(false)
                .build();

        Ticket recuperado = mapper.toDomain(mapper.toEntity(sinEvidencia));

        assertThat(recuperado.getEvidencePhoto()).isNull();
        assertThat(recuperado.getEvidenceLatitude()).isNull();
        assertThat(recuperado.getEvidenceLongitude()).isNull();
    }

    @Test
    void toDomainConNullDevuelveNullEnVezDeLanzar() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    void toEntityConNullDevuelveNullEnVezDeLanzar() {
        assertThat(mapper.toEntity(null)).isNull();
    }
}
