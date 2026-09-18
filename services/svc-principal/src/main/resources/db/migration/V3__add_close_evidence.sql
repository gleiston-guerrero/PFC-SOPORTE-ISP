-- V3__add_close_evidence.sql
-- Entregable 10 de la guia de cierre ("Capacidades de dispositivo: camara y localizacion"):
-- la app movil ya captura la foto de evidencia y las coordenadas GPS al cerrar un ticket en
-- sitio, pero el contrato no tenia donde recibirlas ni el servicio donde guardarlas -- la
-- evidencia se quedaba dentro del telefono. Estas tres columnas nuevas son opcionales
-- (nullable) porque solo se llenan en el cierre en sitio (paso a RESUELTO); el resto de
-- transiciones de estado no las tocan.
--
-- La foto se guarda como BYTES directamente en la fila del ticket, no en un almacen de
-- objetos aparte (S3/MinIO): el proyecto no tiene esa infraestructura desplegada, y guardarla
-- junto a la fila evita depender de un volumen persistente extra en docker-compose.yml o de una
-- credencial de object storage que este entorno academico no tiene. El archivo es una sola foto
-- por ticket (no un adjunto masivo), pero sin comprimir en el movil son ~2,4 MB reales (~3,2 MB
-- en Base64 sobre el alambre) -- no "pocos cientos de KB" como decia una version anterior de
-- este comentario, que la propia evidencia versionada del Entregable 11 contradice. CockroachDB
-- no impone un limite practico a una columna BYTES de ese tamano; el limite de la capa REST
-- esta en UpdateStatusRequest (evidencePhotoBase64, @Size).

ALTER TABLE tickets ADD COLUMN IF NOT EXISTS evidence_photo BYTES;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS evidence_latitude FLOAT8;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS evidence_longitude FLOAT8;
