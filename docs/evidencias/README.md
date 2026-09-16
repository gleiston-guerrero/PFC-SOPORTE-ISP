# Evidencias

## Entregable 11 — Integración web + móvil + backend

[`entregable11_integracion_movil_backend_20260916.md`](entregable11_integracion_movil_backend_20260916.md)
documenta el cierre en sitio de un ticket ejecutado el 2026-09-16 desde la app móvil instalada en
un dispositivo Android físico (no un emulador), con foto y GPS reales, contra el *stack* completo
de Docker levantado en local. Incluye las dos capturas de la app
([`entregable11_movil_evidencia_gps_foto_20260916.png`](entregable11_movil_evidencia_gps_foto_20260916.png),
[`entregable11_movil_ticket_resuelto_20260916.png`](entregable11_movil_ticket_resuelto_20260916.png))
emparejadas con la línea de log real de `ticket-service` y una consulta directa a CockroachDB
confirmando que la foto (2 417 037 bytes) y las coordenadas quedaron persistidas — log y consulta
crudos, sin editar, en
[`entregable11_backend_log_raw_20260916.txt`](entregable11_backend_log_raw_20260916.txt).

## Nota sobre `captura_demo_paso3_notificacion.png` (retirada)

Una auditoría externa (`PFC_E4_Guia_Consolidacion_ACC.pdf`, sección 3) detectó que este archivo
—ya retirado de esta carpeta— se había construido pegando la barra de título de
`captura_demo_ticket_notificacion.png` sobre un tramo inferior recortado de la misma imagen
(`split_demo_capturas.py`, que se conserva en esta carpeta sin cambios, por transparencia sobre
qué se hizo y por qué). El objetivo original era cumplir con una observación de mostrar la
secuencia como capturas independientes, pero el resultado presentaba un montaje como si fuera una
ventana de terminal genuina — eso es lo que se corrigió, no el dato subyacente (la respuesta HTTP
real que mostraba nunca se alteró).

**Reemplazo**: [`captura_demo_ticket_notificacion_real.png`](captura_demo_ticket_notificacion_real.png),
generada el 2026-09-03 contra el *stack* de la Entrega 4 realmente en ejecución (no un montaje ni
una reconstrucción): se autenticó como `admin@soporte.local` vía `api-gateway`, se creó un ticket
real (`POST /api/v1/tickets`, ticket `142e484c-c7f4-4f32-b52a-876dd63752c5`), se esperó a que
`notification-service` lo consumiera de Kafka, y se consultó la notificación real resultante
(`GET /api/v1/notifications`). El texto de la imagen es una transcripción exacta de esa respuesta
real — renderizada con estilo de terminal para el manuscrito (`apps/web/shot_terminal.cjs`, no
conservado en el repo por ser un script de un solo uso), no una captura de pantalla del sistema
operativo, y así se declara en el manuscrito.

`captura_demo_ticket_notificacion.png` (la composición original de julio de 2026) se conserva sin
cambios: es una captura real de un solo trazo, no un montaje, y documenta la misma arquitectura de
demo en la Entrega 3 (rutas directas a los microservicios, antes de que existiera `api-gateway`).

## Guion del video de tolerancia a fallos

[`guion_video_tolerancia_fallos.md`](guion_video_tolerancia_fallos.md) (y su versión
[`.docx`](guion_video_tolerancia_fallos.docx)) es el guion que se siguió para grabar
[`tolerancia_fallos.mp4`](tolerancia_fallos.mp4) — qué se muestra y en qué orden, para quien
quiera reproducir la grabación o verificar que el video corresponde al guion planeado.

[`tolerancia_fallos_original_5m34s.mp4`](tolerancia_fallos_original_5m34s.mp4) es la toma
completa sin editar (5 min 34 s) de la que se recortó la versión final; se conserva por
transparencia, para quien quiera verificar que el corte no alteró el contenido mostrado.
