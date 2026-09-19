package ec.edu.uteq.soporte.ticketservice.application;

import ec.edu.uteq.soporte.ticketservice.domain.Ticket;
import ec.edu.uteq.soporte.ticketservice.domain.TicketRepository;
import ec.edu.uteq.soporte.ticketservice.domain.TicketStatus;
import ec.edu.uteq.soporte.ticketservice.domain.Zone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.ConcurrencyFailureException;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketWriterTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TransactionRetryMetrics crdbMetrics;

    @Test
    void saveWithRetry_retriesOnceOnSerializableConflictAndIncrementsMetric() {
        TicketWriter writer = new TicketWriter(ticketRepository, crdbMetrics);
        Ticket ticket = Ticket.builder()
                .id(UUID.randomUUID()).zone(Zone.QUEVEDO_NORTE).clientId(UUID.randomUUID())
                .status(TicketStatus.NUEVO).createdAt(OffsetDateTime.now()).build();

        when(ticketRepository.save(any(Ticket.class)))
                .thenThrow(new ConcurrencyFailureException("conflicto de escritura simulado"))
                .thenAnswer(inv -> inv.getArgument(0));

        Ticket result = writer.saveWithRetry(ticket);

        assertThat(result).isNotNull();
        verify(crdbMetrics).incrementTransactionRetries();
    }

    @Test
    void saveWithRetry_noConflict_savesOnceWithoutIncrementingMetric() {
        TicketWriter writer = new TicketWriter(ticketRepository, crdbMetrics);
        Ticket ticket = Ticket.builder()
                .id(UUID.randomUUID()).zone(Zone.QUEVEDO_SUR).clientId(UUID.randomUUID())
                .status(TicketStatus.NUEVO).createdAt(OffsetDateTime.now()).build();

        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        writer.saveWithRetry(ticket);

        verify(crdbMetrics, org.mockito.Mockito.never()).incrementTransactionRetries();
    }
}
