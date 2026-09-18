package ec.edu.uteq.soporte.ticketservice.application;

import ec.edu.uteq.soporte.ticketservice.domain.Ticket;
import ec.edu.uteq.soporte.ticketservice.domain.TicketRepository;
import ec.edu.uteq.soporte.ticketservice.domain.TicketStatus;
import ec.edu.uteq.soporte.ticketservice.domain.Zone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketQueryServiceTest {

    private final TicketAuthorization authorization = new TicketAuthorization();

    @Mock
    private TicketRepository ticketRepository;

    private TicketQueryService service() {
        return new TicketQueryService(ticketRepository, authorization);
    }

    @Test
    void getTicket_byOwningCliente_isAllowed() {
        UUID id = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_SUR, id);
        existing.setClientId(clientId);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        Ticket result = service().getTicket(id, "CLIENTE", clientId, null);

        assertThat(result.getClientId()).isEqualTo(clientId);
    }

    @Test
    void getTicket_byNonOwningCliente_isForbidden() {
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_SUR, id);
        existing.setClientId(UUID.randomUUID());
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().getTicket(id, "CLIENTE", UUID.randomUUID(), null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getTicket_byTecnicoInAnotherZone_isForbidden() {
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_SUR, id);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().getTicket(id, "TECNICO", UUID.randomUUID(), Zone.QUEVEDO_CENTRO))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getTicket_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getTicket(id, "ADMIN", UUID.randomUUID(), null))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void listTickets_asCliente_onlyReturnsOwnTickets() {
        UUID clientId = UUID.randomUUID();
        Ticket own = ticketIn(Zone.QUEVEDO_NORTE, UUID.randomUUID());
        own.setClientId(clientId);
        when(ticketRepository.findByClientId(clientId)).thenReturn(List.of(own));

        List<Ticket> result = service().listTickets(Zone.QUEVEDO_SUR, null, "CLIENTE", clientId, null);

        // El parametro "zone" (QUEVEDO_SUR) se ignora: siempre se filtra por clientId.
        assertThat(result).containsExactly(own);
    }

    @Test
    void listTickets_asTecnico_onlyReturnsOwnZoneIgnoringZoneParam() {
        Ticket zoneTicket = ticketIn(Zone.QUEVEDO_NORTE, UUID.randomUUID());
        when(ticketRepository.findByZone(Zone.QUEVEDO_NORTE)).thenReturn(List.of(zoneTicket));

        List<Ticket> result = service().listTickets(
                Zone.QUEVEDO_SUR, null, "TECNICO", UUID.randomUUID(), Zone.QUEVEDO_NORTE);

        assertThat(result).containsExactly(zoneTicket);
    }

    @Test
    void listTickets_asTecnicoWithNoZone_returnsEmpty() {
        List<Ticket> result = service().listTickets(null, null, "TECNICO", UUID.randomUUID(), null);

        assertThat(result).isEmpty();
    }

    @Test
    void listTickets_asAdmin_withNoFilters_returnsAll() {
        Ticket t = ticketIn(Zone.QUEVEDO_SUR, UUID.randomUUID());
        when(ticketRepository.findAll()).thenReturn(List.of(t));

        List<Ticket> result = service().listTickets(null, null, "ADMIN", UUID.randomUUID(), null);

        assertThat(result).containsExactly(t);
    }

    @Test
    void listTickets_asAdmin_filteringByStatusOnly_crossesPartitions() {
        Ticket t = ticketIn(Zone.QUEVEDO_SUR, UUID.randomUUID());
        when(ticketRepository.findByStatus(TicketStatus.ESCALADO)).thenReturn(List.of(t));

        List<Ticket> result = service().listTickets(null, TicketStatus.ESCALADO, "ADMIN", UUID.randomUUID(), null);

        assertThat(result).containsExactly(t);
    }

    @Test
    void getTicket_byTecnicoInTheSameZone_isAllowed() {
        // Solo estaba probado el caso "otra zona -> forbidden"; el camino permitido de
        // TECNICO en getTicket nunca se ejercitaba.
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_NORTE, id);
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        Ticket result = service().getTicket(id, "TECNICO", UUID.randomUUID(), Zone.QUEVEDO_NORTE);

        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    void getTicket_byAdmin_isAllowedRegardlessOfOwnershipOrZone() {
        // La prueba existente de ADMIN en getTicket solo cubria el caso "no encontrado", nunca
        // el camino real de "encontrado y sin restriccion" que describe la tabla de
        // TicketAuthorization.
        UUID id = UUID.randomUUID();
        Ticket existing = ticketIn(Zone.QUEVEDO_SUR, id);
        existing.setClientId(UUID.randomUUID());
        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(existing));

        Ticket result = service().getTicket(id, "ADMIN", UUID.randomUUID(), Zone.QUEVEDO_NORTE);

        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    void listTickets_asCliente_withStatusFilter_usesFindByClientIdAndStatus() {
        // La unica prueba existente de CLIENTE dejaba status=null; la rama con status nunca
        // se ejercitaba, y usa un metodo de repositorio distinto (findByClientIdAndStatus).
        UUID clientId = UUID.randomUUID();
        Ticket own = ticketIn(Zone.QUEVEDO_NORTE, UUID.randomUUID());
        own.setClientId(clientId);
        when(ticketRepository.findByClientIdAndStatus(clientId, TicketStatus.RESUELTO)).thenReturn(List.of(own));

        List<Ticket> result = service().listTickets(null, TicketStatus.RESUELTO, "CLIENTE", clientId, null);

        assertThat(result).containsExactly(own);
    }

    @Test
    void listTickets_asTecnico_withStatusFilter_usesFindByZoneAndStatus() {
        Ticket t = ticketIn(Zone.QUEVEDO_NORTE, UUID.randomUUID());
        when(ticketRepository.findByZoneAndStatus(Zone.QUEVEDO_NORTE, TicketStatus.ASIGNADO)).thenReturn(List.of(t));

        List<Ticket> result = service().listTickets(
                null, TicketStatus.ASIGNADO, "TECNICO", UUID.randomUUID(), Zone.QUEVEDO_NORTE);

        assertThat(result).containsExactly(t);
    }

    @Test
    void listTickets_asAdmin_withZoneAndStatus_usesFindByZoneAndStatus() {
        Ticket t = ticketIn(Zone.QUEVEDO_CENTRO, UUID.randomUUID());
        when(ticketRepository.findByZoneAndStatus(Zone.QUEVEDO_CENTRO, TicketStatus.NUEVO)).thenReturn(List.of(t));

        List<Ticket> result = service().listTickets(
                Zone.QUEVEDO_CENTRO, TicketStatus.NUEVO, "ADMIN", UUID.randomUUID(), null);

        assertThat(result).containsExactly(t);
    }

    @Test
    void listTickets_asAdmin_withZoneOnly_usesFindByZone() {
        Ticket t = ticketIn(Zone.QUEVEDO_SUR, UUID.randomUUID());
        when(ticketRepository.findByZone(Zone.QUEVEDO_SUR)).thenReturn(List.of(t));

        List<Ticket> result = service().listTickets(Zone.QUEVEDO_SUR, null, "ADMIN", UUID.randomUUID(), null);

        assertThat(result).containsExactly(t);
    }

    @Test
    void listTickets_withAnUnrecognizedRole_returnsEmptyInsteadOfEverything() {
        // El fallback final del metodo: un rol que no sea ninguno de los tres reconocidos no
        // debe devolver todos los tickets por descarte -- debe devolver una lista vacia
        // (fail-closed), no una fuga de datos por un rol nuevo o mal escrito.
        List<Ticket> result = service().listTickets(null, null, "ROL_INVENTADO", UUID.randomUUID(), null);

        assertThat(result).isEmpty();
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
