-- queries_bench.sql
-- 5 consultas representativas para el Paso 5 (analisis comparativo de rendimiento).
-- Ejecutar cada una con EXPLAIN ANALYZE en:
--   (a) el cluster de 3 nodos (puerto 26257)
--   (b) una instancia unica: cockroach start-single-node --insecure --listen-addr=localhost:26260
-- y volcar tiempo, filas leidas y filas devueltas a la tabla comparativa del documento.

SET DATABASE = ticket_db;

-- Q1: Lectura por id -- punto de acceso mas comun del sistema (ticket-service
-- recibe un UUID de ticket sin conocer su fecha_apertura de antemano). Ya no
-- resuelve dentro de la clave primaria (created_at, id): usa el indice unico
-- secundario tickets_id_key (ver V1__init_ticket_schema.sql), que evita escanear las 4
-- particiones a costa de un salto extra indice->tabla.
EXPLAIN ANALYZE
SELECT * FROM tickets WHERE id = (
    SELECT id FROM tickets LIMIT 1
);

-- Q2: Consulta de rango por fecha -- se resuelve dentro de una sola particion
-- trimestral (la fragmentacion SI aporta aqui, a diferencia de Q1).
EXPLAIN ANALYZE
SELECT id, zone, category, priority, status, created_at
FROM tickets
WHERE created_at >= now() - INTERVAL '24 hours'
ORDER BY created_at DESC;

-- Q3: Consulta filtrada por zona, sin filtro de fecha (cruza las 4 particiones
-- trimestrales -- desde que la fragmentacion es por fecha y no por zona, todo
-- filtro "por zona" es scatter-gather; ver ADR-0003, seccion de consecuencias).
EXPLAIN ANALYZE
SELECT zone, count(*) AS total
FROM tickets
WHERE zone = 'QUEVEDO_SUR'
GROUP BY zone;

-- Q4: Agregacion — SLA breach rate por zona y categoria
EXPLAIN ANALYZE
SELECT zone, category,
       count(*) AS total_tickets,
       sum(CASE WHEN sla_breached THEN 1 ELSE 0 END) AS breaches,
       round(100.0 * sum(CASE WHEN sla_breached THEN 1 ELSE 0 END) / count(*), 2) AS breach_pct
FROM tickets
GROUP BY zone, category
ORDER BY breach_pct DESC;

-- Q5: Join — tickets con datos del tecnico asignado
EXPLAIN ANALYZE
SELECT t.id, t.zone, t.category, t.status, tech.full_name, tech.specialty
FROM tickets t
JOIN technicians tech ON t.technician_id = tech.id
WHERE t.status IN ('ASIGNADO', 'EN_PROGRESO')
LIMIT 500;
