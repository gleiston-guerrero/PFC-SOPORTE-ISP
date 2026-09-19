package ec.edu.uteq.soporte.authservice.integration;

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
 * Entregable 5 de la guia de cierre: "las migraciones de auth-service ... no las aplica
 * ninguna prueba de su modulo, solo el arranque del stack en integration. Ninguna prueba
 * comprueba flyway_schema_history" -- hallazgo de una revision externa. Antes de esta prueba,
 * si V1__init_auth_schema.sql se rompia, nada en auth-service lo detectaba por su cuenta;
 * habria hecho falta levantar el stack completo (docker-compose) para notarlo.
 *
 * Mismo patron que services/svc-principal/.../integration/TicketRepositoryIntegrationTest.java:
 * CockroachDB real en un contenedor Docker via Testcontainers, no un cluster local ya
 * corriendo ni una base embebida (H2) que no reproduciria el comportamiento real de
 * CockroachDB. Con "spring.jpa.hibernate.ddl-auto: validate" (fijado para los tres servicios
 * con persistencia en este mismo entregable), levantar el ApplicationContext ya verifica que
 * las entidades JPA coincidan con lo que Flyway aplico -- si no coinciden, el contexto no
 * arranca y la prueba falla con la columna/tabla exacta que falta.
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
        return "jdbc:postgresql://%s:%d/auth_db?sslmode=disable"
                .formatted(cockroach.getHost(), cockroach.getMappedPort(26257));
    }

    // La base "auth_db" debe existir antes de que Spring/Flyway intenten conectarse -- en
    // produccion la crea "db-init" (docker-compose.yml) antes de que el servicio arranque;
    // aqui, como en svc-principal, se crea a mano una sola vez en @BeforeAll.
    @BeforeAll
    static void createDatabase() throws SQLException {
        String adminUrl = "jdbc:postgresql://%s:%d/defaultdb?sslmode=disable"
                .formatted(cockroach.getHost(), cockroach.getMappedPort(26257));
        try (Connection conn = DriverManager.getConnection(adminUrl, "root", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS auth_db");
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
    void laTablaUsersExisteConLasColumnasQueEsperaLaEntidadJpa() throws SQLException {
        // No repite la lista completa de columnas (eso ya lo cruzo entidad-por-entidad el
        // ArchUnit/Hibernate "validate" al arrancar el contexto); confirma especificamente
        // que la tabla es alcanzable con una consulta real, no solo que el mapeo JPA compile.
        try (Connection conn = dataSource.getConnection();
             PreparedStatement select = conn.prepareStatement("SELECT count(*) FROM users")) {
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isZero();
            }
        }
    }
}
