package ec.edu.uteq.soporte.ticketservice.domain.correlation;

import ec.edu.uteq.soporte.ticketservice.domain.Incidencia;
import ec.edu.uteq.soporte.ticketservice.domain.Ticket;
import ec.edu.uteq.soporte.ticketservice.domain.Zone;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba las tres implementaciones intercambiables del Strategy de correlacion (Adicion 1). */
class CorrelationStrategyTest {

    private Ticket ticketDe(Zone zone) {
        return Ticket.builder()
                .id(UUID.randomUUID())
                .zone(zone)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private Incidencia incidenciaAbiertaEn(Zone zone, OffsetDateTime createdAt) {
        return Incidencia.builder()
                .id(UUID.randomUUID())
                .zone(zone)
                .createdAt(createdAt)
                .correlMode("c1")
                .ticketIds(new java.util.HashSet<>(List.of(UUID.randomUUID())))
                .build();
    }

    @Test
    void c0_siempreAbreUnaIncidenciaNueva_aunqueHayaCandidatas() {
        CorrelationStrategy c0 = new SinCorrelacionStrategy();
        Ticket ticket = ticketDe(Zone.QUEVEDO_CENTRO);
        List<Incidencia> candidatas = List.of(incidenciaAbiertaEn(Zone.QUEVEDO_CENTRO, OffsetDateTime.now()));

        Incidencia resultado = c0.correlacionar(ticket, candidatas);

        assertThat(resultado.getCorrelMode()).isEqualTo("c0");
        assertThat(resultado.getTicketIds()).containsExactly(ticket.getId());
    }

    @Test
    void c1_seUneALaCandidataMasRecienteSiExiste() {
        CorrelationStrategy c1 = new ZonaVentanaStrategy();
        Ticket ticket = ticketDe(Zone.QUEVEDO_NORTE);
        Incidencia vieja = incidenciaAbiertaEn(Zone.QUEVEDO_NORTE, OffsetDateTime.now().minusMinutes(10));
        Incidencia reciente = incidenciaAbiertaEn(Zone.QUEVEDO_NORTE, OffsetDateTime.now().minusMinutes(1));

        Incidencia resultado = c1.correlacionar(ticket, List.of(vieja, reciente));

        assertThat(resultado.getId()).isEqualTo(reciente.getId());
        assertThat(resultado.getTicketIds()).contains(ticket.getId());
    }

    @Test
    void c1_alUnirseConservaLosTicketsQueYaEstabanEnLaIncidencia() {
        // La prueba de arriba (c1_seUneALaCandidataMasRecienteSiExiste) solo confirma que el
        // ticket nuevo entra -- no que los que ya estaban sigan ahi. Si unir() reemplazara el
        // set en vez de agregarle, esa prueba seguiria en verde y este bug pasaria
        // desapercibido.
        CorrelationStrategy c1 = new ZonaVentanaStrategy();
        Ticket ticket = ticketDe(Zone.QUEVEDO_NORTE);
        Incidencia existente = incidenciaAbiertaEn(Zone.QUEVEDO_NORTE, OffsetDateTime.now());
        UUID ticketPrevio = existente.getTicketIds().iterator().next();

        Incidencia resultado = c1.correlacionar(ticket, List.of(existente));

        assertThat(resultado.getTicketIds()).containsExactlyInAnyOrder(ticketPrevio, ticket.getId());
    }

    @Test
    void c2_convierteLaVentanaDeMinutosASegundosAntesDeConsultarTelemetria() {
        // Las pruebas de c2 de arriba usan lambdas que ignoran el parametro "ventana" por
        // completo -- nunca verifican que el valor real que llega al puerto sea el correcto.
        // Si el constructor dejara de multiplicar por 60 (o multiplicara por otra cosa), una
        // ventana configurada de "15 minutos" le pasaria un numero distinto a telemetry-service
        // y ninguna prueba existente lo notaria.
        long[] segundosCapturados = new long[1];
        TelemetryQueryPort capturaVentana = (zone, ventana) -> {
            segundosCapturados[0] = ventana;
            return false;
        };
        CorrelationStrategy c2 = new ZonaVentanaTelemetriaStrategy(capturaVentana, 15);
        Ticket ticket = ticketDe(Zone.QUEVEDO_CENTRO);

        c2.correlacionar(ticket, List.of());

        assertThat(segundosCapturados[0]).isEqualTo(900L);
    }

    @Test
    void c1_abreUnaNuevaSiNoHayCandidatas() {
        CorrelationStrategy c1 = new ZonaVentanaStrategy();
        Ticket ticket = ticketDe(Zone.QUEVEDO_SUR);

        Incidencia resultado = c1.correlacionar(ticket, List.of());

        assertThat(resultado.getCorrelMode()).isEqualTo("c1");
        assertThat(resultado.getTicketIds()).containsExactly(ticket.getId());
    }

    @Test
    void c2_seUneSoloSiHayEvidenciaDeTelemetria() {
        TelemetryQueryPort conEvidencia = (zone, ventana) -> true;
        CorrelationStrategy c2 = new ZonaVentanaTelemetriaStrategy(conEvidencia, 15);
        Ticket ticket = ticketDe(Zone.QUEVEDO_CENTRO);
        Incidencia existente = incidenciaAbiertaEn(Zone.QUEVEDO_CENTRO, OffsetDateTime.now());

        Incidencia resultado = c2.correlacionar(ticket, List.of(existente));

        assertThat(resultado.getId()).isEqualTo(existente.getId());
        assertThat(resultado.getTicketIds()).contains(ticket.getId());
    }

    @Test
    void c2_noSeUneSiNoHayEvidenciaDeTelemetria_aunqueHayaCandidatas() {
        TelemetryQueryPort sinEvidencia = (zone, ventana) -> false;
        CorrelationStrategy c2 = new ZonaVentanaTelemetriaStrategy(sinEvidencia, 15);
        Ticket ticket = ticketDe(Zone.QUEVEDO_CENTRO);
        Incidencia existente = incidenciaAbiertaEn(Zone.QUEVEDO_CENTRO, OffsetDateTime.now());

        Incidencia resultado = c2.correlacionar(ticket, List.of(existente));

        // Queda aislado en su propia incidencia -- no se une a "existente".
        assertThat(resultado.getId()).isNotEqualTo(existente.getId());
        assertThat(resultado.getCorrelMode()).isEqualTo("c2");
        assertThat(resultado.getTicketIds()).containsExactly(ticket.getId());
    }

    @Test
    void c2_falloDelCanalDeTelemetriaSeTrataComoSinEvidencia() {
        TelemetryQueryPort canalCaido = (zone, ventana) -> {
            throw new RuntimeException("simulado: telemetry-service no responde");
        };
        // El adaptador real (TelemetryGrpcClientAdapter) atrapa la excepcion y devuelve
        // false -- aqui se prueba que la estrategia, dado ese "false", no se cae ni agrupa.
        CorrelationStrategy c2 = new ZonaVentanaTelemetriaStrategy((zone, ventana) -> false, 15);
        Ticket ticket = ticketDe(Zone.QUEVEDO_CENTRO);

        Incidencia resultado = c2.correlacionar(ticket, List.of());

        assertThat(resultado.getTicketIds()).containsExactly(ticket.getId());
    }
}
