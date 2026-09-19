-- seed_partitioned.sql
-- Carga de datos de ejemplo distribuidos a lo largo de todo 2026 para que las 4
-- particiones trimestrales de "tickets" (ver services/svc-principal/.../db/migration/
-- V1__init_ticket_schema.sql, PARTITION BY RANGE (created_at)) reciban filas de verdad -- 150000
-- filas espaciadas cada 210
-- segundos (3.5 min) desde el 1 de enero cubren aproximadamente los 4 trimestres
-- del anio. El volumen (150000 > 10^5) cumple el minimo del Modulo D de la Guia
-- de Entrega 3 (prueba de tolerancia a fallos, D2.2).
-- Ejecutar: cockroach sql --insecure --host=localhost:26257 -f seed_partitioned.sql

SET DATABASE = ticket_db;

INSERT INTO technicians (full_name, zone, specialty) VALUES
    ('Ana Morales',    'QUEVEDO_CENTRO', 'CONECTIVIDAD'),
    ('Luis Zambrano',  'QUEVEDO_CENTRO', 'HARDWARE'),
    ('Maria Cedeño',   'QUEVEDO_NORTE',  'DNS'),
    ('Pedro Vera',     'QUEVEDO_NORTE',  'CONFIGURACION'),
    ('Sofia Intriago', 'QUEVEDO_SUR',    'VELOCIDAD'),
    ('Jorge Alcivar',  'QUEVEDO_SUR',    'CONECTIVIDAD');

-- ~40% centro, ~30% norte, ~30% sur (zone ya no es la clave de fragmentacion,
-- pero se mantiene la misma distribucion realista para los filtros por zona del
-- RBAC de TECNICO y los reportes). created_at se espacia cada 210 segundos desde
-- el 1 de enero de 2026 para cubrir los 4 trimestres definidos en V1__init_ticket_schema.sql.
INSERT INTO tickets (created_at, zone, client_id, category, priority, status, description, sla_deadline)
SELECT
    TIMESTAMPTZ '2026-01-01 00:00:00+00' + (i * INTERVAL '210 seconds'),
    CASE
        WHEN (i % 10) < 4 THEN 'QUEVEDO_CENTRO'
        WHEN (i % 10) < 7 THEN 'QUEVEDO_NORTE'
        ELSE 'QUEVEDO_SUR'
    END,
    gen_random_uuid(),
    (ARRAY['CONECTIVIDAD','DNS','HARDWARE','CONFIGURACION','VELOCIDAD'])[1 + (i % 5)],
    (ARRAY['CRITICO','ALTO','MEDIO','BAJO'])[1 + (i % 4)],
    (ARRAY['NUEVO','ASIGNADO','EN_PROGRESO','RESUELTO','CERRADO'])[1 + (i % 5)],
    'Ticket sintetico de carga inicial #' || i,
    TIMESTAMPTZ '2026-01-01 00:00:00+00' + (i * INTERVAL '210 seconds') + INTERVAL '4 hours'
FROM generate_series(1, 150000) AS i;

-- Verificacion de distribucion real por particion trimestral (fragmentacion):
--   SHOW PARTITIONS FROM TABLE tickets;
--   SELECT
--     CASE
--       WHEN created_at < '2026-04-01' THEN 'tickets_2026_q1'
--       WHEN created_at < '2026-07-01' THEN 'tickets_2026_q2'
--       WHEN created_at < '2026-10-01' THEN 'tickets_2026_q3'
--       ELSE 'tickets_2026_q4'
--     END AS particion, count(*)
--   FROM tickets GROUP BY 1 ORDER BY 1;
-- Y por zona (dimension de negocio, ya no de fragmentacion):
--   SELECT zone, count(*) FROM tickets GROUP BY zone ORDER BY zone;
