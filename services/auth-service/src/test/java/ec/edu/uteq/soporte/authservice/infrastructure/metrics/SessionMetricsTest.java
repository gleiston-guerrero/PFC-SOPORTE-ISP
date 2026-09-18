package ec.edu.uteq.soporte.authservice.infrastructure.metrics;

import ec.edu.uteq.soporte.authservice.domain.RefreshTokenRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SessionMetrics no tenia ninguna prueba: nada confirmaba que el Gauge "app_active_sessions"
 * quedara realmente registrado con ese nombre exacto, ni que su valor reflejara el conteo
 * real del repositorio -- exigido por el Modulo F item 3 de la guia de Entrega 4, nunca
 * verificado hasta ahora. Se usa un SimpleMeterRegistry real, no un mock del registro, para
 * comprobar el registro de verdad (mismo criterio que HttpMetricsFilterTest).
 */
class SessionMetricsTest {

    @Test
    void registraElGaugeAppActiveSessionsConElValorDelRepositorio() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        when(repository.countByRevokedFalseAndExpiresAtAfter(any(OffsetDateTime.class))).thenReturn(7L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new SessionMetrics(repository, registry);

        assertThat(registry.get("app_active_sessions").gauge().value()).isEqualTo(7.0);
    }

    @Test
    void elGaugeSeRecalculaEnCadaLecturaNoQuedaCacheado() {
        // Un Gauge de Micrometer no cachea: si el valor cambia en el repositorio entre dos
        // scrapes de Prometheus, el segundo debe reflejar el numero nuevo sin reconstruir nada.
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        when(repository.countByRevokedFalseAndExpiresAtAfter(any(OffsetDateTime.class)))
                .thenReturn(3L)
                .thenReturn(9L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new SessionMetrics(repository, registry);

        double primeraLectura = registry.get("app_active_sessions").gauge().value();
        double segundaLectura = registry.get("app_active_sessions").gauge().value();

        assertThat(primeraLectura).isEqualTo(3.0);
        assertThat(segundaLectura).isEqualTo(9.0);
    }

    @Test
    void construirSessionMetricsYaConsultaElRepositorioUnaVez() {
        // @PostConstruct logStartup() fuerza una lectura al arrancar (ver el comentario de
        // produccion: detectar un problema de conexion a la base de datos temprano). En una
        // prueba unitaria el @PostConstruct de Spring no corre solo -- se invoca explicitamente
        // para confirmar que existe y que efectivamente consulta el repositorio.
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        when(repository.countByRevokedFalseAndExpiresAtAfter(any(OffsetDateTime.class))).thenReturn(0L);
        SessionMetrics metrics = new SessionMetrics(repository, new SimpleMeterRegistry());

        metrics.logStartup();

        org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeastOnce())
                .countByRevokedFalseAndExpiresAtAfter(any(OffsetDateTime.class));
    }
}
