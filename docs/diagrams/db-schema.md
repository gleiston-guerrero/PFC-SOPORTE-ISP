# Esquema de `ticket_db` (CockroachDB, cluster de 3 nodos)

Corresponde a las migraciones Flyway `services/svc-principal/src/main/resources/db/migration/V1__init_ticket_schema.sql`
(las cinco tablas, Entregable 5 de la guía de cierre) y `V3__add_close_evidence.sql` (las tres
columnas de evidencia de `tickets`, Entregable 10 — `V2__configure_ticket_zone.sql` no cambia
columnas, solo la política de replicación, ver la sección "Diseño del esquema y fragmentación"
del manuscrito, `docs/latex/secciones/diseno_esquema.tex`). `tickets` es la tabla de mayor
cardinalidad y la única fragmentada (`PARTITION BY RANGE
(created_at)`, ver [ADR-0003](../adr/0003-sharding-policy.md)); `technicians`, `incidencias` e
`incidencia_tickets` son dimensiones pequeñas sin particionar.

```mermaid
erDiagram
    TICKETS {
        TIMESTAMPTZ created_at PK "particion: RANGE, 4 trimestres 2026"
        UUID id PK "indice unico secundario tickets_id_key"
        STRING zone "indexado (idx_tickets_zone), ya NO es la clave de fragmentacion"
        UUID client_id "SIN indice propio -- findByClientId escanea sin apoyo de indice (verificado: SHOW INDEXES FROM tickets solo lista tickets_pkey, tickets_id_key, idx_tickets_zone e idx_tickets_status)"
        UUID technician_id FK "nullable, referencia technicians(id)"
        STRING category "nullable, la completa ai-service via Kafka"
        STRING priority "nullable, la completa ai-service via Kafka"
        STRING status "NUEVO por defecto, indexado (idx_tickets_status)"
        STRING description
        TIMESTAMPTZ sla_deadline
        TIMESTAMPTZ resolved_at
        BOOL sla_breached
        BYTES evidence_photo "nullable, V3 -- foto del cierre en sitio, solo TECNICO"
        FLOAT8 evidence_latitude "nullable, V3 -- rango [-90,90] validado en la API"
        FLOAT8 evidence_longitude "nullable, V3 -- rango [-180,180] validado en la API"
    }

    TECHNICIANS {
        UUID id PK "gen_random_uuid()"
        STRING full_name
        STRING zone "zona donde opera (no relacionado a la particion de tickets)"
        STRING specialty
        BOOL active
    }

    INCIDENCIAS {
        UUID id PK "gen_random_uuid()"
        STRING zone "indexado junto con created_at (idx_incidencias_zone_created_at)"
        TIMESTAMPTZ created_at "indexado junto con zone"
        STRING correl_mode "c0 | c1 | c2 -- estrategia CORREL con que se abrio"
    }

    INCIDENCIA_TICKETS {
        UUID incidencia_id PK, FK "referencia incidencias(id)"
        UUID ticket_id PK "NOT NULL, sin FK declarada a tickets(id) -- misma base (ticket_db), la migracion V1 simplemente no la declaro"
    }

    NETWORK_INCIDENTS_SUMMARY {
        STRING zone PK
        TIMESTAMPTZ period_hour PK
        STRING incident_type PK
        INT8 incident_count
        FLOAT8 avg_resolution_min
        FLOAT8 mttr_min
    }

    TICKETS }o--o| TECHNICIANS : "technician_id -> id"
    INCIDENCIA_TICKETS }o--|| INCIDENCIAS : "incidencia_id -> id"
```

`incidencia_tickets.ticket_id` no lleva una restricción `REFERENCES tickets(id)` real en
`V1__init_ticket_schema.sql` —se deja como `UUID NOT NULL` simple, dentro de la misma base
`ticket_db` que `tickets` (verificado: `SHOW TABLES` lista ambas)—, así que el diagrama no dibuja
esa relación como una FK aplicada por la base; la integridad la mantiene la lógica de
`CorrelationService` (Sección de Arquitectura), no una restricción de esquema.

## Notas de diseño

- **Colocalización con `clientes`**: la guía de la Entrega 3 pide colocalizar `tickets` con
  `clientes` por `client_id`. Los clientes viven en `auth_db.users`, propiedad de `auth-service`
  — una base de datos física distinta, por diseño de microservicios (cada servicio es dueño de su
  propio esquema). Ver la sección correspondiente del ADR-0003 para la discusión completa de este
  trade-off.
- **`network_incidents_summary`**: tabla de respaldo para materializar el resultado del pipeline
  de Spark (ver `spark/README.md`, sección de integración) — no se llena desde `ticket-service`,
  la llena un job de Spark o un script de materialización aparte.
- El esquema completo de los otros 4 microservicios (`auth_db`, `report_db`, `ai_db`,
  `notifications_db`) vive en sus propias migraciones Flyway:
  `services/auth-service/src/main/resources/db/migration/V1__init_auth_schema.sql`,
  `services/report-service/src/main/resources/db/migration/V1__init_report_schema.sql`, y las
  colecciones de MongoDB documentadas en los READMEs de `ai-service`/`notification-service`
  respectivamente.
