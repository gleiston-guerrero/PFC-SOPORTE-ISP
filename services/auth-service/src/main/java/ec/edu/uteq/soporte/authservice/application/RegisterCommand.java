package ec.edu.uteq.soporte.authservice.application;

/**
 * Comando de application (Entregable 1 de la guia de cierre): reemplaza a
 * presentation.dto.RegisterRequest como parametro de AuthService.register, para que application
 * no dependa de presentation. AuthController mapea RegisterRequest (con las anotaciones de Bean
 * Validation) a este comando plano, mismo patron que CreateTicketCommand en ticket-service.
 */
public record RegisterCommand(String email, String password, String fullName) {
}
