# Build stage
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B || true

COPY src src
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-jammy AS runner
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 appgroup && \
    useradd --system --uid 10001 --gid appgroup appuser

COPY --from=builder /build/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
