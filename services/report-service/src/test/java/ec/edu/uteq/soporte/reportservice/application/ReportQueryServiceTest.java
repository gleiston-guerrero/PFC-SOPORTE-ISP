package ec.edu.uteq.soporte.reportservice.application;

import ec.edu.uteq.soporte.reportservice.domain.TicketSummary;
import ec.edu.uteq.soporte.reportservice.domain.TicketSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ReportQueryService.filtered() no tenia ninguna prueba: es la unica logica real del
 * servicio (el resto son delegaciones directas al puerto), filtrando en memoria por
 * hasta tres criterios independientes y opcionales, cada uno insensible a mayusculas
 * -- exactamente el tipo de combinatoria (3 filtros, cada uno presente o ausente) que
 * es facil de romper con un caso sin probar.
 */
@ExtendWith(MockitoExtension.class)
class ReportQueryServiceTest {

    @Mock
    private TicketSummaryRepository repository;

    private ReportQueryService service;

    @BeforeEach
    void setUp() {
        service = new ReportQueryService(repository);
    }

    @Test
    void sinNingunFiltroDevuelveTodosLosResumenes() {
        stubDatosDePrueba();

        assertThat(service.filtered(null, null, null)).hasSize(4);
    }

    @Test
    void filtraSoloPorZona() {
        stubDatosDePrueba();

        assertThat(service.filtered("QUEVEDO_NORTE", null, null)).hasSize(2)
                .allMatch(t -> t.getZone().equals("QUEVEDO_NORTE"));
    }

    @Test
    void filtraSoloPorEstado() {
        stubDatosDePrueba();

        assertThat(service.filtered(null, "RESUELTO", null)).hasSize(2)
                .allMatch(t -> t.getStatus().equals("RESUELTO"));
    }

    @Test
    void filtraSoloPorCategoria() {
        stubDatosDePrueba();

        assertThat(service.filtered(null, null, "HARDWARE")).hasSize(3)
                .allMatch(t -> t.getCategory().equals("HARDWARE"));
    }

    @Test
    void combinaLosTresFiltrosALaVez() {
        stubDatosDePrueba();

        List<TicketSummary> resultado = service.filtered("QUEVEDO_SUR", "RESUELTO", "HARDWARE");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getZone()).isEqualTo("QUEVEDO_SUR");
        assertThat(resultado.get(0).getStatus()).isEqualTo("RESUELTO");
        assertThat(resultado.get(0).getCategory()).isEqualTo("HARDWARE");
    }

    @Test
    void unaCombinacionQueNoExisteDevuelveVacioNoTodoElConjunto() {
        // Guarda contra un filtro que se aplique con OR en vez de AND -- ese bug
        // devolveria resultados igual, solo que de mas, y pasaria desapercibido.
        stubDatosDePrueba();

        assertThat(service.filtered("QUEVEDO_NORTE", "RESUELTO", "SOFTWARE")).isEmpty();
    }

    @Test
    void elFiltroDeZonaNoDistingueMayusculasDeMinusculas() {
        stubDatosDePrueba();

        assertThat(service.filtered("quevedo_norte", null, null)).hasSize(2);
    }

    private void stubDatosDePrueba() {
        when(repository.findAll()).thenReturn(List.of(
                resumenEn("QUEVEDO_NORTE", "RESUELTO", "HARDWARE"),
                resumenEn("QUEVEDO_NORTE", "NUEVO", "SOFTWARE"),
                resumenEn("QUEVEDO_SUR", "RESUELTO", "HARDWARE"),
                resumenEn("QUEVEDO_SUR", "NUEVO", "HARDWARE")
        ));
    }

    @Test
    void totalCountDelegaDirectamenteAlRepositorio() {
        when(repository.count()).thenReturn(42L);

        assertThat(service.totalCount()).isEqualTo(42L);
    }

    private TicketSummary resumenEn(String zone, String status, String category) {
        return TicketSummary.builder()
                .ticketId(UUID.randomUUID())
                .clientId(UUID.randomUUID())
                .zone(zone)
                .status(status)
                .category(category)
                .build();
    }
}
