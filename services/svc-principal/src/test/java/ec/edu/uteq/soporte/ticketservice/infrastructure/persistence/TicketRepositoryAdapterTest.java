package ec.edu.uteq.soporte.ticketservice.infrastructure.persistence;

import ec.edu.uteq.soporte.ticketservice.domain.Ticket;
import ec.edu.uteq.soporte.ticketservice.domain.TicketStatus;
import ec.edu.uteq.soporte.ticketservice.domain.Zone;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TicketRepositoryAdapter no tenia ninguna prueba unitaria propia -- solo se ejercitaba de
 * forma indirecta a traves de TicketRepositoryIntegrationTest (Testcontainers, un CockroachDB
 * real), que no corre localmente en Windows por la limitacion de Docker-outside-of-Docker
 * documentada en la Seccion de amenazas. Mismo criterio que
 * IncidenciaRepositoryAdapterTest: delega en Spring Data JPA a traves del mapper real (no
 * mockeado), para que un cambio accidental en el mapeo tambien rompa esta prueba.
 */
@ExtendWith(MockitoExtension.class)
class TicketRepositoryAdapterTest {

    @Mock
    private SpringDataTicketRepository jpaRepository;

    private final TicketMapper mapper = new TicketMapper();

    private TicketRepositoryAdapter adapter() {
        return new TicketRepositoryAdapter(jpaRepository, mapper);
    }

    private Ticket ticket(UUID id, Zone zone, TicketStatus status) {
        return Ticket.builder()
                .id(id)
                .createdAt(OffsetDateTime.now())
                .zone(zone)
                .clientId(UUID.randomUUID())
                .status(status)
                .description("Sin internet")
                .slaDeadline(OffsetDateTime.now().plusHours(24))
                .slaBreached(false)
                .build();
    }

    @Test
    void findByTicketId_mapeaLaEntidadEncontradaAlDominio() {
        UUID id = UUID.randomUUID();
        TicketJpaEntity entity = mapper.toEntity(ticket(id, Zone.QUEVEDO_NORTE, TicketStatus.NUEVO));
        when(jpaRepository.findByTicketId(id)).thenReturn(Optional.of(entity));

        Optional<Ticket> resultado = adapter().findByTicketId(id);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(id);
    }

    @Test
    void findByTicketId_devuelveVacioSiNoExiste() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findByTicketId(id)).thenReturn(Optional.empty());

        assertThat(adapter().findByTicketId(id)).isEmpty();
    }

    @Test
    void findByZone_mapeaCadaResultadoAlDominio() {
        TicketJpaEntity entity = mapper.toEntity(ticket(UUID.randomUUID(), Zone.QUEVEDO_SUR, TicketStatus.ASIGNADO));
        when(jpaRepository.findByZone(Zone.QUEVEDO_SUR)).thenReturn(List.of(entity));

        List<Ticket> resultado = adapter().findByZone(Zone.QUEVEDO_SUR);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getZone()).isEqualTo(Zone.QUEVEDO_SUR);
    }

    @Test
    void findByZoneAndStatus_delegaConAmbosParametros() {
        TicketJpaEntity entity =
                mapper.toEntity(ticket(UUID.randomUUID(), Zone.QUEVEDO_CENTRO, TicketStatus.EN_PROGRESO));
        when(jpaRepository.findByZoneAndStatus(Zone.QUEVEDO_CENTRO, TicketStatus.EN_PROGRESO))
                .thenReturn(List.of(entity));

        List<Ticket> resultado = adapter().findByZoneAndStatus(Zone.QUEVEDO_CENTRO, TicketStatus.EN_PROGRESO);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getStatus()).isEqualTo(TicketStatus.EN_PROGRESO);
    }

    @Test
    void findByStatus_crucePorEstadoSinFiltrarPorZona() {
        TicketJpaEntity entity = mapper.toEntity(ticket(UUID.randomUUID(), Zone.QUEVEDO_SUR, TicketStatus.ESCALADO));
        when(jpaRepository.findByStatus(TicketStatus.ESCALADO)).thenReturn(List.of(entity));

        List<Ticket> resultado = adapter().findByStatus(TicketStatus.ESCALADO);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getStatus()).isEqualTo(TicketStatus.ESCALADO);
    }

    @Test
    void findByClientId_mapeaCadaResultadoAlDominio() {
        UUID clientId = UUID.randomUUID();
        TicketJpaEntity entity = mapper.toEntity(ticket(UUID.randomUUID(), Zone.QUEVEDO_NORTE, TicketStatus.NUEVO));
        when(jpaRepository.findByClientId(clientId)).thenReturn(List.of(entity));

        List<Ticket> resultado = adapter().findByClientId(clientId);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void findByClientIdAndStatus_delegaConAmbosParametros() {
        UUID clientId = UUID.randomUUID();
        TicketJpaEntity entity =
                mapper.toEntity(ticket(UUID.randomUUID(), Zone.QUEVEDO_NORTE, TicketStatus.RESUELTO));
        when(jpaRepository.findByClientIdAndStatus(clientId, TicketStatus.RESUELTO)).thenReturn(List.of(entity));

        List<Ticket> resultado = adapter().findByClientIdAndStatus(clientId, TicketStatus.RESUELTO);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getStatus()).isEqualTo(TicketStatus.RESUELTO);
    }

    @Test
    void findAll_delegaEnJpaRepositoryYMapeaLaListaCompleta() {
        when(jpaRepository.findAll()).thenReturn(List.of());

        assertThat(adapter().findAll()).isEmpty();
    }

    @Test
    void save_delegaEnJpaRepositoryConLaEntidadMapeada() {
        UUID id = UUID.randomUUID();
        Ticket original = ticket(id, Zone.QUEVEDO_NORTE, TicketStatus.NUEVO);
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Ticket guardado = adapter().save(original);

        ArgumentCaptor<TicketJpaEntity> captor = ArgumentCaptor.forClass(TicketJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id);
        assertThat(guardado.getId()).isEqualTo(id);
        assertThat(guardado.getZone()).isEqualTo(Zone.QUEVEDO_NORTE);
    }
}
