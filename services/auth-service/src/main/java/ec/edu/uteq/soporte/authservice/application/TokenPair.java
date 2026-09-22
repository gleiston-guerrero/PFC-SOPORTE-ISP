package ec.edu.uteq.soporte.authservice.application;

import java.time.OffsetDateTime;

/**
 * Resultado de application (Entregable 1 de la guia de cierre): antes AuthService devolvia
 * directamente presentation.dto.AuthResponse -- application dependiendo de presentation, la
 * misma clase de violacion que las importaciones de infrastructure. AuthController mapea este
 * tipo a AuthResponse, mismo patron que TicketController mapea Ticket (dominio) a
 * TicketResponse.
 */
public record TokenPair(String accessToken, String refreshToken, OffsetDateTime accessTokenExpiresAt) {
}
