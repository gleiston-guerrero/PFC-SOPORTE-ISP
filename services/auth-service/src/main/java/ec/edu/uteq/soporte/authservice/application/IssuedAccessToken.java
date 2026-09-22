package ec.edu.uteq.soporte.authservice.application;

import java.time.OffsetDateTime;

/**
 * Puerto de salida (Entregable 1 de la guia de cierre, extendido a auth-service): antes vivia
 * como record anidado de infrastructure/security/JwtService, y AuthService lo usaba
 * directamente -- application dependiendo de infrastructure, la misma violacion que
 * ticket-service ya habia corregido para TransactionRetryMetrics/CrdbMetrics. Vive aqui para que
 * el puerto TokenIssuer pueda declararlo sin que application importe nada de infrastructure.
 */
public record IssuedAccessToken(String token, OffsetDateTime expiresAt) {
}
