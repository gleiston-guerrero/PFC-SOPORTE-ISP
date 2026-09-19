package ec.edu.uteq.soporte.ticketservice.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Entregable 1 de la guia de cierre: "el dominio importa el marco". Antes de esta prueba,
 * domain/ tenia 12 lineas de import org.springframework.* en 9 clases (@Component/@Value/
 * @Order, para que Spring pudiera descubrirlas como beans) mientras el manuscrito publicaba
 * "cero anotaciones Spring/JPA" -- movidas a infrastructure/config/DomainBeansConfig.java.
 * Esta prueba falla la construccion si alguna clase de domain vuelve a depender de Spring o
 * de JPA, en vez de depender de que alguien lo note leyendo el codigo a mano.
 */
@AnalyzeClasses(packages = "ec.edu.uteq.soporte.ticketservice")
class DomainArchitectureTest {

    @ArchTest
    static final ArchRule domain_no_depende_de_spring_ni_de_jpa = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "javax.persistence..");

    /**
     * La regla anterior solo prohibia importar el marco; no verificaba la direccion de
     * dependencias entre capas en si (hallazgo de una revision externa: "la regla solo
     * prohibe org.springframework.. y jakarta/javax.persistence.., no la direccion entre
     * capas"). TicketWriter (application/) importaba directamente CrdbMetrics
     * (infrastructure/metrics/), violando la regla que el propio manuscrito declara
     * ("las capas externas dependen de las internas, nunca al reves") sin que ninguna
     * prueba lo detectara. Esta regla verifica la direccion real: domain no depende de
     * nada de este servicio, application solo puede depender de domain, e
     * infrastructure/presentation pueden depender hacia adentro (domain y application).
     *
     * Excepcion declarada: Infrastructure puede depender de Presentation porque
     * AuthGatewayFilter (infrastructure/security) reutiliza el sobre de respuesta
     * ApiResponse (presentation/dto) para las 401/403 que rechaza antes de que la
     * peticion llegue a un controlador -- una reutilizacion real y pre-existente, no
     * relacionada con la violacion que motiva esta prueba (application -> infrastructure).
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
