# ai-service

Microservicio de clasificacion asincrona de tickets del PFC (equipo ACC — Soporte Tecnico ISP).
Python 3.11 + FastAPI, conectado a Kafka y a su propia base MongoDB.

## Responsabilidad

Cierra la Saga por coreografia documentada en `ticket-service`
(`services/svc-principal/.../application/command/CreateTicketHandler.java`, desde el refactor a
4 capas — antes vivia en el monolítico `TicketService.java`): cuando se crea un ticket, queda con
`category`/`priority` en `null` — este servicio consume el evento `ticket.created`, clasifica el
ticket, y publica `ticket.classified` para que `ticket-service` complete esos campos (y recalcule
el SLA con la prioridad real).

## Honestidad sobre el alcance (importante)

**No hay un dataset real de tickets de soporte etiquetado** para entrenar un modelo de NLP — el
equipo no tiene acceso a un historico real de un ISP (mismo problema que ya se documento para el
dataset sintetico de Spark). Por eso, la "clasificacion" aqui es un **clasificador basado en
reglas/palabras clave** sobre el texto de la descripcion (ver `app/classifier.py`), no un modelo
de Machine Learning entrenado. Es un placeholder honesto y facil de reemplazar despues por un
modelo real si el equipo consigue datos etiquetados — no se presenta como NLP real en ningun lado.

## Cómo correrlo

### 1. Infraestructura (Kafka + MongoDB)

```bash
cd ../../messaging
docker compose -f docker-compose.messaging.yml up -d
```

### 2. Instalar dependencias y correr

```bash
cd services/ai-service
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8004
```

Al arrancar, se conecta a Kafka (`localhost:9092` por defecto) y lanza en un hilo de fondo el
consumidor de `ticket.created`. También expone una API REST mínima:

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/health` | Estado del servicio |
| GET | `/api/v1/ai/classifications/{ticket_id}` | Devuelve la clasificación guardada para un ticket (404 si aún no se procesó) |

### 3. Probarlo de punta a punta

Con `ticket-service` corriendo (publica `ticket.created`) y `ai-service` arriba:

```bash
curl -X POST http://localhost:8002/api/v1/tickets -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"zone":"QUEVEDO_NORTE","title":"Sin internet","description":"No tengo internet, corte total en toda la zona, urgente"}'
```

En unos segundos, `ai-service` debería haber clasificado el ticket como `CONECTIVIDAD` / `CRITICO`
y publicado `ticket.classified` — verificable en:

```bash
curl http://localhost:8004/api/v1/ai/classifications/<ticketId>
```

y también en el propio ticket, vía `ticket-service` (`category`/`priority` ya no serán `null`).

## Variables de entorno (prefijo `AI_SERVICE_`)

| Variable | Default |
|---|---|
| `AI_SERVICE_PORT` | `8004` |
| `AI_SERVICE_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `AI_SERVICE_MONGO_URI` | `mongodb://localhost:27017` |
| `AI_SERVICE_MONGO_DB` | `ai_db` |

## Tests

```bash
python -m pytest tests/ -v
```

Pruebas unitarias puras del clasificador (`tests/test_classifier.py`) — no requieren Kafka ni
Mongo levantados, igual que la convención de pruebas del resto del proyecto.

## Limitación conocida

El endpoint `GET /api/v1/ai/classifications/{ticket_id}` existe para depuración/demo, pero
todavía no está conectado al frontend (por ejemplo, un botón "ver sugerencia de IA" en el detalle
del ticket) — es un seguimiento natural, no parte de este alcance.
