# Etapa 1: Compilacion
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
# Reintenta hasta 3 veces con espera creciente si Maven Central no responde momentaneamente
# (mismo patron que report-service/Dockerfile, ver su comentario).
RUN for i in 1 2 3; do \
      mvn dependency:go-offline -B && exit 0; \
      echo "Intento $i de 'mvn dependency:go-offline' fallo, reintentando en $((i*10))s..."; \
      sleep $((i*10)); \
    done; \
    exit 1

COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Imagen de produccion (JRE solamente)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Agente Java de OpenTelemetry (Modulo F) -- ver el comentario completo en el
# Dockerfile de svc-principal.
RUN wget -q -O /app/otel-agent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

USER appuser

COPY --from=builder /app/target/auth-service.jar app.jar

EXPOSE 8001

ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none
ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8001/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java -javaagent:/app/otel-agent.jar $JAVA_OPTS -jar app.jar"]
