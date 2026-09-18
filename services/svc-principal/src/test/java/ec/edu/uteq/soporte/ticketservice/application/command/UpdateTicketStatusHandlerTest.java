package ec.edu.uteq.soporte.ticketservice.application.command;

import ec.edu.uteq.soporte.ticketservice.application.ForbiddenException;
import ec.edu.uteq.soporte.ticketservice.application.TicketAuthorization;
import ec.edu.uteq.soporte.ticketservice.application.TicketNotFoundException;
import ec.edu.uteq.soporte.ticketservice.application.TicketWriter;
import ec.edu.uteq.soporte.ticketservice.domain.EventPublisher;
import ec.edu.uteq.soporte.ticketservice.domain.Ticket;
import ec.edu.uteq.soporte.ticketservice.domain.TicketRepository;
import ec.edu.uteq.soporte.ticketservice.domain.TicketStatus;
import ec.edu.uteq.soporte.ticketservice.domain.Zone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateTicketStatusHandlerTest {

    private final TicketAuthorization authorization = new TicketAuthorization();

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketWriter ticketWriter;

    @Mock
    private EventPublisher eventPublisher;

    private UpdateTicketStatusHandler handler() {
        return new UpdateTicketStatusHandler(ticketRepository, authorization, ticketWriter, eventPublisher);
    }

    @Test
    void updateStatus_toResuelto_marksResolvedAtAndEvaluatesSlaBreach() {
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_SUR, id);
        existing.setStatus(TicketStatus.EN_PROGRESO);
        existing.setCreatedAt(OffsetDateTime.now().minusHours(30));
        existing.setSlaDeadline(OffsetDateTime.now().minusHours(6)); // ya vencido

        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));
        when(ticketWriter.saveWithRetry(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = handler().handle(new UpdateTicketStatusCommand(id, TicketStatus.RESUELTO, "ADMIN", null, null, null, null));

        assertThat(result.getStatus()).isEqualTo(TicketStatus.RESUELTO);
        assertThat(result.getResolvedAt()).isNotNull();
        assertThat(result.isSlaBreached()).isTrue();
    }

    @Test
    void updateStatus_toResuelto_withEvidence_persistsPhotoAndCoordinates() {
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_SUR, id);
        existing.setStatus(TicketStatus.EN_PROGRESO);

        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));
        when(ticketWriter.saveWithRetry(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] photo = {1, 2, 3, 4};
        Ticket result = handler().handle(
                new UpdateTicketStatusCommand(id, TicketStatus.RESUELTO, "TECNICO", Zone.QUEVEDO_SUR, photo, -1.02, -79.46));

        assertThat(result.getEvidencePhoto()).isEqualTo(photo);
        assertThat(result.getEvidenceLatitude()).isEqualTo(-1.02);
        assertThat(result.getEvidenceLongitude()).isEqualTo(-79.46);
    }

    @Test
    void updateStatus_toAsignado_ignoresEvidenceEvenIfSent() {
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_SUR, id);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));
        when(ticketWriter.saveWithRetry(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        // La evidencia solo tiene sentido en el cierre en sitio (RESUELTO); si llegara en
        // cualquier otra transicion, el manejador no debe guardarla.
        Ticket result = handler().handle(
                new UpdateTicketStatusCommand(id, TicketStatus.ASIGNADO, "TECNICO", Zone.QUEVEDO_SUR, new byte[]{9}, 1.0, 2.0));

        assertThat(result.getEvidencePhoto()).isNull();
        assertThat(result.getEvidenceLatitude()).isNull();
        assertThat(result.getEvidenceLongitude()).isNull();
    }

    @Test
    void updateStatus_byTecnicoInOwnZone_isAllowed() {
        UUID id = UUID.randomUUID();
        Zone zone = Zone.QUEVEDO_NORTE;
        Ticket existing = ticketIn(zone, id);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));
        when(ticketWriter.saveWithRetry(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = handler().handle(new UpdateTicketStatusCommand(id, TicketStatus.ASIGNADO, "TECNICO", zone, null, null, null));

        assertThat(result.getStatus()).isEqualTo(TicketStatus.ASIGNADO);
    }

    @Test
    void updateStatus_byTecnicoInAnotherZone_isForbidden() {
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_NORTE, id);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> handler().handle(
                new UpdateTicketStatusCommand(id, TicketStatus.ASIGNADO, "TECNICO", Zone.QUEVEDO_SUR, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateStatus_byTecnicoWithNoZone_isForbidden() {
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_NORTE, id);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> handler().handle(
                new UpdateTicketStatusCommand(id, TicketStatus.ASIGNADO, "TECNICO", null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateStatus_byCliente_isForbidden() {
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_NORTE, id);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> handler().handle(
                new UpdateTicketStatusCommand(id, TicketStatus.ASIGNADO, "CLIENTE", null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateStatus_publishesTicketStatusChangedEvent() {
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_NORTE, id);
        existing.setStatus(TicketStatus.NUEVO);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));
        when(ticketWriter.saveWithRetry(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = handler().handle(new UpdateTicketStatusCommand(id, TicketStatus.ASIGNADO, "ADMIN", null, null, null, null));

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(eventPublisher).publish(topicCaptor.capture(), any(), any());
        assertThat(topicCaptor.getValue()).isEqualTo("ticket.status-changed");
        assertThat(result.getStatus()).isEqualTo(TicketStatus.ASIGNADO);
    }

    @Test
    void updateStatus_toResuelto_beforeDeadline_isNotSlaBreached() {
        // La unica prueba existente de resolucion dejaba el SLA ya vencido; la rama negativa
        // de isAfter() (resuelto A TIEMPO) nunca se ejercitaba.
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_SUR, id);
        existing.setStatus(TicketStatus.EN_PROGRESO);
        existing.setSlaDeadline(OffsetDateTime.now().plusHours(6)); // todavia no vence

        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));
        when(ticketWriter.saveWithRetry(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = handler().handle(
                new UpdateTicketStatusCommand(id, TicketStatus.RESUELTO, "ADMIN", null, null, null, null));

        assertThat(result.isSlaBreached()).isFalse();
    }

    @Test
    void updateStatus_toResuelto_withNoSlaDeadline_isNeverBreached() {
        // El "&&" de la condicion tiene un cortocircuito por slaDeadline == null (un ticket
        // sin plazo formal, p.ej. antes de que ai-service lo clasifique) que ninguna prueba
        // ejercitaba -- sin el, un NullPointerException tumbaria el cierre del ticket.
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_SUR, id);
        existing.setStatus(TicketStatus.EN_PROGRESO);
        existing.setSlaDeadline(null);

        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));
        when(ticketWriter.saveWithRetry(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = handler().handle(
                new UpdateTicketStatusCommand(id, TicketStatus.RESUELTO, "ADMIN", null, null, null, null));

        assertThat(result.isSlaBreached()).isFalse();
    }

    @Test
    void updateStatus_reintentoIdempotente_noRecalculaNiRepublica() {
        // Entregable 10: el movil reintenta el mismo PATCH RESUELTO hasta 3 veces ante
        // IOException, incluso si la primera escritura si llego al servidor y solo se perdio
        // la respuesta. Antes de este arreglo, el segundo PATCH volvia a poner resolvedAt=now(),
        // podia voltear slaBreached de falso a verdadero solo por el tiempo transcurrido, y
        // volvia a publicar ticket.status-changed.
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_SUR, id);
        existing.setStatus(TicketStatus.RESUELTO);
        OffsetDateTime resolvedAt = OffsetDateTime.now().minusMinutes(5);
        existing.setResolvedAt(resolvedAt);
        existing.setSlaBreached(false);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        Ticket result = handler().handle(
                new UpdateTicketStatusCommand(id, TicketStatus.RESUELTO, "TECNICO", Zone.QUEVEDO_SUR,
                        new byte[]{1}, -1.0, -79.0));

        assertThat(result.getResolvedAt()).isEqualTo(resolvedAt);
        assertThat(result.isSlaBreached()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(ticketWriter, eventPublisher);
    }

    @Test
    void updateStatus_tecnicoSinEvidencia_lanzaIllegalArgument() {
        // El servidor trataba la evidencia como opcional para cualquier rol; un TECNICO podia
        // cerrar un ticket sin foto ni GPS llamando la API directo, sin pasar por el movil, que
        // es donde vivia la unica regla que lo exigia (Entregable 10).
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_SUR, id);
        existing.setStatus(TicketStatus.EN_PROGRESO);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> handler().handle(
                new UpdateTicketStatusCommand(id, TicketStatus.RESUELTO, "TECNICO", Zone.QUEVEDO_SUR, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStatus_adminSinEvidencia_siguePermitido() {
        // Un ADMIN puede resolver un ticket sin pasar por el movil (p.ej. corrigiendolo a
        // mano); esa excepcion es intencional y no debe romperse con la exigencia nueva.
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_SUR, id);
        existing.setStatus(TicketStatus.EN_PROGRESO);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));
        when(ticketWriter.saveWithRetry(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = handler().handle(
                new UpdateTicketStatusCommand(id, TicketStatus.RESUELTO, "ADMIN", null, null, null, null));

        assertThat(result.getStatus()).isEqualTo(TicketStatus.RESUELTO);
    }

    @Test
    void updateStatus_ticketNotFound_throws() {
        // Unica linea que el reporte JaCoCo del modulo seguia marcando parcial
        // (orElseThrow del find) despues de la Ronda 18 -- el resto del manejador ya
        // quedo en 100%.
        UUID id = UUID.randomUUID();
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(
                new UpdateTicketStatusCommand(id, TicketStatus.RESUELTO, "ADMIN", null, null, null, null)))
                .isInstanceOf(TicketNotFoundException.class);
    }

    private Ticket ticketIn(Zone zone, UUID id) {
        return Ticket.builder()
                .zone(zone)
                .id(id)
                .clientId(UUID.randomUUID())
                .status(TicketStatus.NUEVO)
                .createdAt(OffsetDateTime.now())
                .slaDeadline(OffsetDateTime.now().plusHours(24))
                .build();
    }
}
