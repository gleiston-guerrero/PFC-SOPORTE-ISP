package ec.edu.uteq.soporte.ticketservice.presentation;

import ec.edu.uteq.soporte.ticketservice.application.TicketQueryService;
import ec.edu.uteq.soporte.ticketservice.application.command.AssignTechnicianCommand;
import ec.edu.uteq.soporte.ticketservice.application.command.AssignTechnicianHandler;
import ec.edu.uteq.soporte.ticketservice.application.command.CreateTicketCommand;
import ec.edu.uteq.soporte.ticketservice.application.command.CreateTicketHandler;
import ec.edu.uteq.soporte.ticketservice.application.command.UpdateTicketStatusCommand;
import ec.edu.uteq.soporte.ticketservice.application.command.UpdateTicketStatusHandler;
import ec.edu.uteq.soporte.ticketservice.domain.Ticket;
import ec.edu.uteq.soporte.ticketservice.domain.TicketStatus;
import ec.edu.uteq.soporte.ticketservice.domain.Zone;
import ec.edu.uteq.soporte.ticketservice.presentation.dto.CreateTicketRequest;
import ec.edu.uteq.soporte.ticketservice.presentation.dto.TicketResponse;
import ec.edu.uteq.soporte.ticketservice.presentation.dto.UpdateStatusRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TicketController no tenia ninguna prueba propia (los manejadores a los que delega si la
 * tienen, extensamente). Se prueba con los manejadores mockeados, sin MockMvc ni contexto de
 * Spring -- los parametros @RequestAttribute/@PathVariable los resuelve el framework, no
 * logica de esta clase; lo que si es logica propia del controlador es el decodificado Base64
 * de la evidencia y el armado de cada Command, que es lo que estas pruebas cubren.
 */
@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private CreateTicketHandler createTicketHandler;
    @Mock
    private UpdateTicketStatusHandler updateTicketStatusHandler;
    @Mock
    private AssignTechnicianHandler assignTechnicianHandler;
    @Mock
    private TicketQueryService ticketQueryService;

    private TicketController controller() {
        return new TicketController(createTicketHandler, updateTicketStatusHandler, assignTechnicianHandler, ticketQueryService);
    }

    @Test
    void createTicketArmaElCommandConLosDatosDelRequestYDelTokenValidado() {
        UUID clientId = UUID.randomUUID();
        CreateTicketRequest request = new CreateTicketRequest(Zone.QUEVEDO_NORTE, "Sin senal", "desc", "099", "Av x");
        when(createTicketHandler.handle(any())).thenReturn(ticket());

        controller().createTicket(request, clientId, "CLIENTE");

        ArgumentCaptor<CreateTicketCommand> captor = ArgumentCaptor.forClass(CreateTicketCommand.class);
        verify(createTicketHandler).handle(captor.capture());
        CreateTicketCommand command = captor.getValue();
        assertThat(command.clientId()).isEqualTo(clientId);
        assertThat(command.role()).isEqualTo("CLIENTE");
        assertThat(command.zone()).isEqualTo(Zone.QUEVEDO_NORTE);
    }

    @Test
    void updateStatusSinEvidenciaPasaNullSinIntentarDecodificar() {
        UpdateStatusRequest request = new UpdateStatusRequest(TicketStatus.ASIGNADO, null, null, null);
        when(updateTicketStatusHandler.handle(any())).thenReturn(ticket());

        controller().updateStatus(UUID.randomUUID(), request, "TECNICO", Zone.QUEVEDO_NORTE);

        ArgumentCaptor<UpdateTicketStatusCommand> captor = ArgumentCaptor.forClass(UpdateTicketStatusCommand.class);
        verify(updateTicketStatusHandler).handle(captor.capture());
        assertThat(captor.getValue().evidencePhoto()).isNull();
    }

    @Test
    void updateStatusConEvidenciaValidaLaDecodificaCorrectamente() {
        byte[] fotoOriginal = {1, 2, 3, 4, 5};
        String base64 = Base64.getEncoder().encodeToString(fotoOriginal);
        UpdateStatusRequest request = new UpdateStatusRequest(TicketStatus.RESUELTO, base64, -1.02, -79.46);
        when(updateTicketStatusHandler.handle(any())).thenReturn(ticket());

        controller().updateStatus(UUID.randomUUID(), request, "TECNICO", Zone.QUEVEDO_NORTE);

        ArgumentCaptor<UpdateTicketStatusCommand> captor = ArgumentCaptor.forClass(UpdateTicketStatusCommand.class);
        verify(updateTicketStatusHandler).handle(captor.capture());
        assertThat(captor.getValue().evidencePhoto()).isEqualTo(fotoOriginal);
        assertThat(captor.getValue().evidenceLatitude()).isEqualTo(-1.02);
        assertThat(captor.getValue().evidenceLongitude()).isEqualTo(-79.46);
    }

    @Test
    void updateStatusConBase64InvalidoLanzaIllegalArgumentSinTocarElManejador() {
        // El decodificado Base64 (logica propia del controlador) lanza IllegalArgumentException
        // para un evidencePhotoBase64 malformado; GlobalExceptionHandler.handleIllegalArgument
        // la traduce a 400 en el stack HTTP real (Entregable 10). Esta prueba llama al
        // controlador directo, sin Spring, por lo que solo verifica el origen de la excepcion;
        // el 400 esta cubierto en GlobalExceptionHandlerTest.
        UpdateStatusRequest request = new UpdateStatusRequest(TicketStatus.RESUELTO, "esto-no-es-base64-valido!!", -1.0, -79.0);

        assertThatThrownBy(() -> controller().updateStatus(UUID.randomUUID(), request, "TECNICO", Zone.QUEVEDO_NORTE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignTechnicianArmaElCommandConElIdDelTicketYDelTecnico() {
        UUID ticketId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        when(assignTechnicianHandler.handle(any())).thenReturn(ticket());

        controller().assignTechnician(ticketId, technicianId, "ADMIN", null);

        ArgumentCaptor<AssignTechnicianCommand> captor = ArgumentCaptor.forClass(AssignTechnicianCommand.class);
        verify(assignTechnicianHandler).handle(captor.capture());
        assertThat(captor.getValue().ticketId()).isEqualTo(ticketId);
        assertThat(captor.getValue().technicianId()).isEqualTo(technicianId);
    }

    @Test
    void listTicketsDelegaLosFiltrosTalCualYMapeaElResultado() {
        UUID userId = UUID.randomUUID();
        when(ticketQueryService.listTickets(Zone.QUEVEDO_SUR, TicketStatus.NUEVO, "TECNICO", userId, Zone.QUEVEDO_SUR))
                .thenReturn(List.of(ticket()));

        var respuesta = controller().listTickets(Zone.QUEVEDO_SUR, TicketStatus.NUEVO, "TECNICO", userId, Zone.QUEVEDO_SUR);

        assertThat(respuesta.data()).hasSize(1);
        assertThat(respuesta.data().get(0)).isInstanceOf(TicketResponse.class);
    }

    @Test
    void getTicketDelegaElIdYLosAtributosDeAutenticacionTalCual() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(ticketQueryService.getTicket(id, "CLIENTE", userId, null)).thenReturn(ticket());

        controller().getTicket(id, "CLIENTE", userId, null);

        verify(ticketQueryService).getTicket(id, "CLIENTE", userId, null);
    }

    private Ticket ticket() {
        return Ticket.builder()
                .id(UUID.randomUUID())
                .createdAt(OffsetDateTime.now())
                .zone(Zone.QUEVEDO_NORTE)
                .clientId(UUID.randomUUID())
                .status(TicketStatus.NUEVO)
                .description("desc")
                .slaDeadline(OffsetDateTime.now().plusHours(24))
                .slaBreached(false)
                .build();
    }
}
