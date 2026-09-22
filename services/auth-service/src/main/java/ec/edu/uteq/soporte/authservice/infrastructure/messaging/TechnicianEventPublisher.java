package ec.edu.uteq.soporte.authservice.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.soporte.authservice.application.TechnicianCreatedNotifier;
import ec.edu.uteq.soporte.authservice.domain.User;
import ec.edu.uteq.soporte.authservice.domain.event.TechnicianCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lado emisor de la Saga por coreografia (ver ADR-0004 de ticket-service): publica
 * "technician.created" cuando se da de alta un TECNICO, para que ticket-service pueda
 * asignarle tickets sin violar su llave foranea local. Igual que
 * ticket-service/KafkaEventPublisherAdapter, un fallo de Kafka NUNCA revierte ni bloquea
 * la creacion de la cuenta -- se registra y sigue: el usuario ya quedo creado en auth_db,
 * que es la fuente de verdad de identidad, y el mensaje se puede reintentar/reenviar
 * despues sin que el alta administrativa haya fallado a medias.
 */
@Component
public class TechnicianEventPublisher implements TechnicianCreatedNotifier {

    private static final String TOPIC_TECHNICIAN_CREATED = "technician.created";
    private static final Logger LOGGER = Logger.getLogger(TechnicianEventPublisher.class.getName());

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TechnicianEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishCreated(User technician) {
        TechnicianCreatedEvent event = new TechnicianCreatedEvent(
                technician.getId().toString(), technician.getFullName(), technician.getZone());
        try {
            kafkaTemplate.send(TOPIC_TECHNICIAN_CREATED, event.technicianId(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "No se pudo publicar technician.created para " + technician.getId(), e);
        }
    }
}
