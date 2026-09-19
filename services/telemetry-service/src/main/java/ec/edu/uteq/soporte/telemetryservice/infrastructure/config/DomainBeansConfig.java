package ec.edu.uteq.soporte.telemetryservice.infrastructure.config;

import ec.edu.uteq.soporte.telemetryservice.domain.TelemetryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra como bean de Spring la unica clase de domain/ que antes se anotaba a si misma con
 * @Component (Entregable 1 de la guia de cierre: "el dominio importa el marco"). Mismo patron
 * que services/svc-principal/infrastructure/config/DomainBeansConfig.java.
 */
@Configuration
public class DomainBeansConfig {

    @Bean
    public TelemetryStore telemetryStore() {
        return new TelemetryStore();
    }
}
