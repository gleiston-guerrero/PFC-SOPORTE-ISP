package ec.edu.uteq.soporte.reportservice.infrastructure.persistence;

import ec.edu.uteq.soporte.reportservice.domain.TicketSummary;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TicketSummaryMapper no tenia ninguna prueba: el mismo tipo de mapeo campo a campo entre el
 * dominio puro y su entidad JPA que ya causo un bug real en ticket-service (ver
 * TicketMapperTest, Entregable 10 -- campos de evidencia agregados a un lado y olvidados en el
 * otro). Aqui el riesgo es mayor: TicketSummary solo se reconstruye a partir de eventos de
 * Kafka (ver ReportEventListener), asi que un campo perdido en el mapeo no se nota en una
 * escritura directa a la base -- se nota semanas despues, como un reporte con un dato en blanco
 * que nadie relaciona con el mapeo.
 */
class TicketSummaryMapperTest {

    private final TicketSummaryMapper mapper = new TicketSummaryMapper();

    @Test
    void toEntityLuegoToDomainPreservaTodosLosCamposSinPerdida() {
        TicketSummary original = TicketSummary.builder()
                .zone("QUEVEDO_NORTE")
                .ticketId(UUID.randomUUID())
                .clientId(UUID.randomUUID())
                .technicianId(UUID.randomUUID())
                .category("HARDWARE")
                .priority("ALTO")
                .status("EN_PROGRESO")
                .description("Router sin luz de enlace")
                .createdAt(OffsetDateTime.parse("2026-09-17T05:18:52.933Z"))
                .updatedAt(OffsetDateTime.parse("2026-09-17T06:00:00.000Z"))
                .build();

        TicketSummary recuperado = mapper.toDomain(mapper.toEntity(original));

        assertThat(recuperado).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void unResumenSinTecnicoAsignadoMantieneEseCampoNulo() {
        // El caso normal antes de la asignacion: technicianId debe seguir siendo null a
        // traves del mapeo, no un UUID vacio que parezca un dato real.
        TicketSummary sinTecnico = TicketSummary.builder()
                .zone("QUEVEDO_SUR")
                .ticketId(UUID.randomUUID())
                .clientId(UUID.randomUUID())
                .category("CONECTIVIDAD")
                .priority("MEDIO")
                .status("NUEVO")
                .description("Sin evidencia todavia")
                .createdAt(OffsetDateTime.now())
                .build();

        TicketSummary recuperado = mapper.toDomain(mapper.toEntity(sinTecnico));

        assertThat(recuperado.getTechnicianId()).isNull();
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
