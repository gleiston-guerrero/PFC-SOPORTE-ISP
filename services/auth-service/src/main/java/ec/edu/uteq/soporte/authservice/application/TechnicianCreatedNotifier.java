package ec.edu.uteq.soporte.authservice.application;

import ec.edu.uteq.soporte.authservice.domain.User;

/**
 * Puerto de salida (Entregable 1 de la guia de cierre): antes AuthService (application)
 * importaba infrastructure.messaging.TechnicianEventPublisher directamente. AuthService
 * depende solo de esta interfaz; TechnicianEventPublisher (infrastructure) la implementa.
 */
public interface TechnicianCreatedNotifier {
    void publishCreated(User technician);
}
