package ec.edu.uteq.soporte.authservice.application;

import ec.edu.uteq.soporte.authservice.domain.User;
import io.jsonwebtoken.Claims;

import java.time.Duration;

/**
 * Puerto de salida (Entregable 1 de la guia de cierre): firmar/validar JWT es un detalle
 * tecnico de infrastructure (JwtService lo implementa), no una regla de negocio -- pero antes
 * AuthService (application) importaba JwtService (infrastructure) directamente, violando la
 * regla de capas que el manuscrito declara. AuthService depende solo de esta interfaz; Spring
 * inyecta la implementacion real por tipo, mismo patron que TransactionRetryMetrics en
 * ticket-service.
 */
public interface TokenIssuer {

    IssuedAccessToken generateAccessToken(User user);

    Claims parseAndValidate(String token);

    Duration refreshTokenTtl();
}
