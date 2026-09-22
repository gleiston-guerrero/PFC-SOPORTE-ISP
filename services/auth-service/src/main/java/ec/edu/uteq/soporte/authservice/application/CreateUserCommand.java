package ec.edu.uteq.soporte.authservice.application;

import ec.edu.uteq.soporte.authservice.domain.Role;

/**
 * Comando de application (Entregable 1 de la guia de cierre): reemplaza a
 * presentation.dto.CreateUserRequest como parametro de AuthService.createUserAsAdmin, para que
 * application no dependa de presentation. AuthController mapea CreateUserRequest a este comando
 * plano, mismo patron que CreateTicketCommand en ticket-service.
 */
public record CreateUserCommand(String email, String password, String fullName, Role role, String zone) {
}
