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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignTechnicianHandlerTest {

    private final TicketAuthorization authorization = new TicketAuthorization();

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketWriter ticketWriter;

    @Mock
    private EventPublisher eventPublisher;

    private AssignTechnicianHandler handler() {
        return new AssignTechnicianHandler(ticketRepository, authorization, ticketWriter, eventPublisher);
    }

    @Test
    void assignTechnician_byTecnicoInAnotherZone_isForbidden() {
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_CENTRO, id);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> handler().handle(
                new AssignTechnicianCommand(id, UUID.randomUUID(), "TECNICO", Zone.QUEVEDO_NORTE)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assignTechnician_publishesTicketAssignedEvent() {
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_NORTE, id);
        UUID technicianId = UUID.randomUUID();
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));
        when(ticketWriter.saveWithRetry(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = handler().handle(new AssignTechnicianCommand(id, technicianId, "ADMIN", null));

        assertThat(result.getStatus()).isEqualTo(TicketStatus.ASIGNADO);
        assertThat(result.getTechnicianId()).isEqualTo(technicianId);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher).publish(topicCaptor.capture(), any(), any());
        assertThat(topicCaptor.getValue()).isEqualTo("ticket.assigned");
    }

    @Test
    void assignTechnician_ticketNotFound_throws() {
        UUID id = UUID.randomUUID();
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(
                new AssignTechnicianCommand(id, UUID.randomUUID(), "ADMIN", null)))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void assignTechnician_byCliente_isForbidden() {
        // assertCanManage: CLIENTE nunca puede asignar tecnico, sin importar el ticket.
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_NORTE, id);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> handler().handle(
                new AssignTechnicianCommand(id, UUID.randomUUID(), "CLIENTE", null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assignTechnician_byTecnicoInOwnZone_isAllowed() {
        // La unica prueba positiva existente era con ADMIN; el camino permitido de TECNICO
        // nunca se ejercitaba, solo el prohibido (otra zona).
        UUID id = UUID.randomUUID();
        Zone zone = Zone.QUEVEDO_SUR;
        Ticket existing = ticketIn(zone, id);
        UUID technicianId = UUID.randomUUID();
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));
        when(ticketWriter.saveWithRetry(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = handler().handle(new AssignTechnicianCommand(id, technicianId, "TECNICO", zone));

        assertThat(result.getTechnicianId()).isEqualTo(technicianId);
    }

    @Test
    void assignTechnician_byTecnicoWithNoZone_isForbidden() {
        // authZone == null se trata fail-closed, no como "todas las zonas".
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_NORTE, id);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> handler().handle(
                new AssignTechnicianCommand(id, UUID.randomUUID(), "TECNICO", null)))
                .isInstanceOf(ForbiddenException.class);
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
