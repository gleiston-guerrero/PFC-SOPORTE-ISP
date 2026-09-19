package ec.edu.uteq.soporte.telemetryservice.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Entregable 1 de la guia de cierre: telemetry-service era, junto con svc-principal, el unico
 * otro microservicio con paquete domain/ (TelemetryStore), y tambien violaba la regla ("el
 * dominio importa el marco") con @Component -- pero era el unico de los dos sin una prueba de
 * arquitectura que lo hiciera fallar en CI en vez de depender de que alguien lo notara leyendo
 * el codigo a mano. Mismo patron que svc-principal/architecture/DomainArchitectureTest.java.
 */
@AnalyzeClasses(packages = "ec.edu.uteq.soporte.telemetryservice")
class DomainArchitectureTest {

    @ArchTest
    static final ArchRule domain_no_depende_de_spring_ni_de_jpa = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "javax.persistence..");
}
