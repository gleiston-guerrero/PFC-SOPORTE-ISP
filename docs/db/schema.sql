-- ============================================================================
-- docs/db/schema.sql
-- Esquema desplegado del sistema (equipo ACC — Soporte Técnico ISP), consolidado en un solo
-- archivo por conveniencia de lectura. Este archivo NO se ejecuta -- es una vista del estado
-- FINAL del esquema tal como queda tras aplicar, en orden, todas las migraciones Flyway
-- versionadas que sí se ejecutan y que siguen siendo la fuente de verdad (Entregable 5 de la
-- guía de cierre; antes eran guiones sueltos de db-cluster/scripts/, no versionados como
-- cambios). No es una copia textual de ningún archivo individual: en particular, la tabla
-- "tickets" de más abajo integra las columnas que V3 le agrega a la tabla que V1 crea
-- (evidence_photo/evidence_latitude/evidence_longitude), porque este archivo documenta el
-- esquema final, no cada paso por separado.
--   - services/svc-principal/src/main/resources/db/migration/V1__init_ticket_schema.sql +
--     V2__configure_ticket_zone.sql (política de replicación, no un cambio de columnas) +
--     V3__add_close_evidence.sql (ticket_db, aplicadas por ticket-service al arrancar)
--   - services/auth-service/src/main/resources/db/migration/V1__init_auth_schema.sql
--     (auth_db, aplicado por auth-service al arrancar)
--   - services/report-service/src/main/resources/db/migration/V1__init_report_schema.sql
--     (report_db, aplicado por report-service al arrancar, lado de lectura del CQRS)
-- La base de datos en sí (CREATE DATABASE) la sigue creando "db-init" en
-- docker-compose.yml antes de que cada servicio arranque; Flyway conecta a una base
-- que ya existe. Ver docs/adr/0003-sharding-policy.md / docs/adr/0008-correl-incidencias.md
-- para la justificación de diseño de "tickets" (particionada por fecha_apertura) e
-- "incidencias" (agrupación de tickets por avería, CORREL) respectivamente.
-- ============================================================================


-- ============================================================
-- ticket_db — services/svc-principal/.../db/migration/V1__init_ticket_schema.sql
-- ============================================================

-- Tabla de técnicos (dimensión pequeña, no particionada)
CREATE TABLE IF NOT EXISTS technicians (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name STRING NOT NULL,
    zone STRING NOT NULL,          -- zona donde opera el técnico
    specialty STRING,
    active BOOL DEFAULT TRUE
);

-- Tabla principal de tickets, particionada horizontalmente por fecha de apertura
-- (fecha_apertura -> created_at), segun exige la Guia de Entrega 3 (Tabla 1, fila
-- ACC) -- reemplaza el esquema anterior por zona geografica (ver ADR-0003).
-- "created_at" debe ser la columna lider de la PK para que CockroachDB pueda
-- aplicar PARTITION BY RANGE sobre ella.
CREATE TABLE IF NOT EXISTS tickets (
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    zone            STRING NOT NULL,          -- 'QUEVEDO_CENTRO' | 'QUEVEDO_NORTE' | 'QUEVEDO_SUR' (ya no es la clave de fragmentacion, ver ADR-0003)
    client_id       UUID NOT NULL,
    technician_id   UUID REFERENCES technicians(id),
    category        STRING,                   -- CONECTIVIDAD | DNS | HARDWARE | CONFIGURACION | VELOCIDAD
    priority        STRING,                   -- CRITICO | ALTO | MEDIO | BAJO
    status          STRING NOT NULL DEFAULT 'NUEVO',
    description     STRING,
    sla_deadline    TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    sla_breached    BOOL DEFAULT FALSE,
    -- Evidencia del cierre en sitio (Entregable 10 de la guia de cierre, agregadas en
    -- db/migration/V3__add_close_evidence.sql). Nulas salvo en el cierre desde el movil.
    evidence_photo      BYTES,
    evidence_latitude   FLOAT8,
    evidence_longitude  FLOAT8,
    PRIMARY KEY (created_at, id)
) PARTITION BY RANGE (created_at) (
    PARTITION tickets_2026_q1 VALUES FROM (MINVALUE) TO ('2026-04-01T00:00:00Z'),
    PARTITION tickets_2026_q2 VALUES FROM ('2026-04-01T00:00:00Z') TO ('2026-07-01T00:00:00Z'),
    PARTITION tickets_2026_q3 VALUES FROM ('2026-07-01T00:00:00Z') TO ('2026-10-01T00:00:00Z'),
    PARTITION tickets_2026_q4 VALUES FROM ('2026-10-01T00:00:00Z') TO (MAXVALUE)
);

-- Punto de acceso mas comun del sistema: buscar un ticket por su id, sin conocer
-- su fecha_apertura de antemano (el id ya no es autosuficiente como PK -- ver
-- ADR-0003). Indice unico secundario para que ese lookup no tenga que escanear
-- las 4 particiones.
CREATE UNIQUE INDEX IF NOT EXISTS tickets_id_key ON tickets (id);

-- La zona sigue siendo un filtro de negocio real (RBAC de TECNICO, reportes por
-- zona) aunque ya no sea la clave de fragmentacion -- se indexa para que ese
-- filtro no degrade a un full scan.
CREATE INDEX IF NOT EXISTS idx_tickets_zone ON tickets (zone);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets (status);

-- Agrupacion de tickets por averia (Adicion 1 de la Ampliacion del Modulo G, equipo ACC --
-- ver docs/adr/0008-correl-incidencias.md). Tabla chica, no particionada, igual que
-- "technicians": no es el registro de negocio principal, es una agrupacion sobre "tickets".
-- OJO: "incidencias" (esta tabla) NO tiene relacion con "network_incidents_summary" de mas
-- abajo -- esa es un agregado de telemetria que llena Spark, esta es la agrupacion de
-- tickets que decide la estrategia de correlacion (CORREL) en tiempo real.
CREATE TABLE IF NOT EXISTS incidencias (
    id           UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    zone         STRING NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    correl_mode  STRING NOT NULL  -- c0 | c1 | c2: con que estrategia se abrio
);

CREATE INDEX IF NOT EXISTS idx_incidencias_zone_created_at ON incidencias (zone, created_at);

CREATE TABLE IF NOT EXISTS incidencia_tickets (
    incidencia_id  UUID NOT NULL REFERENCES incidencias(id),
    ticket_id      UUID NOT NULL,
    PRIMARY KEY (incidencia_id, ticket_id)
);

-- Tabla de incidencias de red (telemetria), respaldo del reporte agregado que
-- produce el pipeline Spark (Paso 8 — integracion). No se carga aqui; la llena
-- el job de Spark o un script de materializacion posterior.
CREATE TABLE IF NOT EXISTS network_incidents_summary (
    zone                STRING NOT NULL,
    period_hour         TIMESTAMPTZ NOT NULL,
    incident_type       STRING NOT NULL,
    incident_count      INT8 NOT NULL,
    avg_resolution_min  FLOAT8,
    mttr_min            FLOAT8,
    PRIMARY KEY (zone, period_hour, incident_type)
);


-- ============================================================
-- auth_db — services/auth-service/.../db/migration/V1__init_auth_schema.sql
-- ============================================================

-- Usuarios del sistema. El id lo genera la aplicacion (Hibernate GenerationType.UUID,
-- ver domain/User.java) antes del INSERT; el DEFAULT de aqui es solo un resguardo para
-- filas insertadas fuera de la aplicacion (por ejemplo, un script de seed manual).
CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         STRING UNIQUE NOT NULL,
    password_hash STRING NOT NULL,
    full_name     STRING NOT NULL,
    role          STRING NOT NULL,          -- CLIENTE | TECNICO | ADMIN (ver domain/Role.java)
    -- Solo se llena para TECNICO (ver AuthService.createUserAsAdmin). Debe usar
    -- exactamente los mismos valores que el enum Zone de ticket-service
    -- (QUEVEDO_CENTRO | QUEVEDO_NORTE | QUEVEDO_SUR) -- viaja como claim del JWT
    -- para que ticket-service filtre "tickets de mi zona" sin consultar otra base.
    zone          STRING,
    active        BOOL NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Refresh tokens: solo se persiste el hash SHA-256 del token crudo, nunca el valor
-- original (el valor crudo se devuelve una sola vez al cliente en la respuesta de
-- login/refresh). "revoked" + "replaced_by" sostienen la rotacion con deteccion de reuso:
-- si un token ya revocado se vuelve a presentar, se asume robo y se revocan todos los
-- tokens del usuario (ver service/AuthService.java).
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id),
    token_hash   STRING UNIQUE NOT NULL,
    issued_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked      BOOL NOT NULL DEFAULT false,
    replaced_by  UUID NULL REFERENCES refresh_tokens(id)
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);


-- ============================================================
-- report_db — services/report-service/.../db/migration/V1__init_report_schema.sql
-- ============================================================

-- Base de datos propia de report-service (lado de lectura del CQRS), equipo ACC.
-- Una fila por ticket, reconstruida unicamente a partir de los eventos de Kafka
-- publicados por ticket-service/ai-service (ticket.created, ticket.classified,
-- ticket.status-changed, ticket.assigned) -- ver config/ReportEventListener.java.
-- Completamente desacoplada de ticket_db.tickets: report-service nunca la consulta
-- ni depende de su esquema, solo de los eventos publicos.

CREATE TABLE IF NOT EXISTS ticket_summary (
    zone           STRING NOT NULL,
    ticket_id      UUID NOT NULL,
    client_id      UUID,
    technician_id  UUID,
    category       STRING,
    priority       STRING,
    status         STRING NOT NULL,
    description    STRING,
    created_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ,
    PRIMARY KEY (zone, ticket_id)
);
