# notification-service

Microservicio de notificaciones del PFC (equipo ACC — Soporte Tecnico ISP).
Node.js + Express, conectado a Kafka y a su propia base MongoDB.

## Responsabilidad

Consume los eventos que publica `ticket-service`
(`services/svc-principal/.../application/command/*Handler.java` -- `CreateTicketHandler`,
`UpdateTicketStatusHandler`, `AssignTechnicianHandler`, desde el refactor a 4 capas; antes
vivian juntos en el monolítico `TicketService.java`) y despacha una notificacion al cliente
por el canal adecuado:

| Evento | Canal(es) |
|---|---|
| `ticket.created` | EMAIL |
| `ticket.status-changed` | EMAIL |
| `ticket.assigned` | EMAIL + SMS (una asignacion es mas urgente) |

## Honestidad sobre el alcance (importante)

**No hay un proveedor real de email/SMS/push configurado** (no hay credenciales de
SendGrid/Twilio/FCM) — mismo criterio que el clasificador basado en reglas de `ai-service` y el
dataset sintetico de Spark: `src/dispatcher.js` decide el canal y el mensaje, y
`src/kafkaConsumer.js` **guarda** la notificacion simulada en MongoDB
(`notifications_db.notifications`, `{ticketId, zone, eventType, channel, message,
simulated: true, createdAt}`). Nada se envia de verdad.

## Como correrlo

### 1. Infraestructura (Kafka + MongoDB)

```bash
cd ../../messaging
docker compose -f docker-compose.messaging.yml up -d
```

### 2. Instalar dependencias y correr

```bash
cd services/notification-service
npm install
node src/index.js
```

Al arrancar, se conecta a Kafka (`localhost:9092` por defecto) y empieza a consumir
`ticket.created` / `ticket.status-changed` / `ticket.assigned`. Tambien expone una API REST
minima:

| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | `/health` | Estado del servicio |
| GET | `/api/v1/notifications?ticketId=...` | Notificaciones simuladas guardadas (todas, o filtradas por ticket) |

### 3. Probarlo de punta a punta

Con `ticket-service` corriendo y `notification-service` arriba, crea/actualiza/asigna un ticket
y confirma que aparecen documentos en Mongo:

```bash
curl "http://localhost:8003/api/v1/notifications?ticketId=<ticketId>"
```

Para una asignacion deberian aparecer 2 documentos (`EMAIL` y `SMS`).

## Variables de entorno (prefijo `NOTIFICATION_SERVICE_`)

| Variable | Default |
|---|---|
| `NOTIFICATION_SERVICE_PORT` | `8003` |
| `NOTIFICATION_SERVICE_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `NOTIFICATION_SERVICE_MONGO_URI` | `mongodb://localhost:27017` |
| `NOTIFICATION_SERVICE_MONGO_DB` | `notifications_db` |

## Tests

```bash
npm test
```

Pruebas unitarias puras de `src/dispatcher.js` (`test/dispatcher.test.js`, con el runner
integrado `node:test`, autodetectado por convencion en la carpeta `test/`) — no requieren Kafka
ni Mongo levantados, misma convencion de pruebas del resto del proyecto.

## Limitacion conocida

El endpoint `GET /api/v1/notifications` existe para depuracion/demo, pero todavia no esta
conectado al frontend — seguimiento natural, no parte de este alcance.
