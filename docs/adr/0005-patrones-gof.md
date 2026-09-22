# ADR-0005: Patrones GoF del refactor a arquitectura hexagonal (ticket-service)

## Estado
Aceptado — Entrega 4 (Agosto 2026)

## Contexto
El Módulo A de la guía de Entrega 4 exige refactorizar el backend en 4 capas
(presentación/aplicación/dominio/infraestructura) e implementar al menos 5 patrones GoF
**reales**, no cosméticos: cada patrón debe resolver un problema de diseño que existía de
verdad en el código de la Entrega 3, no agregarse solo para completar el conteo. El texto
general del Módulo A exige incluir como mínimo Repository, Factory Method y Strategy; la
Tabla 1 de la misma guía (aplicación concreta por equipo) asigna a ACC/Soporte-ISP el conjunto
*Command, Chain of Responsibility, Observer, Repository, Strategy*. Para satisfacer ambas
instrucciones sin contradecirlas, se implementan **6 patrones** — la unión de los dos
conjuntos — en vez de elegir uno de los dos textos.

Antes de este refactor, `ticket-service` tenía una única clase `TicketService` que mezclaba:
acceso a datos vía `TicketRepository extends JpaRepository` (interfaz de Spring Data
inyectada directamente, sin puerto de dominio), la lógica de creación de tickets en línea, dos
copias distintas de "cuánto SLA le corresponde a este ticket" (una en `TicketService`, otra en
`TicketClassificationListener`), y los tres casos de uso mutables (crear, cambiar estado,
asignar técnico) como métodos con nombre en esa misma clase, sin forma de interceptarlos de
manera uniforme. El estado `ESCALADO` existía en el enum `TicketStatus` desde la E3 pero
ningún camino del código lo alcanzaba: no había ninguna regla de escalado implementada, ni
ninguna forma de reaccionar a que un ticket se escalara.

## Decisión
Se adoptan 6 patrones GoF, cada uno mapeado a un problema concreto que existía antes del
refactor (los 5 primeros son extracciones de lógica preexistente; Observer es
funcionalidad nueva, igual que Chain of Responsibility):

### 1. Repository
**Problema que resolvía:** el dominio dependía directamente de `JpaRepository` (Spring Data),
acoplando la lógica de negocio a JPA/Hibernate.
**Implementación:** `domain/TicketRepository.java` es ahora un puerto puro (sin imports de
JPA) con los métodos de consulta/escritura que el dominio necesita.
`infrastructure/persistence/TicketRepositoryAdapter.java` lo implementa delegando en
`SpringDataTicketRepository` (interfaz Spring Data, ahora *package-private*, invisible fuera de
`infrastructure/persistence`) y traduce entre `Ticket` (dominio) y `TicketJpaEntity`
(infraestructura) vía `TicketMapper`. Es el único punto del sistema donde ambos mundos se
tocan.

### 2. Factory Method
**Problema que resolvía:** crear un `Ticket` nuevo (id, estado inicial `NUEVO`, SLA por
defecto) se hacía en línea con `Ticket.builder()` directamente dentro de
`TicketService.createTicket()` — cualquier invariante de creación quedaba implícita y solo
reutilizable copiando código.
**Implementación:** `domain/factory/TicketFactory.crearNuevo(zone, clientId, description)`
centraliza esa construcción como una operación con nombre, reutilizable desde cualquier otro
punto de entrada futuro (alta administrativa, importación masiva) sin duplicar las reglas.

### 3. Strategy
**Problema que resolvía:** el cálculo del plazo de SLA vivía hardcodeado en **dos** lugares
distintos con la misma responsabilidad: 24h fijas en `TicketService.createTicket()`, y un mapa
prioridad→horas hardcodeado por separado en `TicketClassificationListener`.
**Implementación:** `domain/policy/SlaPolicy` define `slaFor(Priority)`; `DefaultSlaPolicy`
(24h planas, usada al crear el ticket, cuando aún no hay prioridad real) y
`ClassifiedSlaPolicy` (4h/12h/24h/48h según prioridad, usada cuando `ai-service` clasifica el
ticket) son intercambiables detrás de la misma interfaz. Agregar una tercera política (p. ej.
SLA distinto por zona) no exige tocar el código que ya las usa.

### 4. Command
**Problema que resolvía:** `TicketController` llamaba directamente a métodos con nombre
(`ticketService.createTicket(...)`, `.updateStatus(...)`, `.assignTechnician(...)`) todos
implementados en una única clase, sin forma uniforme de interceptar, loguear o reintentar esas
operaciones.
**Implementación:** cada acción mutable es un objeto de comando inmutable
(`CreateTicketCommand`, `UpdateTicketStatusCommand`, `AssignTechnicianCommand`) ejecutado por un
manejador dedicado que implementa `TicketCommandHandler<C, R>`
(`CreateTicketHandler`, `UpdateTicketStatusHandler`, `AssignTechnicianHandler`).
`presentation/TicketController` arma el comando a partir del request HTTP y se lo entrega al
manejador correspondiente — queda desacoplado de cómo se ejecuta la acción.

### 5. Chain of Responsibility
**Problema que resolvía:** el estado `ESCALADO` no era alcanzable por ningún camino del código
— no existía ninguna regla de escalado. Esta es funcionalidad **nueva**, no una extracción de
lógica preexistente.
**Implementación:** `domain/escalation/EscalationHandler` es el eslabón abstracto
(`evaluate()` + delegación a `next`); `SlaBreachedEscalationHandler` (SLA vencido sin
resolución) y `StaleCriticalEscalationHandler` (ticket `CRITICO` sin asignar por más de 2h,
aunque el SLA formal no haya vencido) son los dos eslabones concretos, ensamblados por orden
(`@Order`) en `domain/escalation/EscalationChain`. Un nuevo
`infrastructure/scheduling/EscalationScheduler` (`@Scheduled(fixedDelay = 5 min)`) recorre los
tickets activos y aplica la cadena — agregar un tercer criterio de escalado no requiere tocar
los eslabones existentes ni el scheduler.

### 6. Observer
**Problema que resolvía:** hasta el paso anterior, "reaccionar a que un ticket se escale" era
una única llamada a un logger en línea dentro de `EscalationScheduler` — agregar una segunda
reacción (una métrica, un evento de integración) exigía editar esa misma clase, mezclando "cuándo
escalar" (ya resuelto por Chain of Responsibility) con "qué hacer cuando se escala".
**Implementación:** `domain/escalation/EscalationObserver.java` define
`onTicketEscalated(Ticket, String motivo)`. `EscalationScheduler` es ahora el sujeto: guarda el
ticket como `ESCALADO` y notifica a la lista de observadores inyectada por Spring (`List<EscalationObserver>`,
resuelta automáticamente con todos los beans que implementan la interfaz), sin conocer sus
implementaciones concretas ni verse afectado si uno de ellos falla (`EscalationSchedulerTest`
cubre explícitamente ese caso). Tres observadores concretos, cada uno en su capa de
infraestructura correspondiente:
- `EscalationLoggingObserver` — el logging que antes vivía en línea.
- `EscalationMetricsObserver` — incrementa `app_business_events_total{event="ticket_escalated"}`,
  una de las cuatro métricas nuevas que exige el Módulo F (D6, observabilidad); se adelanta aquí
  porque el escalado es el primer evento de negocio real que el sistema dispara solo.
- `EscalationEventPublisherObserver` — publica `ticket.escalated` en Kafka (mismo puerto
  `EventPublisher` de la Saga por coreografía, ADR-0004), para que `notification-service` avise a
  coordinadores/técnicos. Antes de la E4 este evento nunca existía porque nada llegaba a
  `ESCALADO`.

Agregar una cuarta reacción al escalado en el futuro es agregar un `@Component` nuevo — no
requiere tocar `EscalationScheduler` ni ninguno de los observadores existentes.

## Consecuencias

**Positivas:**
- Cada patrón reemplaza una duplicación o un acoplamiento real que existía en la E3 (verificable
  contra el historial de `TicketService.java` previo al refactor), no una envoltura artificial.
- El dominio (`domain/`) queda libre de anotaciones JPA/Spring — se puede probar con JUnit puro
  sin levantar contexto de Spring (ver `TicketFactoryTest`, `SlaPolicyTest`,
  `EscalationChainTest`).
- La funcionalidad de escalado, ausente desde la E3, ahora es real y probada (4 casos de prueba
  en `EscalationChainTest`, incluyendo que un ticket ya resuelto no se re-escala) y observable
  (3 casos en `EscalationSchedulerTest`, incluyendo que un observador que falla no bloquea a los
  demás ni pierde el escalado ya persistido).
- Los tests de la suite (unitarios + integración real contra CockroachDB vía Testcontainers)
  pasan sobre la nueva estructura de 4 capas — 45 al momento de este ADR (Agosto 2026); la
  cifra vigente y verificada es la de la Sección de Pruebas del manuscrito
  (`docs/latex/secciones/pruebas_cicd.tex`, 156 en `svc-principal` a esta entrega), que sí se
  recalcula en cada corrección — este ADR es un registro de la decisión de diseño en su
  momento, no un dato que se actualice junto con las rondas de pruebas posteriores.
- El observador de métricas (`app_business_events_total`) adelanta parte del trabajo del Módulo F
  (D6, observabilidad) que de otro modo se haría desde cero en esa etapa.

**Negativas / riesgos:**
- Más clases e indirección que la versión de la E3 (15 clases nuevas solo para Command +
  Strategy + Chain of Responsibility + Observer) — el equipo debe poder justificar en la defensa
  oral por qué cada una existe, no solo que existe.
- `EscalationScheduler` introduce un `@Scheduled` nuevo: un despliegue con múltiples réplicas de
  `ticket-service` ejecutaría la evaluación de escalado en paralelo en cada instancia. No es un
  problema de corrección (evaluar dos veces el mismo ticket es idempotente: el motivo de
  escalado se recalcula, no se acumula) pero sí de eficiencia si el número de réplicas crece;
  queda documentado como limitación conocida, no resuelto en esta entrega.
  
- Ver [ADR-0009](0009-nota-trazabilidad-commit-a74791c.md) para el desglose exacto de líneas/archivos del commit `a74791c` que introdujo este refactor, y su separación respecto de una limpieza accidental no relacionada con el diseño.
