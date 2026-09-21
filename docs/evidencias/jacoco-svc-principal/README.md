# Informe JaCoCo real — svc-principal (ticket-service)

Generado corriendo `mvn clean test` (sin `-Pcoverage`: ese perfil ya no existe, JaCoCo está
siempre activo desde la corrección del entregable #12 — ver `pruebas_cicd.tex`) sobre el
commit `6d7d46f`, en la máquina de desarrollo del equipo (host, sin anidar Docker — por eso
`TicketRepositoryIntegrationTest`, con Testcontainers, sí corrió aquí; ver limitación conocida
en `docs/experimentos/evaluacion_iso25010.md` sección 7 para el caso de Docker anidado). 156
pruebas ejecutadas, todas verdes (1 omitida a propósito: `TicketServiceProviderPactTest`,
`@EnabledIfSystemProperty`).

Abrir `index.html` en este directorio para el reporte navegable completo (por paquete y por
clase). `../jacoco-svc-principal-raw.exec` es el archivo binario crudo de cobertura
(`jacoco.exec`), para quien quiera regenerar el HTML o verificar los números con otra
herramienta.

## Resumen (total del proyecto, agregado por el XML de nivel `<report>`)

| Métrica | Cobertura |
|---|---|
| Instrucciones | 89.1 % (3 461 de 3 886 cubiertas) |
| Ramas | 78.5 % (135 de 172 cubiertas) |
| Líneas | 96.7 % (700 de 724 cubiertas) |
| Métodos | 83.5 % (283 de 339 cubiertos) |
| Clases | 98.6 % (69 de 70 cubiertas) |

Esta es la tercera generación de este directorio. La segunda (commit `31f1ef5`, 87 pruebas,
81.1 % líneas) quedó desactualizada cuando entregables posteriores (#1, #10, #11) añadieron
código y pruebas sin regenerar esta evidencia — la brecha la encontró una revisión propia
posterior comparando estas cifras contra un `mvn clean test` fresco, no una revisión externa.
Las cifras de la Sección de Pruebas del manuscrito (`pruebas_cicd.tex`) y de
`docs/experimentos/evaluacion_iso25010.md` se recalcularon junto con esta regeneración para
que las tres fuentes coincidan.

Nota sobre el conteo por archivo (CSV) frente al agregado por archivo fuente (XML,
`<report>`): 705/729 líneas si se suman las filas del CSV, 700/724 según el XML. La diferencia
no es un error: cinco archivos fuente contienen dos clases cada uno (una externa y una interna
o lambda generada por el compilador), así que el CSV —que reporta por clase— cuenta dos veces
las líneas físicas que comparten. El XML agrega por archivo fuente y cuenta cada línea física
una sola vez; esta tabla usa el XML.

Excluye `ec/edu/uteq/soporte/telemetryservice/infrastructure/grpc/**` (código generado por
protobuf/gRPC a partir de `telemetry.proto`), configurado en `services/svc-principal/pom.xml`.
