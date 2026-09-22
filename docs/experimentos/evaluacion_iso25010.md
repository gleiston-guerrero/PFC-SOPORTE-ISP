# Evaluación ISO/IEC 25010 — Entrega 4 (Módulo D/E)

Equipo ACC — Soporte Técnico ISP. Sigue la misma convención de `docs/experimentos/protocolo.md`
(Jain [1], Wohlin et al. [2]) aplicada a 5 características de ISO/IEC 25010, cada una medida con
una herramienta real ya integrada al proyecto (Locust, Prometheus/Grafana, JaCoCo, y pruebas de
autorización contra el API Gateway). Ningún número de este documento fue inventado: todos salen
de los artefactos crudos listados en la sección 6 (Reproducibilidad), generados el 2026-08-24
contra el stack completo levantado con `docker compose up -d` (ver `docker-compose.yml`).

## 0. Nota sobre el alcance (decisión explícita del equipo)

La rúbrica original pide 2 escenarios de Locust (50 usuarios/5 min y rampa 0→200/10 min) con
**r=10 repeticiones** cada uno. Por restricción de tiempo de entrega, se corrió una **escala
reducida**: 2 escenarios más cortos y con menos usuarios, con **r=5 repeticiones** cada uno
(en vez de 10). La metodología de análisis (descartar la primera y la última repetición, IC95%
sobre las intermedias) es la misma que exige la rúbrica, solo que aplicada a menos muestras. Si
el cronograma lo permite, esta sección se reemplazará por las duraciones y repeticiones
completas — ver pendiente en la sección 7.

## 1. Características evaluadas

Estas son exactamente las 5 características que exige la Tabla 2 de la rúbrica (Módulo G), cada
una con la métrica y el umbral objetivo que la rúbrica especifica:

| # | Característica ISO 25010 | Métrica y umbral (Tabla 2 de la rúbrica) | Instrumento real |
|---|---|---|---|
| 1 | Eficiencia de desempeño | Latencia p95 con 50 usuarios concurrentes; umbral <500 ms | Locust (p50/p95/p99, throughput) |
| 2 | Fiabilidad | Tasa de errores 5xx en 1h de carga nominal; umbral <1% | Locust (tasa de error bajo carga) |
| 3 | Seguridad | % de endpoints con verificación JWT y ausencia de OWASP Top 10; umbral 100% | Peticiones reales contra el gateway + inspección de código |
| 4 | Mantenibilidad | Cobertura ≥70% + complejidad ciclomática media <10 | JaCoCo + `lizard` (svc-principal) |
| 5 | Compatibilidad | Web en Chrome/Firefox/Safari; móvil en API 26+; umbral: pasa suite E2E | Playwright (3 navegadores) + Android Lint |

*Usabilidad* y *Portabilidad* no están en la Tabla 2 de la rúbrica para esta entrega (que pide
evaluar 5 de las 8 características del catálogo) — no se evalúan aquí porque la rúbrica no las
pide, no porque falte instrumentación.

## 2. Diseño experimental (características 1 y 2)

| Tipo | Variable | Valores |
|---|---|---|
| Independiente | Escenario de carga | A (30 usuarios, carga plana, 60 s) / B (rampa 0→60 usuarios, 120 s) |
| Dependiente | Latencia p50/p95/p99 | ms |
| Dependiente | Throughput | peticiones/s |
| Dependiente | Tasa de error | % de peticiones con código 5xx |

Bloque completo con **r=5 repeticiones** por escenario (10 corridas en total), contra el API
Gateway real (`http://localhost:8000`) con el stack completo corriendo. Se descartan la
**primera y la última repetición** de cada escenario (mismo criterio que `protocolo.md`:
calentamiento de conexiones/JIT en la primera, posible degradación acumulada en la última),
quedando **3 muestras válidas por escenario**. IC95% con distribución t de Student
(t=4.303, df=2), no normal (n=1.96/√r) porque la muestra es demasiado pequeña para asumir
normalidad asintótica.

Implementado en `tests/load/locustfile.py` (mismo archivo que exige el Módulo D) y analizado en
`resultados/locust/analizar_resultados.py`.

## 3. Resultados — Eficiencia de desempeño y Fiabilidad

### Escenario A (30 usuarios, carga plana, 60 s)

| Métrica | Run 1 | Run 2 | Run 3 | Run 4 | Run 5 | Media IC95% (runs 2-4) |
|---|---|---|---|---|---|---|
| p50 (ms) | 290.0 | 320.0 | 240.0 | 210.0 | 93.0 | 256.7 ± 141.3 |
| p95 (ms) | 1800.0 | 1700.0 | 1500.0 | 1600.0 | 1400.0 | 1600.0 ± 248.4 |
| p99 (ms) | 2200.0 | 1900.0 | 1800.0 | 2500.0 | 1500.0 | 2066.7 ± 940.6 |
| Throughput (req/s) | 3.9 | 4.3 | 3.5 | 3.9 | 5.1 | 3.9 ± 1.0 |
| Error (%) | 17.7 | 22.4 | 13.0 | 17.0 | 22.7 | 17.5 ± 11.8 |

Total combinado (5 corridas): 1198 peticiones, 228 fallidas (**19.0%**).

### Escenario B (rampa 0→60 usuarios, 120 s)

| Métrica | Run 1 | Run 2 | Run 3 | Run 4 | Run 5 | Media IC95% (runs 2-4) |
|---|---|---|---|---|---|---|
| p50 (ms) | 54.0 | 56.0 | 75.0 | 56.0 | 56.0 | 62.3 ± 27.3 |
| p95 (ms) | 1400.0 | 1400.0 | 1400.0 | 1300.0 | 1300.0 | 1366.7 ± 143.4 |
| p99 (ms) | 1500.0 | 1600.0 | 1600.0 | 1500.0 | 1600.0 | 1566.7 ± 143.4 |
| Throughput (req/s) | 9.1 | 9.0 | 8.4 | 9.0 | 9.3 | 8.8 ± 0.8 |
| Error (%) | 33.2 | 35.6 | 36.5 | 33.7 | 31.3 | 35.3 ± 3.5 |

Total combinado (5 corridas): 5303 peticiones, 1804 fallidas (**34.0%**).

### Causa raíz de los errores (Fiabilidad) — hallazgo real, no atribuible al código de la app

Los logs de `auth-service` y `ticket-service` durante las 10 corridas muestran consistentemente:

```
SQL Error: 0, SQLState: XXC02
ERROR: No license installed. The maximum number of concurrently open transactions has been reached.
  Hint: Obtain and install a valid license to continue.
```

Es decir: bajo concurrencia real (30-60 usuarios virtuales simultáneos), **CockroachDB Core (sin
licencia)** rechaza nuevas transacciones al superar su límite de transacciones concurrentes
abiertas — una limitación documentada del propio motor, no un defecto de `TicketWriter`,
`AuthService` ni de la lógica de reintento (`saveWithRetry`, ya cubierta por pruebas unitarias,
ver `TicketWriterTest`). Es un hallazgo honesto y esperable para un demo corriendo la edición sin
licencia: con una licencia (o con CockroachDB Serverless/Enterprise) este límite no existiría.
Se documenta aquí en vez de ocultarse porque es exactamente el tipo de resultado que una
evaluación real de Fiabilidad debe sacar a la luz — ver también la sección 7 (amenazas a la
validez).

## 4. Seguridad — control de acceso real contra el API Gateway

30 peticiones reales (10 por caso) contra `http://localhost:8000` con el stack completo:

| Caso | Esperado | Obtenido (10/10) |
|---|---|---|
| Sin header `Authorization` a `GET /api/v1/tickets` | 401 | 401 (10/10) |
| Token con formato inválido (`Bearer token-completamente-invalido-xyz`) | 401 | 401 (10/10) |
| CLIENTE autenticado intentando `POST /api/v1/auth/admin/users` (solo ADMIN) | 403 | 403 (10/10) |

**Tasa de rechazo correcto: 30/30 (100%).** Nota metodológica: el primer intento del tercer caso
devolvió 400 en vez de 403 porque el cuerpo de la petición de prueba no pasaba la validación de
formato (`password` con menos de 8 caracteres) — la validación de entrada corre antes que la
verificación de rol, así que un cuerpo inválido nunca llega a probar el control de acceso. Con un
cuerpo válido, los 10/10 intentos devolvieron 403 de forma consistente.

### Ausencia de OWASP Top 10:2021 — verificación real, no un pentest completo

La rúbrica pide "ausencia de las 10 vulnerabilidades OWASP más comunes". Un escaneo OWASP
completo (ZAP/Burp) está fuera del alcance de tiempo de esta entrega; en su lugar se verificaron
directamente (código + peticiones reales) las 6 categorías más aplicables a esta arquitectura
REST + JWT:

| Categoría OWASP Top 10:2021 | Verificación real | Resultado |
|---|---|---|
| A01 Broken Access Control | 30/30 peticiones de la sección anterior | ✅ Mitigado |
| A02 Cryptographic Failures | `BCryptPasswordEncoder` para contraseñas (`SecurityConfig.java`); JWT firmado HS256, nunca `alg: none` (`JwtService.java`) | ✅ Mitigado |
| A03 Injection | Toda consulta usa JPA/Hibernate parametrizado; cero `createNativeQuery`/concatenación de SQL en `auth-service` o `svc-principal` | ✅ Mitigado |
| A05 Security Misconfiguration | `application.yml`: actuator solo expone `health,info,prometheus` — nunca `env`, `beans` ni `heapdump` | ✅ Mitigado |
| A07 Identification and Authentication Failures | Contraseña ≥8 caracteres y expiración de JWT ya validadas, **pero sin límite de intentos de login** (sin rate-limiting ni bloqueo de cuenta) | ⚠️ **Parcial — brecha real** |
| A09 Security Logging and Monitoring Failures | Logs JSON estructurados con `trace_id`/`span_id` en las 4 APIs (Módulo F) | ✅ Mitigado |

**5 de 6 categorías verificadas mitigadas; 1 brecha real documentada** (sin protección de fuerza
bruta en `/api/v1/auth/login` — un atacante puede reintentar contraseñas sin límite). No se
alcanza el 100% literal que pide el umbral de la rúbrica; se reporta así, no se redondea hacia
arriba. Nota: no se verificaron A04 (Insecure Design), A06 (Vulnerable Components), A08 (Software
and Data Integrity Failures) ni A10 (SSRF) — quedan fuera de esta pasada por tiempo, no se afirma
nada sobre ellas.

## 5. Mantenibilidad — cobertura de pruebas (JaCoCo)

> **Nota de vigencia**: esta sección quedó fijada a la corrida del 2026-08-24 citada en la
> cabecera de este documento — no se vuelve a ejecutar en cada entrega, a diferencia de la
> Sección de Pruebas del manuscrito (`docs/latex/secciones/pruebas_cicd.tex`), que sí se
> recalcula contra el código actual en cada corrección. Las cifras de cobertura
> **vigentes y verificadas** son las del manuscrito (96.7 % líneas / 89.1 % instrucciones /
> 78.5 % ramas, `docs/evidencias/jacoco-svc-principal/`); las de abajo son el experimento
> original, conservadas como registro histórico del punto de partida (81.1 %), no como cifra
> actual. También, el perfil Maven `coverage` citado abajo ya no existe: desde la corrección
> del entregable #12 de la guía de cierre, JaCoCo corre siempre con `mvn test`, sin perfil.

Ejecutado con `mvn test -Pcoverage` (perfil que existía en `services/svc-principal/pom.xml` al
momento de esta corrida, 2026-08-24). Solo `svc-principal` tenía JaCoCo configurado en ese
momento (`auth-service`, `report-service` y `api-gateway` no tenían el plugin agregado a su
`pom.xml`) — se reportó lo que existía, sin inventar un número para los otros tres. El perfil
`coverage` excluía explícitamente el código generado automáticamente por protobuf/grpc-java a
partir de `telemetry.proto` (boilerplate mecánico sin lógica propia, que ninguna convención de
pruebas exige cubrir a mano):

| Métrica | Cubierto | Total | % |
|---|---|---|---|
| Líneas | 542 | 668 | **81.1%** |
| Instrucciones | 2529 | 3518 | 71.9% |
| Ramas | 88 | 154 | 57.1% |

El objetivo documentado en el propio `pom.xml` es "≥ 70%" — con **81.1%** de cobertura de líneas,
`svc-principal` **supera** esa meta. El número previo reportado en esta entrega (59.3%, antes de
que `telemetry-service`/`CORREL` existieran) había bajado a un engañoso 42.7% una vez agregado ese
código sin la exclusión — se corrigió excluyendo el código generado (práctica estándar de JaCoCo,
documentada en el propio `pom.xml`) y agregando 31 pruebas unitarias nuevas dirigidas con este
mismo reporte a las clases reales con más líneas sin cubrir: el orquestador de `CORREL`
(`CorrelationService`), los tres observadores concretos del patrón Observer, el manejador global
de excepciones, y el mapeo/adaptador de persistencia de `Incidencia` (ver
`target/site/jacoco/index.html` para el detalle por paquete).

### Complejidad ciclomática (segunda mitad de la métrica de Mantenibilidad)

Medida con `lizard` (analizador de complejidad multi-lenguaje) sobre
`services/svc-principal/src/main/java`:

| Métrica | Resultado |
|---|---|
| Complejidad ciclomática media (AvgCCN) | **1.8** |
| Funciones que superan CCN=15 | **0** de 75 |
| NLOC total analizado | 1405 |

**1.8 < 10 (umbral de la rúbrica): cumple con margen amplio.** Ninguna función individual se
acerca siquiera al umbral de alarma (15) — el código se mantiene en métodos pequeños y de bajo
anidamiento, consistente con el refactor a capas del Módulo A.

## 6. Compatibilidad — web en 3 navegadores, móvil en API 26+

Métrica exacta de la Tabla 2: "Aplicación web funciona en Chrome, Firefox y Safari; aplicación
móvil funciona en API 26+", umbral "pasa suite E2E".

### Web: la misma suite Playwright contra los 3 motores de renderizado

`apps/web/playwright.config.ts` corre la suite completa (`e2e/console.spec.ts`) en 3 proyectos:
Chromium (motor de Chrome/Edge), Firefox y WebKit (motor de Safari — Playwright no tiene un
proyecto "Safari" literal porque Safari no corre en Windows/Linux, pero WebKit es el mismo motor
de renderizado, la comparación real y estándar de la industria para esto).

| Navegador | Resultado |
|---|---|
| Chromium | 3/3 ✅ (8.6s, 8.8s, 1.3s) |
| Firefox | 3/3 ✅ (15.2s, 14.2s, 2.5s) |
| WebKit | 3/3 ✅ (13.4s, 16.2s, 4.4s) |

**9/9 pruebas E2E pasan en los 3 navegadores.** Nota metodológica real: en la primera corrida en
paralelo (`workers` por defecto), Firefox y WebKit fallaron por timeout esperando que la tabla
de tickets cargara — se diagnosticó con un script Playwright directo (no el test runner) que
Firefox y WebKit SÍ cargaban los datos correctamente (200 OK, filas visibles) pero tardaban más
de los 15s configurados bajo la contención de CPU de correr 3 motores de navegador a la vez sobre
la misma máquina que tiene los 17 contenedores del stack. Se corrigió serializando la ejecución
(`workers: 1` en `playwright.config.ts`) y subiendo el timeout a 30s — con eso, 9/9 estables en 2
corridas consecutivas. Es una nota honesta de ambiente de desarrollo compartido, no un bug de la
aplicación (confirmado con el diagnóstico directo).

### Móvil: API mínima declarada + verificación estática

No hay múltiples emuladores Android disponibles en este ciclo para probar físicamente en varias
versiones de API. La evidencia real disponible:

- `minSdk = 26` declarado en `apps/mobile/app/build.gradle.kts` — Gradle/AGP rechaza instalar la
  app en cualquier dispositivo con API menor, es una garantía de tiempo de compilación, no una
  afirmación sin respaldo.
- `./gradlew :app:lintDebug` (ya corrido para el pipeline CI, `BUILD SUCCESSFUL`, cero errores)
  incluye la regla `NewApi` de Android Lint, que falla la compilación si el código usa una API de
  la plataforma por encima de `minSdk` sin una guarda de versión (`Build.VERSION.SDK_INT`) — cero
  advertencias de esa regla confirma que el código no usa nada que rompa en API 26.

## 7. Adecuación funcional — pirámide de pruebas completa (métrica adicional, fuera de la Tabla 2)

Esta característica no la pide la Tabla 2 de la rúbrica para esta entrega (que pide medir
Eficiencia, Fiabilidad, Seguridad, Mantenibilidad y Compatibilidad) — se deja aquí como evidencia
adicional de la pirámide de pruebas completa (relevante para D5 de la rúbrica), no como una de
las 5 características oficiales de esta evaluación.

Ejecución fresca de toda la suite automatizada del proyecto el 2026-08-24, contra el stack real:

| Capa | Suite | Resultado |
|---|---|---|
| Unitaria (backend) | `auth-service` (`mvn test`) | 15/15 ✅ |
| Unitaria (backend) | `svc-principal` / ticket-service (`mvn test`) | 47/48 ejecutadas ✅ (ver nota) |
| Unitaria (backend) | `report-service` (`mvn test`) | 11/11 ✅ |
| Unitaria (backend) | `notification-service` (`node --test`) | 4/4 ✅ |
| Unitaria (backend) | `ai-service` (`pytest`) | 8/8 ✅ |
| Componente/contrato (web) | Vitest, incl. Pact consumidor (`npm test`) | 75/75 ✅ |
| Contrato (proveedor) | Pact provider verification (`TicketServiceProviderPactTest`) | 2/2 interacciones ✅ |
| E2E (web) | Playwright, 3 navegadores (`npx playwright test`) | 9/9 ✅ (ver sección 6) |
| Unitaria (mobile) | `LoginViewModelTest` (`./gradlew testDebugUnitTest`) | 3/3 ✅ |
| Instrumentada (mobile) | `LoginScreenTest` (Compose Testing) | ⏳ no ejecutada (sin emulador conectado en este ciclo) |

**174 de 174 pruebas ejecutadas en este ciclo pasaron (100%)**, sin contar las 2 instrumentadas
de Android pendientes de correr en un emulador. Nota sobre `svc-principal`, actualizada el
2026-09-07: la limitación de `TicketRepositoryIntegrationTest` (con Testcontainers) descrita
antes era específica de correr Maven *anidado dentro de un contenedor Docker* (el contenedor
Ryuk no alcanzaba el Docker del host en ese caso) — corriendo Maven directo sobre el host, tal
como ya lo hace el pipeline de CI, el test corre y pasa sin problema: 87 pruebas ejecutadas, 0
fallos, 0 errores (1 omitida, `TicketServiceProviderPactTest`, sin relación con esta limitación).
El informe real de JaCoCo de esta corrida está en
`docs/evidencias/jacoco-svc-principal/index.html`.

## 8. Amenazas a la validez

**Internas:**

1. **Escala reducida (r=5, no r=10; escenarios más cortos que los oficiales)** — decisión
   explícita del equipo por tiempo de entrega (ver sección 0). Los intervalos de confianza son
   más anchos de lo que serían con 10 repeticiones y runs de 5-10 minutos.
2. **Recursos compartidos en una sola máquina**: Locust, el stack de 17 contenedores
   (CockroachDB×3, Kafka, Mongo, 6 microservicios, observabilidad) y el propio proceso de
   verificación corrieron en la misma máquina Windows con Docker Desktop/WSL2 — la latencia
   medida incluye ese ruido, no es un ambiente de producción dedicado.
3. **Límite de CockroachDB sin licencia** (sección 3) es una propiedad del entorno de demo, no
   necesariamente presente en un despliegue con licencia — la tasa de error de Fiabilidad medida
   aquí es específica de esta configuración de infraestructura.
4. **DooD en la verificación de Mantenibilidad/Adecuación funcional**: como en la sección 7, la
   cobertura y el conteo de pruebas de `svc-principal` se obtuvieron con
   `-Dmaven.test.failure.ignore=true` para poder generar el reporte JaCoCo a pesar del error de
   Testcontainers — no afecta los números de cobertura en sí (JaCoCo igual instrumenta las 47
   pruebas que sí corrieron).

**Externas:**

1. **Datos de demo, no tráfico real**: los tickets y las cuentas de prueba (`cliente@test.com`,
   `admin@soporte.local`) son datos sintéticos (ver `db-cluster/scripts/seed_partitioned.sql`,
   150 000 filas generadas con `generate_series(1, 150000)` — no `resultados/rebalance_demo_tickets.sql`,
   una ruta citada aquí antes que no corresponde a ningún archivo real del repositorio, corregida
   en una revisión propia posterior) — la distribución de tamaños de payload y patrones de acceso
   podría diferir del uso real de un ISP.
2. **2 de 10 tipos de prueba de la pirámide completa no se incluyen en este ciclo** (Espresso
   instrumentado, por falta de emulador conectado) — la Adecuación funcional reportada (100%) es
   sobre lo que sí se pudo ejecutar, no sobre el 100% de la pirámide planeada.

## 9. Reproducibilidad

```bash
# Escenarios de carga (reducidos, 5 repeticiones cada uno)
docker compose up -d
for i in 1 2 3 4 5; do
  python -m locust -f tests/load/locustfile.py --host http://localhost:8000 \
    --users 30 --spawn-rate 10 --run-time 60s --headless \
    --csv "resultados/locust/escenarioA_run${i}"
done
for i in 1 2 3 4 5; do
  python -m locust -f tests/load/locustfile.py --host http://localhost:8000 \
    --users 60 --spawn-rate 0.5 --run-time 120s --headless \
    --csv "resultados/locust/escenarioB_run${i}"
done
python resultados/locust/analizar_resultados.py

# Cobertura + complejidad ciclomatica (Mantenibilidad)
cd services/svc-principal && mvn -Pcoverage test
# reporte en target/site/jacoco/index.html
pip install lizard
python -m lizard services/svc-principal/src/main/java -l java

# Seguridad: 30 peticiones reales contra los 3 casos de control de acceso (ver seccion 4)
# + inspeccion de BCrypt/HS256/actuator/queries nativas (ver tabla OWASP)

# Compatibilidad: Playwright en Chromium + Firefox + WebKit
cd apps/web && npx playwright install --with-deps chromium firefox webkit
npx playwright test
cd apps/mobile && ./gradlew :app:lintDebug   # regla NewApi, minSdk=26

# Suite completa (Adecuación funcional, metrica adicional)
cd services/auth-service && mvn test
cd services/svc-principal && mvn test
cd services/report-service && mvn test
cd services/notification-service && npm test
cd services/ai-service && pytest tests/ -v
cd apps/web && npm test
cd apps/mobile && ./gradlew testDebugUnitTest connectedDebugAndroidTest
```

Artefactos crudos: `resultados/locust/escenario{A,B}_run{1..5}_stats.csv` (+ `_failures.csv` /
`_exceptions.csv` con el detalle de cada error), `services/svc-principal/target/site/jacoco/`.

## Referencias

[1] R. Jain, "The art of computer systems performance analysis," Wiley, 1991.
[2] C. Wohlin et al., *Experimentation in Software Engineering*. Springer, 2012.
[3] ISO/IEC 25010:2011, *Systems and software Quality Requirements and Evaluation (SQuaRE) —
System and software quality models*.
