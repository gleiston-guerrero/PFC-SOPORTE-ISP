package ec.edu.uteq.soporte.ticketservice.presentation.dto;

import ec.edu.uteq.soporte.ticketservice.domain.TicketStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TicketControllerTest ejercita UpdateStatusRequest llamando al controlador directamente (sin
 * MockMvc ni contexto de Spring), asi que nunca dispara el pipeline de @Valid -- una revision
 * externa confirmo que quitar las cinco anotaciones (@Size/@DecimalMin/@DecimalMax) del record
 * no rompia ninguna prueba (154 ejecutadas, 0 fallos). Esta clase valida el record directamente
 * con el mismo Validator que usa Spring Boot (hibernate-validator via
 * spring-boot-starter-validation), sin necesidad de contexto Spring, para que borrar cualquiera
 * de las cinco anotaciones si rompa una prueba.
 */
class UpdateStatusRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void solicitudValidaSinEvidenciaNoTieneViolaciones() {
        var request = new UpdateStatusRequest(TicketStatus.ASIGNADO, null, null, null);

        Set<ConstraintViolation<UpdateStatusRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void solicitudValidaConEvidenciaDentroDeRangoNoTieneViolaciones() {
        var request = new UpdateStatusRequest(TicketStatus.RESUELTO, "foto-base64", -1.02, -79.46);

        Set<ConstraintViolation<UpdateStatusRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void statusNuloVioleNotNull() {
        var request = new UpdateStatusRequest(null, null, null, null);

        Set<ConstraintViolation<UpdateStatusRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("status");
    }

    @Test
    void evidenciaSobreElTamanoMaximoVioleSize() {
        String evidenciaSobredimensionada = "a".repeat(8_000_001);
        var request = new UpdateStatusRequest(TicketStatus.RESUELTO, evidenciaSobredimensionada, -1.0, -79.0);

        Set<ConstraintViolation<UpdateStatusRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("evidencePhotoBase64");
    }

    @Test
    void latitudFueraDeRangoPositivoVioleDecimalMax() {
        var request = new UpdateStatusRequest(TicketStatus.RESUELTO, null, 90.0001, -79.0);

        Set<ConstraintViolation<UpdateStatusRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("latitude");
    }

    @Test
    void latitudFueraDeRangoNegativoVioleDecimalMin() {
        var request = new UpdateStatusRequest(TicketStatus.RESUELTO, null, -90.0001, -79.0);

        Set<ConstraintViolation<UpdateStatusRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("latitude");
    }

    @Test
    void longitudFueraDeRangoPositivoVioleDecimalMax() {
        var request = new UpdateStatusRequest(TicketStatus.RESUELTO, null, -1.0, 180.0001);

        Set<ConstraintViolation<UpdateStatusRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("longitude");
    }

    @Test
    void longitudFueraDeRangoNegativoVioleDecimalMin() {
        var request = new UpdateStatusRequest(TicketStatus.RESUELTO, null, -1.0, -180.0001);

        Set<ConstraintViolation<UpdateStatusRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("longitude");
    }
}
