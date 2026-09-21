# db-cluster — Cluster CockroachDB del PFC (equipo ACC)

## Cómo levantar el cluster

```bash
cd db-cluster
docker compose -f docker-compose.cockroach.yml up -d
docker exec -it roach1 cockroach init --insecure
```

Verificar en la consola web que los 3 nodos aparecen `live`: http://localhost:8080

## Cargar el esquema

Desde el Entregable 5 de la guía de cierre, el esquema de `ticket_db` es una migración Flyway
versionada (`services/svc-principal/src/main/resources/db/migration/V1__init_ticket_schema.sql`
+ `V2__configure_ticket_zone.sql` + `V3__add_close_evidence.sql`), no un guion suelto de esta
carpeta — normalmente las aplica el propio `ticket-service` al arrancar, contra una base que
crea `db-init` en `docker-compose.yml` antes de que el servicio arranque. Para las pruebas
manuales de este archivo (cluster levantado aparte, sin los microservicios ni `db-init`), hay
que crear la base primero: ninguna de las tres migraciones trae `CREATE DATABASE` (eso es
responsabilidad de `db-init`, no de Flyway), así que cargarlas sin crear `ticket_db` antes las
deja en `defaultdb` y V2 (`ALTER TABLE tickets CONFIGURE ZONE ...`, sin calificar la base) no
encontraría la tabla `tickets` fuera de `ticket_db` — no porque V2 cambie de base con una
sentencia SQL, sino porque `tickets` sin calificar se resuelve contra la base activa de la
sesión, que fija el flag `--database=` de cada comando, no el script en sí.

```bash
cockroach sql --insecure --host=localhost:26257 -e "CREATE DATABASE IF NOT EXISTS ticket_db;"
cockroach sql --insecure --host=localhost:26257 --database=ticket_db -f ../services/svc-principal/src/main/resources/db/migration/V1__init_ticket_schema.sql
cockroach sql --insecure --host=localhost:26257 --database=ticket_db -f ../services/svc-principal/src/main/resources/db/migration/V2__configure_ticket_zone.sql
cockroach sql --insecure --host=localhost:26257 --database=ticket_db -f ../services/svc-principal/src/main/resources/db/migration/V3__add_close_evidence.sql
cockroach sql --insecure --host=localhost:26257 -f scripts/seed_partitioned.sql
```

Verificado de extremo a extremo el 21/09 contra un cluster real (`roach1/2/3` del
`docker-compose.yml` raíz, reutilizando sus volúmenes con datos existentes): las tres
migraciones corren limpias en secuencia contra una base de prueba desechable
(`ticket_db_verify_test`, creada y eliminada solo para esta verificación, sin tocar
`ticket_db`), dejan las 5 tablas esperadas y las 4 particiones trimestrales de `tickets` con
`num_replicas = 3`, y `SHOW COLUMNS` confirma las tres columnas de evidencia
(`evidence_photo`, `evidence_latitude`, `evidence_longitude`) que añade V3.

Verificar la partición:

```sql
SHOW PARTITIONS FROM TABLE ticket_db.tickets;
SELECT zone, count(*) FROM ticket_db.tickets GROUP BY zone;
```

## Prueba de tolerancia a fallos (Paso 4)

Terminal 1 (carga sostenida):
```bash
pip install psycopg2-binary --break-system-packages
python scripts/load_write.py --duration 180 --rate 100
```

Terminal 2 (a mitad de la ejecución, ~90s después de iniciar):
```bash
docker stop roach2
# esperar 30-60s, observar que la carga sigue sin errores
docker start roach2
```

Grabar en simultáneo: consola web (8080), terminal 1 y el comando `docker stop`. Guardar el
video en `docs/evidencias/fault_tolerance.mp4` y un frame representativo en
`docs/evidencias/fault_tolerance.png`.

## Comparativa de rendimiento (Paso 5)

```bash
# Cluster de 3 nodos ya levantado arriba
cockroach sql --insecure --host=localhost:26257 -f scripts/queries_bench.sql > results_cluster.txt

# Nodo único para comparar
cockroach start-single-node --insecure --listen-addr=localhost:26260 --http-addr=localhost:8090 &
cockroach sql --insecure --host=localhost:26260 -f ../services/svc-principal/src/main/resources/db/migration/V1__init_ticket_schema.sql
cockroach sql --insecure --host=localhost:26260 -f scripts/seed_partitioned.sql
cockroach sql --insecure --host=localhost:26260 -f scripts/queries_bench.sql > results_single_node.txt
```

Comparar los tiempos de `EXPLAIN ANALYZE` de ambos archivos en la tabla del documento LaTeX.
