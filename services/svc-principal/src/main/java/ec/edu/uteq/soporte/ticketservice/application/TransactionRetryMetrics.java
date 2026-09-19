package ec.edu.uteq.soporte.ticketservice.application;

/**
 * Puerto de salida (Entregable 1 de la guia de cierre): antes, TicketWriter (application/)
 * importaba directamente infrastructure.metrics.CrdbMetrics, violando la regla de capas
 * estricta que el manuscrito declara para este servicio ("las capas externas dependen de las
 * internas, nunca al reves"). CrdbMetrics implementa esta interfaz; TicketWriter depende solo
 * de ella, y Spring inyecta la implementacion real por tipo.
 */
public interface TransactionRetryMetrics {
    void incrementTransactionRetries();
}
