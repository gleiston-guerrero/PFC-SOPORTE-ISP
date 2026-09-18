package ec.edu.uteq.soporte.authservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PermissionCatalog no tenia ninguna prueba: es el mapa rol-a-permisos que se embebe como claim
 * en el access token para que ticket-service/api-gateway autoricen sin volver a consultar
 * auth-service en cada request. Un permiso mal listado aqui -- o un rol que caiga al valor por
 * defecto en vez del suyo -- se propagaria silenciosamente a cada token emitido.
 */
class PermissionCatalogTest {

    @Test
    void clienteSoloPuedeCrearYLeerSusPropiosTickets() {
        assertThat(PermissionCatalog.permissionsFor(Role.CLIENTE))
                .containsExactlyInAnyOrder("ticket:create", "ticket:read:own");
    }

    @Test
    void tecnicoPuedeLeerAsignarYActualizarTicketsDeSuZonaPeroNoCrearlos() {
        assertThat(PermissionCatalog.permissionsFor(Role.TECNICO))
                .containsExactlyInAnyOrder("ticket:read:zone", "ticket:update:status", "ticket:assign")
                .doesNotContain("ticket:create");
    }

    @Test
    void adminTieneElComodinQueRepresentaAccesoTotal() {
        assertThat(PermissionCatalog.permissionsFor(Role.ADMIN)).containsExactly("*");
    }

    @Test
    void cadaRolTieneUnaListaDePermisosDistintaDeLosDemas() {
        // Si dos roles compartieran por error la misma entrada del mapa (copy-paste), esta
        // prueba lo detecta aunque las pruebas individuales de arriba sigan en verde.
        assertThat(PermissionCatalog.permissionsFor(Role.CLIENTE))
                .isNotEqualTo(PermissionCatalog.permissionsFor(Role.TECNICO));
        assertThat(PermissionCatalog.permissionsFor(Role.TECNICO))
                .isNotEqualTo(PermissionCatalog.permissionsFor(Role.ADMIN));
    }
}
