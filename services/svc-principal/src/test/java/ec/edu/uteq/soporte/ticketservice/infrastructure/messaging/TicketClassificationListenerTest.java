package ec.edu.uteq.soporte.ticketservice.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.soporte.ticketservice.domain.Category;
import ec.edu.uteq.soporte.ticketservice.domain.Priority;
import ec.edu.uteq.soporte.ticketservice.domain.Ticket;
import ec.edu.uteq.soporte.ticketservice.domain.TicketRepository;
import ec.edu.uteq.soporte.ticketservice.domain.TicketStatus;
import ec.edu.uteq.soporte.ticketservice.domain.Zone;
import ec.edu.uteq.soporte.ticketservice.domain.policy.ClassifiedSlaPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias puras del otro lado de la Saga por coreografia: dado un mensaje de
 * "ticket.classified" ya parseado, confirma que category/priority/SLA se aplican
 * correctamente -- sin Kafka real, usando el puerto TicketRepository y la estrategia
 * ClassifiedSlaPolicy real (es logica pura, no hace falta mockearla).
 */
@ExtendWith(MockitoExtension.class)
class TicketClassificationListenerTest {

    @Mock
    private TicketRepository ticketRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClassifiedSlaPolicy slaPolicy = new ClassifiedSlaPolicy();

    @Test
    void appliesCategoryPriorityAndRecomputesSlaFromPriority() {
        TicketClassificationListener listener = new TicketClassificationListener(ticketRepository, objectMapper, slaPolicy);
        UUID id = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now().minusMinutes(5);
        Ticket ticket = Ticket.builder()
                .zone(Zone.QUEVEDO_NORTE)
                .id(id)
                .clientId(UUID.randomUUID())
                .status(TicketStatus.NUEVO)
                .createdAt(createdAt)
                .slaDeadline(createdAt.plusHours(24))
                .slaBreached(false)
                .build();

        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(ticket));
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        when(ticketRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {"ticketId":"%s","zone":"QUEVEDO_NORTE","category":"CONECTIVIDAD","priority":"CRITICO"}
                """.formatted(id).strip();

        listener.onTicketClassified(payload);

        Ticket saved = captor.getValue();
        assertThat(saved.getCategory()).isEqualTo(Category.CONECTIVIDAD);
        assertThat(saved.getPriority()).isEqualTo(Priority.CRITICO);
        // CRITICO -> 4h de SLA desde la creacion (no las 24h por defecto originales)
        assertThat(saved.getSlaDeadline()).isEqualTo(createdAt.plus(Duration.ofHours(4)));
    }

    @Test
    void unknownTicketIsIgnoredWithoutThrowing() {
        TicketClassificationListener listener = new TicketClassificationListener(ticketRepository, objectMapper, slaPolicy);
        when(ticketRepository.findByTicketId(any())).thenReturn(Optional.empty());

        String payload = """
                {"ticketId":"%s","zone":"QUEVEDO_SUR","category":"DNS","priority":"BAJO"}
                """.formatted(UUID.randomUUID()).strip();

        listener.onTicketClassified(payload); // no debe lanzar

        verify(ticketRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void unrecognizedCategoryIsIgnoredButValidPriorityStillApplies() {
        // ai-service y ticket-service pueden desincronizar su vocabulario de categorias (ver
        // el javadoc del propio evento) -- un valor que no matchea ningun Category no debe
        // tumbar el listener, solo dejar esa categoria en null.
        TicketClassificationListener listener = new TicketClassificationListener(ticketRepository, objectMapper, slaPolicy);
        UUID id = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now().minusMinutes(5);
        Ticket ticket = Ticket.builder()
                .zone(Zone.QUEVEDO_NORTE)
                .id(id)
                .clientId(UUID.randomUUID())
                .status(TicketStatus.NUEVO)
                .createdAt(createdAt)
                .slaDeadline(createdAt.plusHours(24))
                .build();

        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(ticket));
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        when(ticketRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {"ticketId":"%s","zone":"QUEVEDO_NORTE","category":"CATEGORIA_INVENTADA","priority":"ALTO"}
                """.formatted(id).strip();

        listener.onTicketClassified(payload); // no debe lanzar

        Ticket saved = captor.getValue();
        assertThat(saved.getCategory()).isNull();
        assertThat(saved.getPriority()).isEqualTo(Priority.ALTO);
    }

    @Test
    void missingCategoryAndPriorityLeavesBothNullButStillSavesTheTicket() {
        // La rama value == null || isBlank() de parseEnumOrNull nunca se ejercitaba: los otros
        // tres payloads del archivo siempre mandan category/priority, validos o invalidos,
        // pero nunca ausentes.
        TicketClassificationListener listener = new TicketClassificationListener(ticketRepository, objectMapper, slaPolicy);
        UUID id = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .zone(Zone.QUEVEDO_SUR)
                .id(id)
                .clientId(UUID.randomUUID())
                .status(TicketStatus.NUEVO)
                .createdAt(OffsetDateTime.now())
                .build();

        when(ticketRepository.findByTicketId(id)).thenReturn(Optional.of(ticket));
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        when(ticketRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {"ticketId":"%s","zone":"QUEVEDO_SUR"}
                """.formatted(id).strip();

        listener.onTicketClassified(payload);

        Ticket saved = captor.getValue();
        assertThat(saved.getCategory()).isNull();
        assertThat(saved.getPriority()).isNull();
    }

    @Test
    void malformedPayloadIsIgnoredWithoutThrowing() {
        TicketClassificationListener listener = new TicketClassificationListener(ticketRepository, objectMapper, slaPolicy);

        listener.onTicketClassified("esto no es json"); // no debe lanzar

        verify(ticketRepository, org.mockito.Mockito.never()).save(any());
    }
}
