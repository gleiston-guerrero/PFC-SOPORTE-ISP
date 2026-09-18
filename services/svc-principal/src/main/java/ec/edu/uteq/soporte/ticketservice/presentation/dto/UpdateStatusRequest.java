package ec.edu.uteq.soporte.ticketservice.presentation.dto;

import ec.edu.uteq.soporte.ticketservice.domain.TicketStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * evidencePhotoBase64/latitude/longitude son opcionales (Entregable 10 de la guia de cierre):
 * solo los manda el movil en el cierre en sitio (status = RESUELTO). El resto de transiciones
 * de estado (ASIGNADO, EN_PROGRESO, etc.) los deja en null. Las cotas de latitude/longitude
 * son el rango valido de coordenadas geograficas, no el area de cobertura del ISP -- rechazar
 * aqui solo lo geometricamente imposible; el limite de tamano en evidencePhotoBase64 (unos 6 MB
 * decodificados) cubre una foto real sin comprimir (~2,4 MB, ~3,2 MB en Base64) con margen,
 * evitando aceptar un payload arbitrariamente grande.
 */
public record UpdateStatusRequest(
        @NotNull TicketStatus status,
        @Size(max = 8_000_000, message = "evidencePhotoBase64 supera el tamano maximo permitido")
        String evidencePhotoBase64,
        @DecimalMin(value = "-90.0", message = "latitude fuera de rango [-90, 90]")
        @DecimalMax(value = "90.0", message = "latitude fuera de rango [-90, 90]")
        Double latitude,
        @DecimalMin(value = "-180.0", message = "longitude fuera de rango [-180, 180]")
        @DecimalMax(value = "180.0", message = "longitude fuera de rango [-180, 180]")
        Double longitude
) {
}
