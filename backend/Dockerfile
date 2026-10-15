# Imagen de produccion del backend (CAL-17).
#
# Bases fijadas por version y digest: el mismo Dockerfile produce las mismas
# bases siempre. Para actualizarlas, cambia version y digest juntos y vuelve
# a escanear (ver docs/cal-17-imagenes-produccion.md).
ARG JDK_IMAGE=eclipse-temurin:21.0.12.1_1-jdk-noble@sha256:b468c3fc688b14450571494f588bd939378e7fd542ed5a73f8efc13f17872a87
ARG JRE_IMAGE=eclipse-temurin:21.0.12.1_1-jre-noble@sha256:000fd431958bc81a24abe1e8e5f0f0fd3ae365a594bd50aadb20696805f9408c

# --- Compilacion: Maven Wrapper con la version fijada en .mvn/wrapper -------
FROM ${JDK_IMAGE} AS builder
WORKDIR /build

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
# Capa de dependencias: solo se vuelve a descargar si cambia el pom.
RUN chmod +x ./mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -Dmaven.test.skip=true && \
    cp target/backend-*.jar app.jar && \
    java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# --- Ejecucion: solo la JRE y el JAR por capas ------------------------------
FROM ${JRE_IMAGE} AS runner

RUN groupadd --system --gid 10001 appgroup && \
    useradd --system --uid 10001 --gid appgroup --no-create-home --shell /usr/sbin/nologin appuser

WORKDIR /app
# Una capa por grupo de Spring Boot, de la que menos cambia a la que mas: un
# cambio de codigo solo reemplaza la ultima. Los archivos son de root y de
# solo lectura para el proceso; la unica ruta escribible que usa es /tmp.
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

# La imagen arranca en prod: si faltan los secretos, el backend se niega a
# arrancar (CAL-15). Compose la sobrescribe para desarrollo.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

# Metadatos al final: un VCS_REF nuevo no invalida la cache de las capas.
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="callejon9-backend" \
      org.opencontainers.image.source="https://github.com/LudwinGarcia1/callejon9-saas" \
      org.opencontainers.image.revision="${VCS_REF}"

USER 10001:10001
EXPOSE 8080

# curl ya viene en la imagen base de Temurin.
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=3 \
  CMD ["curl", "-fsS", "-o", "/dev/null", "http://127.0.0.1:8080/actuator/health"]

# Forma exec, sin shell: java es PID 1 y recibe SIGTERM directamente, y
# Spring Boot cierra con apagado ordenado.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
