# frontend — Panel de Tickets (equipo ACC)

> **Obsoleto desde la Entrega 4** (revisión propia posterior, no parte de esta corrección
> original): este dashboard vanilla es el cliente web de la Entrega 2/3, reemplazado por
> `apps/web` (React) como frontend oficial — así lo declara
> `docs/diagrams/c4-nivel2-contenedores.md` ("se retira el `frontend` HTML/CSS/JS vanilla"). Se
> deja en el repositorio como referencia histórica, no como una ruta soportada. El modo "contra
> el backend real" de abajo **ya no funciona tal cual está escrito**: `CorsConfig.java`, la
> clase que habilitaba CORS para que este dashboard (servido en un puerto aparte, 5500) pudiera
> llamar a `ticket-service` en 8002, se retiró del código — no se necesita para `apps/web`,
> que pasa todo por `api-gateway` en el mismo origen. El modo demo sin backend (`test/mock_server.py`,
> más abajo) sigue funcionando porque no depende de CORS.

Dashboard estático (HTML + CSS + JS vanilla, sin build step) que consume la API real de
`ticket-service`. Pensado para mostrar el sistema en vivo: KPIs, distribución de tickets por
zona/estado (ligado a la fragmentación de CockroachDB), y un tablero Kanban editable.

## Cómo correrlo contra el backend real (obsoleto, ver nota arriba)

1. Levantar el cluster CockroachDB + `ticket-service` (ver `../db-cluster/README.md` y
   `../services/svc-principal/README.md`). **Ya no tiene CORS habilitado** (la clase
   `CorsConfig.java` que esto asumía se retiró del código de `ticket-service`): sin volver a
   agregar una configuración de CORS al microservicio, el navegador rechazará las llamadas de
   este dashboard por origen cruzado (puerto 5500 → 8002).
2. Servir esta carpeta como archivos estáticos — cualquiera de estas opciones funciona:
   - Extensión **Live Server** de VS Code: clic derecho sobre `index.html` → "Open with Live Server".
   - `python3 -m http.server 5500` desde esta carpeta, y abrir `http://localhost:5500`.
3. Abrir el navegador. El indicador de conexión (abajo a la izquierda) debe ponerse verde
   ("Conectado a ticket-service"). Si sale en rojo, revisar que el microservicio esté corriendo
   en `localhost:8002` (la URL está fija en la primera línea útil de `app.js`, constante `API_BASE`).

## Modo demo sin backend (para practicar o si falla el cluster el día de la defensa)

Incluí `test/mock_server.py`, un servidor mínimo que imita el contrato REST real de
`ticket-service` (mismo formato de respuesta `{data, message, timestamp}`) con 3 tickets de
ejemplo en las 3 zonas. Sirve para:
- Iterar sobre el diseño del frontend sin tener Docker/CockroachDB levantado.
- Tener un plan B para la demo si el cluster real falla en el momento — **aclarando siempre
  al profesor que es un mock**, nunca presentarlo como el sistema real.

```bash
python3 test/mock_server.py
```

Corre en el mismo puerto (8002) que el microservicio real, así que no hace falta cambiar nada
en `app.js` — simplemente no tengas ambos corriendo al mismo tiempo.

## Qué muestra

- **Dashboard**: KPIs (total, abiertos, SLA vencido, resueltos/cerrados) y dos gráficos
  (Chart.js, vía CDN): distribución por zona — ligada 1:1 a las particiones
  `PARTITION BY LIST (zone)` del cluster — y distribución por estado.
- **Tablero de Tickets**: Kanban con las 6 columnas del ciclo de vida (NUEVO → ASIGNADO →
  EN_PROGRESO → ESCALADO/RESUELTO → CERRADO). Cada tarjeta permite cambiar el estado desde un
  selector, y un clic abre el detalle completo del ticket.
- Los tickets recién creados muestran "categoría/prioridad: pendiente IA" en vez de un valor —
  es un recordatorio visual honesto de que `ai-service` (Kafka) todavía no está conectado.

## Verificación ya realizada

Antes de entregarte esto, corrí una prueba automatizada (jsdom + un servidor mock) que carga
`index.html`, ejecuta `app.js` de verdad, y confirma que: los KPIs se calculan bien, las 6
columnas del tablero se arman, y crear un ticket vía el formulario actualiza el conteo en
pantalla. Lo que **no** pude probar desde este entorno es el flujo contra el CockroachDB real
(por eso conviene que lo primero que hagas sea abrirlo con el cluster ya levantado y confirmar
que el indicador de conexión se pone verde).

## Personalización rápida

- Colores de zona/estado: al principio de `app.js`, arrays `ZONES` y `STATUSES`.
- URL del backend: constante `API_BASE` en `app.js`.
- Frecuencia de auto-refresco: constante `POLL_INTERVAL_MS` (15 segundos por defecto).
