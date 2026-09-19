package ec.edu.uteq.soporte.ticketservice.application;

import ec.edu.uteq.soporte.ticketservice.domain.Ticket;
import ec.edu.uteq.soporte.ticketservice.domain.TicketRepository;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CockroachDB usa aislamiento serializable: cualquier escritura puede abortar por un
 * conflicto con otra transaccion concurrente (visto en vivo en report-service, ver
 * ReportEventListener.applyWithRetry) y Spring la traduce a ConcurrencyFailureException. Se
 * reintenta una sola vez, incrementando crdb_transaction_retries_total (D3.2) -- un segundo
 * conflicto seguido se deja propagar en vez de reintentar indefinidamente.
 *
 * Compartido por los tres manejadores de comando (application/command/) para no repetir esta
 * logica en cada uno.
 */
@Component
public class TicketWriter {

    private static final Logger LOGGER = Logger.getLogger(TicketWriter.class.getName());

    private final TicketRepository ticketRepository;
    private final TransactionRetryMetrics crdbMetrics;

    public TicketWriter(TicketRepository ticketRepository, TransactionRetryMetrics crdbMetrics) {
        this.ticketRepository = ticketRepository;
        this.crdbMetrics = crdbMetrics;
    }

    public Ticket saveWithRetry(Ticket ticket) {
        try {
            return ticketRepository.save(ticket);
        } catch (ConcurrencyFailureException e) {
            crdbMetrics.incrementTransactionRetries();
            LOGGER.log(Level.INFO, "Conflicto de escritura serializable para " + ticket.getId() + ", reintentando una vez", e);
            return ticketRepository.save(ticket);
        }
    }
}
