package ec.edu.uteq.soporte.authservice.application;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Resultado de application (Entregable 1 de la guia de cierre): reemplaza a
 * presentation.dto.ValidateResponse como tipo de retorno de AuthService.validate, para que
 * application no dependa de presentation. AuthController mapea este tipo a ValidateResponse.
 */
public record TokenValidation(
        String userId,
        String email,
        String role,
        String zone,
        List<String> permissions,
        OffsetDateTime expiresAt) {
}
