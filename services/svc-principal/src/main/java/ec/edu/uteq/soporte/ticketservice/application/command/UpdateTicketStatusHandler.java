package ec.edu.uteq.soporte.ticketservice.application.command;

import ec.edu.uteq.soporte.ticketservice.application.TicketAuthorization;
import ec.edu.uteq.soporte.ticketservice.application.TicketNotFoundException;
import ec.edu.uteq.soporte.ticketservice.application.TicketWriter;
import ec.edu.uteq.soporte.ticketservice.domain.EventPublisher;
import ec.edu.uteq.soporte.ticketservice.domain.Ticket;
import ec.edu.uteq.soporte.ticketservice.domain.TicketRepository;
import ec.edu.uteq.soporte.ticketservice.domain.TicketStatus;
import ec.edu.uteq.soporte.ticketservice.domain.event.TicketStatusChangedEvent;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class UpdateTicketStatusHandler implements TicketCommandHandler<UpdateTicketStatusCommand, Ticket> {

    private static final String TOPIC_TICKET_STATUS_CHANGED = "ticket.status-changed";

    private final TicketRepository ticketRepository;
    private final TicketAuthorization authorization;
    private final TicketWriter ticketWriter;
    private final EventPublisher eventPublisher;

    public UpdateTicketStatusHandler(
            TicketRepository ticketRepository,
            TicketAuthorization authorization,
            TicketWriter ticketWriter,
            EventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.authorization = authorization;
        this.ticketWriter = ticketWriter;
        this.eventPublisher = eventPublisher;
    }

    private static final String ROLE_TECNICO = "TECNICO";

    @Override
    public Ticket handle(UpdateTicketStatusCommand command) {
        Ticket ticket = ticketRepository.findByTicketId(command.ticketId())
                .orElseThrow(() -> new TicketNotFoundException(command.ticketId()));
        authorization.assertCanManage(ticket, command.role(), command.authZone());

        TicketStatus oldStatus = ticket.getStatus();

        // Idempotencia del cierre en sitio (Entregable 10): el movil reintenta el mismo PATCH
        // hasta 3 veces si no recibe respuesta (TicketRepository.kt), lo que puede repetir una
        // escritura que si llego al servidor. Si el ticket ya esta en el estado pedido, no hay
        // nada que cambiar -- se devuelve tal cual, sin recalcular resolvedAt/slaBreached (que
        // podria voltear de falso a verdadero solo por el tiempo transcurrido en el reintento)
        // ni volver a publicar el evento de cambio de estado, que ya se publico la vez que si
        // proceso.
        if (oldStatus == command.newStatus()) {
            return ticket;
        }

        ticket.setStatus(command.newStatus());
        if (command.newStatus() == TicketStatus.RESUELTO) {
            // La evidencia es obligatoria para TECNICO (el cierre en sitio real, vía movil);
            // un ADMIN puede resolver un ticket sin pasar por el movil (p.ej. corrigiendo un
            // estado a mano), asi que para ese rol se mantiene opcional a proposito.
            if (ROLE_TECNICO.equals(command.role())
                    && (command.evidencePhoto() == null
                    || command.evidenceLatitude() == null
                    || command.evidenceLongitude() == null)) {
                throw new IllegalArgumentException(
                        "El cierre en sitio requiere foto y coordenadas de evidencia");
            }
            ticket.setResolvedAt(OffsetDateTime.now());
            ticket.setSlaBreached(
                    ticket.getSlaDeadline() != null
                            && ticket.getResolvedAt().isAfter(ticket.getSlaDeadline())
            );
            if (command.evidencePhoto() != null) {
                ticket.setEvidencePhoto(command.evidencePhoto());
            }
            if (command.evidenceLatitude() != null) {
                ticket.setEvidenceLatitude(command.evidenceLatitude());
            }
            if (command.evidenceLongitude() != null) {
                ticket.setEvidenceLongitude(command.evidenceLongitude());
            }
        }
        Ticket saved = ticketWriter.saveWithRetry(ticket);
        publishStatusChanged(saved, oldStatus);
        return saved;
    }

    private void publishStatusChanged(Ticket ticket, TicketStatus oldStatus) {
        TicketStatusChangedEvent event = new TicketStatusChangedEvent(
                ticket.getId().toString(), ticket.getZone().name(), oldStatus.name(), ticket.getStatus().name());
        eventPublisher.publish(TOPIC_TICKET_STATUS_CHANGED, event.ticketId(), event);
    }
}
