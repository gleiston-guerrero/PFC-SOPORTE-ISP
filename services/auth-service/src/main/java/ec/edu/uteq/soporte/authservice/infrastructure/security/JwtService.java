package ec.edu.uteq.soporte.authservice.infrastructure.security;

import ec.edu.uteq.soporte.authservice.application.InvalidTokenException;
import ec.edu.uteq.soporte.authservice.application.IssuedAccessToken;
import ec.edu.uteq.soporte.authservice.application.TokenIssuer;
import ec.edu.uteq.soporte.authservice.domain.PermissionCatalog;
import ec.edu.uteq.soporte.authservice.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * Emision y validacion de access tokens (JWT firmados HS256). Los refresh tokens NO
 * son JWT -- son cadenas opacas de alta entropia cuyo hash se guarda en
 * `refresh_tokens` (ver application/AuthService); asi la fila en base de datos es la
 * unica fuente de verdad para revocacion, sin tener que mantener sincronizadas dos
 * nociones de expiracion (la del JWT y la de la base).
 *
 * <p>Vive en infrastructure/security (no en application) porque firmar/validar JWT es
 * un detalle tecnico de como se implementa la autenticacion, no una regla de negocio
 * -- mismo criterio que TelemetryGrpcClientAdapter vive en infrastructure/grpc en
 * ticket-service.
 */
@Service
public class JwtService implements TokenIssuer {

    private final SecretKey signingKey;
    private final long accessTtlMinutes;
    private final long refreshTtlDays;

    public JwtService(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.access-ttl-minutes}") long accessTtlMinutes,
            @Value("${auth.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMinutes = accessTtlMinutes;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Override
    public IssuedAccessToken generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(accessTtlMinutes));

        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("permissions", PermissionCatalog.permissionsFor(user.getRole()))
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        if (user.getZone() != null) {
            builder.claim("zone", user.getZone());
        }
        String token = builder.signWith(signingKey).compact();

        return new IssuedAccessToken(token, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    @Override
    public Claims parseAndValidate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Token invalido o expirado: " + e.getMessage());
        }
    }

    @Override
    public Duration refreshTokenTtl() {
        return Duration.ofDays(refreshTtlDays);
    }
}
