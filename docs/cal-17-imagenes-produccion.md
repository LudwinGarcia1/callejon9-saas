# CAL-17 — Imágenes de producción mínimas y no root

Las imágenes del backend y del frontend se construyen desde cero con bases fijadas, corren sin root sobre un sistema de archivos que el proceso no puede modificar y se escanearon con Trivy. Este documento registra las decisiones, cómo verificarlas y el resultado del escaneo, con sus excepciones.

## Decisiones

| Decisión | Motivo |
|---|---|
| Bases fijadas por versión **y** digest: `eclipse-temurin:21.0.12.1_1-jdk-noble` / `-jre-noble` y `node:24.21.0-alpine3.24` | La etiqueta sola puede cambiar de contenido; con el digest, el mismo Dockerfile produce siempre la misma base. |
| Backend sobre Ubuntu 24.04 (noble) en vez de 22.04 (jammy) | En el escaneo, la JRE noble no tuvo HIGH ni CRITICAL; la jammy tuvo una HIGH. |
| Frontend sobre Alpine en vez de Debian slim | `node:24.21.0-slim` (Debian 12) trae 4 CRITICAL del sistema (perl-base, zlib); la variante trixie, 43 HIGH. Alpine no tiene ninguna del sistema. |
| Next en modo `output: "standalone"`, activado solo por el Dockerfile (`NEXT_OUTPUT=standalone`) | La imagen lleva `server.js` con solo las dependencias que usa, no el `node_modules` completo con pnpm y herramientas de compilación. En local queda apagado porque en Windows, sin permiso para crear enlaces simbólicos, `next build` falla al copiar los enlaces de pnpm a esa salida. |
| El destino del rewrite `/api/v1` se fija **al compilar** (`--build-arg BACKEND_ORIGIN`, por defecto `http://backend:8080`) | Next resuelve `rewrites()` en `next build` y guarda el destino en la salida: cambiar `BACKEND_ORIGIN` al arrancar no lo mueve. Cada entorno publica el backend con el nombre interno `backend` (Compose ya lo hace) o compila con otro valor. El rewrite sigue en `next.config.ts`, como exige `AGENTS.md`. |
| La imagen del backend arranca con `SPRING_PROFILES_ACTIVE=prod` | Sin secretos válidos, el backend se niega a arrancar (CAL-15) en vez de usar los valores de desarrollo. Compose la sobrescribe con `default`. |
| Archivos de la aplicación de `root`, proceso como UID 10001 | El proceso no puede modificar su propio código. Las únicas rutas escribibles son `/tmp` (Tomcat) y `/app/.next/cache` (Next). |
| JAR de Spring Boot extraído por capas (`-Djarmode=tools extract --layers --launcher`) | Un cambio de código reemplaza una capa de ~1,5 MB, no el JAR completo con sus 71 MB de dependencias. |
| `ENTRYPOINT`/`CMD` en forma exec, sin shell | `java` y `node` son PID 1 y reciben `SIGTERM` directamente. |
| Healthchecks sin instalar paquetes | El backend usa el `curl` que ya trae Temurin. El frontend usa `fetch` de Node; la imagen no tiene `curl`, `npm`, `npx`, `corepack` ni `yarn`. |
| `.dockerignore` excluye `.env*`, `.git`, `target/`, `node_modules`, `.next` y `src/test/` | Nada local ni secreto entra al contexto de construcción. |
| Metadatos OCI (`org.opencontainers.image.*`) al final de la etapa | `--build-arg VCS_REF=<commit>` identifica la imagen sin invalidar la caché de las capas anteriores. |

Compose aplica además `read_only: true`, `tmpfs` para las rutas escribibles, `cap_drop: [ALL]` y `no-new-privileges`. El frontend espera a que el backend esté `healthy`.

## Construir y verificar

```powershell
docker compose build --build-arg VCS_REF=$(git rev-parse --short HEAD)
docker compose up -d --wait
docker inspect --format '{{.Config.User}} {{.HostConfig.ReadonlyRootfs}}' callejon9-backend callejon9-frontend
```

Imagen del frontend para otro origen interno:

```powershell
docker build --build-arg BACKEND_ORIGIN=http://api.interno:8080 -t callejon9-frontend frontend
```

Escaneo con Trivy, sin instalarlo:

```powershell
docker run --rm -v //var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.76.0 image --scanners vuln --severity HIGH,CRITICAL callejon9-backend
```

CI construye las dos imágenes en cada push y PR (job `images` de `.github/workflows/ci.yml`). Comprueba que corren como UID 10001, que el frontend `standalone` sirve `/login` y responde `healthy`, y que el backend tiene el perfil `prod` por defecto. Es la única puerta que ejercita la salida `standalone`: el `pnpm build` del job `frontend` no la activa.

Para actualizar una base: cambia versión y digest juntos en el `ARG` del Dockerfile (`docker buildx imagetools inspect <imagen>:<versión>` da el digest), reconstruye, repite el escaneo y actualiza este documento.

## Verificación (2026-10-14)

| Comprobación | Resultado |
|---|---|
| Construcción limpia (`docker compose build --no-cache`) | Las dos imágenes se construyen con el lockfile de pnpm y el Maven Wrapper. `dependency:go-offline` ya no necesita `\|\| true`. |
| Stack completo (`docker compose up --wait`) | `db`, `backend` y `frontend` en `healthy`. |
| Prueba de humo desde el frontend, por el rewrite | Alta 201 → login 200 con `access_token` y `refresh_token` `HttpOnly` y `SameSite=Strict` → `/api/v1/auth/me` 200 → `/login` 200 con CSP. |
| Usuario y escritura | Los dos procesos corren como UID 10001. `touch /app/...` falla; `/tmp` y la caché de Next son escribibles. |
| Señales | `docker stop`: el frontend sale en 1 s con código 0; el backend, en 1 s con 143 (SIGTERM en la JVM) tras cerrar JPA e Hikari. |
| Rewrite al origen configurado | Imagen compilada con `BACKEND_ORIGIN=http://fakebackend:8080`: `/api/v1/*` llega a ese host, con cuerpo, query y las dos `Set-Cookie`. |
| Con CAL-15 combinada | La imagen sin variables se detiene en `prod` y lista `JWT_SECRET`, `DB_APP_PASSWORD`, `DB_OWNER_PASSWORD` y `APP_ALLOWED_ORIGINS`, sin conectarse a la base. Con `SPRING_PROFILES_ACTIVE=default` arranca `healthy` y advierte. |

### Defecto corregido de CAL-11

En `main` (`ac71b10`) el frontend de Compose no reenviaba `/api/v1` al backend. La imagen se compilaba sin `BACKEND_ORIGIN`, así que el rewrite quedaba fijo en `http://localhost:8080`; la variable que Compose pasaba al arrancar no lo cambiaba. Dentro del contenedor, `/api/v1/*` respondía 500. CAL-17 lo corrige pasando el origen como argumento de compilación.

## Tamaño y capas

Medido con `docker image inspect` (tamaño sin comprimir).

| Imagen | `main` (`ac71b10`) | CAL-17 |
|---|---|---|
| Backend | 677 MB | 549 MB |
| Frontend | 1119 MB | 327 MB |

Capas principales del backend:

| Capa | Tamaño |
|---|---|
| Ubuntu 24.04 (base) | 87,6 MB |
| Paquetes de la imagen Temurin (certificados, locales, `curl`) | 55,9 MB |
| JRE Temurin 21.0.12.1 | 166 MB |
| `dependencies/` de Spring Boot | 71,1 MB |
| `spring-boot-loader/` | 0,7 MB |
| `application/` (código del proyecto) | 1,5 MB |

Capas principales del frontend:

| Capa | Tamaño |
|---|---|
| Alpine 3.24.2 (base) | 9,1 MB |
| Node.js 24.21.0 | 165 MB |
| Salida `standalone` (`server.js` y dependencias trazadas) | 66,5 MB |
| `.next/static` | 2 MB |

## Escaneo de vulnerabilidades

Trivy 0.76.0, severidades HIGH y CRITICAL, sobre las imágenes finales. **Los sistemas base de las dos imágenes no tienen ninguna.** Lo que queda está en dependencias de la aplicación.

El resultado depende de la base de vulnerabilidades, no solo de la versión de Trivy. Todos los escaneos de este documento usaron la misma: publicada el 2026-10-14 a las 13:10 UTC y descargada a las 18:22 UTC. Un escaneo posterior puede mostrar CVE nuevas sin que la imagen haya cambiado. Para ver la base que usó un escaneo: `docker run --rm aquasec/trivy:0.76.0 version --format json`, campo `VulnerabilityDB.UpdatedAt`.

Dentro de CAL-17 se aplicaron dos parches de la misma línea de versiones:

* Tomcat 10.1.50 → 10.1.61 (`tomcat.version` en `backend/pom.xml`): cierra 6 CRITICAL y 7 HIGH.
* Next 15.5.22 → 15.5.28, con `eslint-config-next`: cierra 2 CRITICAL.

### Excepciones CRITICAL documentadas (backend)

| CVE | Paquete | ¿Aplica? | Corrección pública | Seguimiento |
|---|---|---|---|---|
| CVE-2026-22732 | `spring-security-web` 6.4.13 | **Sí**: con la escritura diferida de cabeceras (el valor por defecto), las cabeceras de seguridad pueden no escribirse. `SecurityHeadersTest` comprueba que hoy sí se escriben en los casos probados. | 6.5.9, que solo trae Spring Boot 3.5; 3.4.13 es la última versión pública de 3.4. | CAL-107 |
| CVE-2026-47884 | `spring-webmvc` 6.2.15 | No: requiere `XsltView`, que el proyecto no usa. | Solo 7.0.9 (Spring Boot 4). | Revisar al migrar a Spring Boot 4. |
| CVE-2026-47890 | `spring-webmvc` 6.2.15 | No: requiere SSE con fragmentos de vista, y la API no usa SSE ni vistas. | Solo 7.0.9 (Spring Boot 4). | Revisar al migrar a Spring Boot 4. |

### HIGH restantes

Backend, que se corrigen con Spring Boot 3.5 (CAL-107):

* `jackson-core` 2.18.5: 3. `jackson-databind` 2.18.5: 5.
* `micrometer-core` 1.14.14: 1. `postgresql` 42.7.8: 2.
* `spring-boot` 3.4.13: 1. `spring-data-commons` 3.4.13: 2.
* `spring-expression` 6.2.15: 1. `spring-webmvc` 6.2.15: 2.

Frontend, en dependencias transitivas de Next:

* `sharp` 0.34.5: 3, en la optimización de imágenes.
* `postcss` 8.4.31: 2. `nanoid` 3.3.16: 1. `source-map-js` 1.2.1: 1.

## Fuera de alcance

* Publicar las imágenes en un registro y escanearlas ahí: CAL-50.
* La IP del cliente que llega al backend la puede elegir el navegador con `X-Forwarded-For`: CAL-106. Lo encontramos durante esta entrega, pero ya existía en `main`.
