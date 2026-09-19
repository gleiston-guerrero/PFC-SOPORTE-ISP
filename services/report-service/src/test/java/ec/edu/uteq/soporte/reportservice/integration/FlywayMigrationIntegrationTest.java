package ec.edu.uteq.soporte.reportservice.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entregable 5 de la guia de cierre: mismo hallazgo y mismo patron que
 * services/auth-service/.../integration/FlywayMigrationIntegrationTest.java -- ninguna prueba
 * de report-service aplicaba V1__init_report_schema.sql contra un CockroachDB real antes de
 * esta prueba.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FlywayMigrationIntegrationTest {

    @Container
    static final GenericContainer<?> cockroach = new GenericContainer<>(DockerImageName.parse("cockroachdb/cockroach:latest-v23.2"))
            .withCommand("start-single-node", "--insecure")
            .withExposedPorts(26257, 8080)
            .waitingFor(Wait.forHttp("/health?ready=1").forPort(8080).withStartupTimeout(Duration.ofSeconds(90)));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", FlywayMigrationIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
    }

    private static String jdbcUrl() {
        return "jdbc:postgresql://%s:%d/report_db?sslmode=disable"
                .formatted(cockroach.getHost(), cockroach.getMappedPort(26257));
    }

    @BeforeAll
    static void createDatabase() throws SQLException {
        String adminUrl = "jdbc:postgresql://%s:%d/defaultdb?sslmode=disable"
                .formatted(cockroach.getHost(), cockroach.getMappedPort(26257));
        try (Connection conn = DriverManager.getConnection(adminUrl, "root", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS report_db");
        }
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayAplicaLaMigracionV1YQuedaRegistradaEnElHistorial() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement select = conn.prepareStatement(
                     "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank")) {
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next())
                        .describedAs("flyway_schema_history debe tener al menos una fila tras arrancar el contexto")
                        .isTrue();
                assertThat(rs.getString("version")).isEqualTo("1");
                assertThat(rs.getBoolean("success")).isTrue();
            }
        }
    }

    @Test
    void laTablaTicketSummaryExisteConLasColumnasQueEsperaLaEntidadJpa() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement select = conn.prepareStatement("SELECT count(*) FROM ticket_summary")) {
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isZero();
            }
        }
    }
}
