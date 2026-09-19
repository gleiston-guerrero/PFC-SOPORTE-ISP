package ec.edu.uteq.soporte.ticketservice.infrastructure.metrics;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import ec.edu.uteq.soporte.ticketservice.application.TransactionRetryMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * Los 3 instrumentos Prometheus incrementales exigidos por la Guia de Entrega 3
 * (D3.2, Modulo G): validables con `promtool check metrics` sobre la salida cruda
 * de /actuator/prometheus.
 *
 * <pre>
 * crdb_query_duration_seconds     histogram  duracion de cada consulta al puerto TicketRepository
 * crdb_transaction_retries_total  counter    reintentos por conflicto de escritura serializable
 * crdb_pool_active_connections    gauge      conexiones activas del pool HikariCP hacia CockroachDB
 * </pre>
 *
 * El histograma y el contador se usan desde otras clases (ver
 * RepositoryTimingAspect y application/command/CreateTicketHandler#saveWithRetry); el gauge se
 * auto-actualiza porque Micrometer lo re-consulta bajo demanda contra el HikariPoolMXBean real,
 * no contra un valor cacheado.
 */
@Component
public class CrdbMetrics implements TransactionRetryMetrics {

    private final Counter transactionRetries;
    private final Timer queryDuration;

    public CrdbMetrics(MeterRegistry registry, DataSource dataSource) {
        this.transactionRetries = Counter.builder("crdb_transaction_retries_total")
                .description("Reintentos de transaccion por conflicto de escritura serializable en CockroachDB")
                .register(registry);

        this.queryDuration = Timer.builder("crdb_query_duration_seconds")
                .description("Duracion de cada consulta ejecutada contra el cluster CockroachDB")
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(5))
                .maximumExpectedValue(Duration.ofSeconds(2))
                .register(registry);

        if (dataSource instanceof HikariDataSource hikari) {
            Gauge.builder("crdb_pool_active_connections", hikari, CrdbMetrics::activeConnections)
                    .description("Conexiones activas del pool HikariCP hacia CockroachDB")
                    .register(registry);
        }
    }

    private static double activeConnections(HikariDataSource hikari) {
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        return pool != null ? pool.getActiveConnections() : 0;
    }

    public Timer queryDurationTimer() {
        return queryDuration;
    }

    @Override
    public void incrementTransactionRetries() {
        transactionRetries.increment();
    }
}
