package ec.edu.uteq.soporte.authservice.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Extension a auth-service de la prueba de arquitectura de ticket-service (Entregable 1 de la
 * guia de cierre). Una revision externa aplico la misma regla `layeredArchitecture()` de
 * ticket-service/architecture/DomainArchitectureTest.java a este servicio y encontro 38
 * violaciones reales en AuthService.java: application importaba directamente
 * infrastructure.security.JwtService, infrastructure.messaging.TechnicianEventPublisher y cinco
 * DTO de presentation.dto, pese a que arquitectura_sistema_e4.tex afirmaba "el mismo patron" que
 * ticket-service sin que ninguna prueba lo verificara para este servicio. Se corrige moviendo
 * JwtService/TechnicianEventPublisher detras de los puertos application/TokenIssuer y
 * application/TechnicianCreatedNotifier, y reemplazando los DTO de presentation que
 * AuthService recibia/devolvia por tipos propios de application (RegisterCommand,
 * CreateUserCommand, TokenPair, TokenValidation, User) que el controlador mapea -- mismo patron
 * que CreateTicketCommand/Ticket en ticket-service.
 */
@AnalyzeClasses(packages = "ec.edu.uteq.soporte.authservice")
class DomainArchitectureTest {

    @ArchTest
    static final ArchRule domain_no_depende_de_spring_ni_de_jpa = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "javax.persistence..");

    /**
     * Direccion real de dependencias entre capas: domain no depende de nada de este servicio,
     * application solo puede depender de domain, e infrastructure/presentation pueden depender
     * hacia adentro (domain y application).
     *
     * Excepcion declarada: Infrastructure puede depender de Presentation porque SecurityConfig
     * (infrastructure/security) reutiliza el sobre de respuesta ApiResponse (presentation/dto)
     * para las respuestas 401/403 que construye antes de que la peticion llegue a un
     * controlador -- una reutilizacion real y pre-existente, mismo criterio ya aceptado en
     * ticket-service para AuthGatewayFilter, no relacionada con la violacion que motiva esta
     * prueba (application -> infrastructure/presentation).
     */
    @ArchTest
    static final ArchRule las_capas_dependen_solo_hacia_adentro = Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .layer("Presentation").definedBy("..presentation..")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Presentation")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure", "Presentation")
            .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Presentation")
            .whereLayer("Presentation").mayOnlyBeAccessedByLayers("Infrastructure");
}
