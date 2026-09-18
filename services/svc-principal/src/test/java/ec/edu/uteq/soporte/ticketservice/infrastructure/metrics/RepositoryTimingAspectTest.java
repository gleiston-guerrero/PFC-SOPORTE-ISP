package ec.edu.uteq.soporte.ticketservice.infrastructure.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RepositoryTimingAspect no tenia ninguna prueba: el unico punto de instrumentacion del
 * histograma crdb_query_duration_seconds, con una garantia en el propio codigo (el
 * cronometro se detiene en un bloque finally) que ninguna prueba habia verificado -- ni que
 * el resultado real del metodo envuelto pase intacto, ni que una excepcion siga
 * propagandose sin que el aspecto la trague.
 */
class RepositoryTimingAspectTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CrdbMetrics crdbMetrics = new CrdbMetrics(registry, mock(DataSource.class));
    private final RepositoryTimingAspect aspect = new RepositoryTimingAspect(crdbMetrics);

    @Test
    void dejaPasarElResultadoRealDelMetodoEnvuelto() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("valor-real-del-repositorio");

        Object resultado = aspect.timeQuery(joinPoint);

        assertThat(resultado).isEqualTo("valor-real-del-repositorio");
    }

    @Test
    void registraLaDuracionEnElHistogramaTrasUnaLlamadaExitosa() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.timeQuery(joinPoint);

        assertThat(registry.get("crdb_query_duration_seconds").timer().count()).isEqualTo(1);
    }

    @Test
    void siElMetodoEnvueltoLanzaUnaExcepcionSiguePropagandoseSinTragarla() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("fallo real del repositorio"));

        assertThatThrownBy(() -> aspect.timeQuery(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fallo real del repositorio");
    }

    @Test
    void registraLaDuracionAunSiElMetodoEnvueltoLanzaUnaExcepcion() throws Throwable {
        // La garantia central del aspecto: el cronometro vive en un bloque finally, asi que
        // una consulta que falla igual debe contar para el histograma -- si no, las
        // consultas lentas-que-terminan-fallando serian invisibles en las metricas justo
        // cuando mas importa verlas.
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("fallo real del repositorio"));

        try {
            aspect.timeQuery(joinPoint);
        } catch (IllegalStateException esperada) {
            // se espera, ver la prueba anterior
        }

        assertThat(registry.get("crdb_query_duration_seconds").timer().count()).isEqualTo(1);
    }
}
