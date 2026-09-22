# ticket-service (svc-principal)

Microservicio central del PFC (equipo ACC — Soporte Técnico ISP). Java 21 + Spring Boot 3.2,
conectado por JDBC al cluster CockroachDB del proyecto (carpeta `db-cluster/` en la raíz del repo).

## Cómo correrlo en VS Code

1. Instalar extensiones: **Extension Pack for Java** y **Spring Boot Extension Pack**.
2. Abrir esta carpeta (`services/svc-principal`) o el repo completo en VS Code.
3. Levantar primero el cluster CockroachDB (ver `../../db-cluster/README.md`) y crear la base
   vacía (`CREATE DATABASE IF NOT EXISTS ticket_db;`). Ni el esquema de tablas
   (`db/migration/V1__init_ticket_schema.sql`) ni la política de replicación
   (`V2__configure_ticket_zone.sql`) ni las columnas de evidencia del cierre en sitio
   (`V3__add_close_evidence.sql`) se cargan a mano: las tres las aplica Flyway automáticamente
   al arrancar el servicio, en ese orden (Entregable 5 de la guía de cierre) — el guion suelto
   `zones.sql` que esto pedía cargar manualmente ya no existe en el repositorio. Si hacen falta
   datos de demostración, `db-cluster/scripts/seed_partitioned.sql` (150 000 filas) sigue siendo
   manual, después de que el servicio haya arrancado al menos una vez y aplicado las migraciones.
4. Correr `TicketServiceApplication.java` con el botón "Run" que aparece sobre el `main`, o:
   ```bash
   mvn spring-boot:run
   ```
5. El servicio queda en `http://localhost:8002`.

## Endpoints (contrato REST heredado de la Entrega 2, Capítulo 7.3)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/v1/tickets` | Crear ticket (requiere `zone` y `clientId` en el body) |
| GET | `/api/v1/tickets?zone=&status=` | Listar tickets, filtro opcional por zona y/o estado |
| GET | `/api/v1/tickets/{zone}/{id}` | Detalle de un ticket |
| PATCH | `/api/v1/tickets/{zone}/{id}/status` | Cambiar estado |
| POST | `/api/v1/tickets/{zone}/{id}/assign?technicianId=` | Asignar técnico |

Ejemplo de creación:

```bash
curl -X POST http://localhost:8002/api/v1/tickets \
  -H "Content-Type: application/json" \
  -d '{
    "zone": "QUEVEDO_NORTE",
    "clientId": "11111111-1111-1111-1111-111111111111",
    "title": "Sin acceso a Internet",
    "description": "El router muestra luz roja desde las 08:00",
    "contactPhone": "0991234567",
    "address": "Av. Quevedo 123"
  }'
```

## Pendiente (fuera del alcance de este esqueleto)

- Integración real con `auth-service` (JWT) vía API Gateway — por ahora `clientId` se recibe
  directo en el request, no desde un token.
- Publicación de eventos a Kafka (`ticket.created`, etc. — Capítulo 5 y 6 de la E2). Se puede
  agregar con `spring-kafka` cuando el equipo retome esa parte.
- Verificar que el dialecto de Hibernate funciona sin fricción contra CockroachDB (algunas
  features avanzadas de PostgreSQL no están soportadas 1:1; para el CRUD básico de este servicio
  no debería haber problema, pero conviene probarlo apenas el cluster esté arriba).

## Nota sobre verificación

Sección obsoleta corregida (revisión propia posterior): describía el esqueleto inicial del
servicio como "generado con asistencia de IA, no pudo compilarse... correr `mvn clean test`, que
corre `TicketServiceTest`" — `TicketServiceTest` no existe desde el refactor a 4 capas (el
monolítico `TicketService.java` se dividió en los manejadores de `application/command/`), y el
servicio compila y pasa sus pruebas desde hace muchas rondas de este proyecto. Para verificar hoy:

```bash
mvn clean test      # 156 pruebas (1 omitida: TicketServiceProviderPactTest, solo con -Dpact.verifier.publishResults=true)
mvn clean package   # build completo
```
