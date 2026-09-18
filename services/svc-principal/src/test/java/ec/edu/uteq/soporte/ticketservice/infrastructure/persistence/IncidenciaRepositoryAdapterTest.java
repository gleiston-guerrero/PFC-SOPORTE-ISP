package ec.edu.uteq.soporte.ticketservice.infrastructure.persistence;

import ec.edu.uteq.soporte.ticketservice.domain.Incidencia;
import ec.edu.uteq.soporte.ticketservice.domain.Zone;
import java.time.OffsetDateTime;
import java.util.List;
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
 * Prueba el adaptador de persistencia de Incidencia (mismo criterio que
 * TicketRepositoryAdapterTest): delega en Spring Data JPA a traves del mapper real (no un
 * mapper mockeado, para que un cambio accidental en el mapeo tambien rompa esta prueba), y
 * genera un id nuevo cuando la incidencia llega sin uno -- el unico comportamiento propio
 * (no delegado) de esta clase.
 */
@ExtendWith(MockitoExtension.class)
class IncidenciaRepositoryAdapterTest {

    @Mock
    private SpringDataIncidenciaRepository jpaRepository;

    private final IncidenciaMapper mapper = new IncidenciaMapper();

    private IncidenciaRepositoryAdapter adapter() {
        return new IncidenciaRepositoryAdapter(jpaRepository, mapper);
    }

    private Incidencia incidenciaSinId() {
        return Incidencia.builder()
                .zone(Zone.QUEVEDO_NORTE)
                .createdAt(OffsetDateTime.now())
                .correlMode("c1")
                .build();
    }

    @Test
    void save_generaUnIdNuevoCuandoLaIncidenciaNoTieneUno() {
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Incidencia guardada = adapter().save(incidenciaSinId());

        assertThat(guardada.getId()).isNotNull();
    }

    @Test
    void save_conservaElIdExistenteSinGenerarUnoNuevo() {
        UUID idExistente = UUID.randomUUID();
        Incidencia conId = incidenciaSinId();
        conId.setId(idExistente);
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Incidencia guardada = adapter().save(conId);

        assertThat(guardada.getId()).isEqualTo(idExistente);
    }

    @Test
    void save_delegaEnJpaRepositoryConLaEntidadMapeada() {
        UUID id = UUID.randomUUID();
        Incidencia incidencia = incidenciaSinId();
        incidencia.setId(id);
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter().save(incidencia);

        ArgumentCaptor<IncidenciaJpaEntity> captor = ArgumentCaptor.forClass(IncidenciaJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id);
        assertThat(captor.getValue().getZone()).isEqualTo(Zone.QUEVEDO_NORTE);
        assertThat(captor.getValue().getCorrelMode()).isEqualTo("c1");
    }

    @Test
    void findByZoneAndCreatedAtAfter_mapeaCadaResultadoAlDominio() {
        OffsetDateTime desde = OffsetDateTime.now().minusMinutes(15);
        IncidenciaJpaEntity entity = IncidenciaJpaEntity.builder()
                .id(UUID.randomUUID())
                .zone(Zone.QUEVEDO_SUR)
                .createdAt(OffsetDateTime.now())
                .correlMode("c2")
                .build();
        when(jpaRepository.findByZoneAndCreatedAtAfter(Zone.QUEVEDO_SUR, desde)).thenReturn(List.of(entity));

        List<Incidencia> resultado = adapter().findByZoneAndCreatedAtAfter(Zone.QUEVEDO_SUR, desde);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getId()).isEqualTo(entity.getId());
        assertThat(resultado.get(0).getCorrelMode()).isEqualTo("c2");
    }

    @Test
    void findByZone_mapeaCadaResultadoAlDominio() {
        IncidenciaJpaEntity entity = IncidenciaJpaEntity.builder()
                .id(UUID.randomUUID())
                .zone(Zone.QUEVEDO_NORTE)
                .createdAt(OffsetDateTime.now())
                .correlMode("c1")
                .build();
        when(jpaRepository.findByZone(Zone.QUEVEDO_NORTE)).thenReturn(List.of(entity));

        List<Incidencia> resultado = adapter().findByZone(Zone.QUEVEDO_NORTE);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getZone()).isEqualTo(Zone.QUEVEDO_NORTE);
    }

    @Test
    void findAll_delegaEnJpaRepositoryYMapeaLaListaCompleta() {
        when(jpaRepository.findAll()).thenReturn(List.of());

        List<Incidencia> resultado = adapter().findAll();

        assertThat(resultado).isEmpty();
    }
}
