# CAL-11 — Entorno completo con Docker Compose

## Resumen y alcance

CAL-11 provee la configuración completa, reproducible y segura para levantar el stack de Callejón 9 SaaS en cualquier estación de trabajo o servidor utilizando Docker Compose (`docker compose up -d`).

Elimina la necesidad de instalar previamente Java 21, Maven, PostgreSQL o Node.js/pnpm en la máquina anfitriona para ejecutar el sistema de extremo a extremo.

## Arquitectura de servicios

```
+--------------------------------------------------------------+
|                    Navegador del cliente                     |
|                   (http://localhost:3000)                    |
+------------------------------+-------------------------------+
                               |
                               v (puerto 3000)
+--------------------------------------------------------------+
| callejon9-frontend (Next.js 15 App Router / React 19)        |
| - Usuario no-root: nextjs (UID 10001)                        |
| - Rewrite interno: /api/v1/* -> http://backend:8080/api/v1/* |
| - Cookie first-party con SameSite=Strict                     |
+------------------------------+-------------------------------+
                               |
                               v (red interna compose: 8080)
+--------------------------------------------------------------+
| callejon9-backend (Spring Boot 3.4 / Java 21 Temurin)        |
| - Usuario no-root: appuser (UID 10001)                       |
| - Runtime conectado como callejon9_app (sujeto a RLS)        |
| - Migraciones Flyway ejecutadas como callejon9_owner         |
| - Healthcheck: /actuator/health                              |
+------------------------------+-------------------------------+
                               |
                               v (red interna compose: 5432)
+--------------------------------------------------------------+
| callejon9-db (PostgreSQL 16 Alpine)                          |
| - Volumen persistente: pgdata                                |
| - Init script: scripts/setup-db.sql                          |
| - Roles: callejon9_owner, callejon9_app                      |
| - Bases: callejon9, callejon9_test                           |
| - RLS forzado en 17 tablas de datos                          |
+--------------------------------------------------------------+
```

## Servicios definidos

### 1. `db` (PostgreSQL 16)
- **Imagen:** `postgres:16-alpine`.
- **Inicialización:** Monta `scripts/setup-db.sql` en `/docker-entrypoint-initdb.d/01-setup-db.sql` (modo sólo lectura).
- **Garantía RLS:** Crea `callejon9_owner` (dueño de esquemas y tablas) y `callejon9_app` (runtime sin privilegios de superusuario ni `BYPASSRLS`).
- **Healthcheck:** `pg_isready -U postgres -d callejon9` con reintentos para asegurar que la base esté lista antes de arrancar servicios dependientes.
- **Persistencia:** Volumen `pgdata` montado en `/var/lib/postgresql/data`.

### 2. `backend` (Spring Boot 3.4 / Java 21)
- **Dockerfile:** Multi-stage build (`eclipse-temurin:21.0.12.1_1-jdk-noble` para compilación con Maven Wrapper; `eclipse-temurin:21.0.12.1_1-jre-noble` para runtime, ambas fijadas por digest). Detalle en `docs/cal-17-imagenes-produccion.md`.
- **Perfil:** la imagen arranca en `prod`; Compose fija `SPRING_PROFILES_ACTIVE=default` para desarrollo.
- **Seguridad:** Usuario no privilegiado `appuser` (UID 10001, GID 10001).
- **Conectividad:** Configura `DB_HOST=db`, `DB_PORT=5432`, `DB_NAME=callejon9`.
- **Dependencia:** `depends_on.db.condition: service_healthy`.
- **Healthcheck:** Monitorea `http://localhost:8080/actuator/health` cada 10 s.

### 3. `frontend` (Next.js 15 / React 19)
- **Dockerfile:** Multi-stage build sobre `node:24.21.0-alpine3.24` fijada por digest; el runtime sirve solo la salida `standalone` de Next (CAL-17).
- **Seguridad:** Usuario no privilegiado `nextjs` (UID 10001, GID 10001).
- **Conectividad:** `BACKEND_ORIGIN=http://backend:8080` se pasa como argumento de compilación: Next fija el destino del rewrite al compilar, así que cambiarlo requiere reconstruir la imagen.
- **Healthcheck:** Monitorea `http://localhost:3000/login` cada 10 s.

## Uso operativo

### Requisitos
- Docker Engine 24+ y Docker Compose v2+.

### Puesta en marcha

1. (Opcional) Copiar variables de entorno:
   ```bash
   cp .env.example .env
   ```
2. Construir y levantar los contenedores:
   ```bash
   docker compose up -d --build
   ```
3. Verificar el estado de los servicios:
   ```bash
   docker compose ps
   ```
4. Consultar los logs en tiempo real:
   ```bash
   docker compose logs -f
   ```
5. Acceder a la aplicación:
   - Frontend: `http://localhost:3000`
   - Backend API Docs (Swagger): `http://localhost:8080/swagger-ui.html`
   - Healthcheck: `http://localhost:8080/actuator/health`

### Detención y limpieza
- Detener los contenedores:
  ```bash
  docker compose down
  ```
- Detener y eliminar los volúmenes persistentes de datos:
  ```bash
  docker compose down -v
  ```

## Verificación de extremo a extremo (2026-10-13)

- **Sintaxis y configuración:** `docker compose config` validado exitosamente.
- **Construcción de imágenes:**
  - `callejon9-backend`: Multi-stage build con `eclipse-temurin:21` generando `app.jar` sin errores.
  - `callejon9-frontend`: Multi-stage build con `node:24-slim` y `pnpm`, compilando Next.js con Turbopack exitosamente.
- **Ciclo de vida y healthchecks:**
  - `callejon9-db` (PostgreSQL 16 Alpine): estado `healthy` verificado mediante `pg_isready -U postgres -d callejon9`.
  - `callejon9-backend`: estado `healthy` verificado con `http://localhost:8080/actuator/health` respondiendo `{"status":"UP"}`.
  - `callejon9-frontend`: estado `healthy` verificado con `http://localhost:3000/login` respondiendo HTTP 200.
- **Principio de menor privilegio (no-root):**
  - Contenedor backend ejecuta como usuario `appuser` (UID 10001).
  - Contenedor frontend ejecuta como usuario `nextjs` (UID 10001).
- **Conectividad y rewrites same-origin:**
  - Petición `/api/v1/*` al frontend enrutada exitosamente hacia el backend en la red interna de Docker.
- **Limpieza:** `docker compose down -v` elimina contenedores y volúmenes sin recursos huérfanos.

> **Corrección (CAL-17, 2026-10-14):** la afirmación sobre los rewrites de esta verificación no se sostenía. Next fija el destino del rewrite al compilar y la imagen se compilaba sin `BACKEND_ORIGIN`, así que `/api/v1/*` se reenviaba a `http://localhost:8080` dentro del contenedor y respondía 500; la variable que Compose pasaba al arrancar no lo cambiaba. CAL-17 lo corrige con un argumento de compilación y lo verifica con una prueba de humo de alta, login y `/auth/me` a través del frontend (`docs/cal-17-imagenes-produccion.md`).


## Re-verificación sobre `main` (2026-10-28)

Se repitió la verificación sobre un checkout limpio de `main` (`9a130f4`) para cerrar los criterios pendientes de CAL-11 antes de liberar CAL-28. Se usó un nombre de proyecto y puertos alternos para no chocar con otro stack local; ninguno es obligatorio:

```bash
DB_PORT=55432 BACKEND_PORT=18080 FRONTEND_PORT=13000 docker compose -p cal11 up -d --build
```

| Criterio | Comprobación | Resultado |
|---|---|---|
| Los tres servicios levantan sanos | `docker compose -p cal11 ps` | `db`, `backend` y `frontend` en `healthy` (construcción completa en ~6 min, salida 0) |
| La UI responde | `GET /` y `GET /login` en el frontend | HTTP 200 en ambas |
| El rol de runtime no es privilegiado | `pg_roles` para `callejon9_%` | `callejon9_app` y `callejon9_owner` sin `SUPERUSER`, `BYPASSRLS`, `CREATEROLE` ni `CREATEDB` |
| El rol de runtime no es dueño de tablas | `pg_tables` agrupado por dueño | Las 21 tablas pertenecen a `callejon9_owner` |
| Flyway completa | `flyway_schema_history` | V1–V14 aplicadas, todas con `success = true` |
| RLS forzado | `pg_class` de tablas con `tenant_id` | 17 de 18 con `ENABLE` y `FORCE`; la restante es `subscriptions`, plano de control por diseño |
| `verify-rls.sql` en el contenedor | `psql -U callejon9_app -f /tmp/verify-rls.sql` dentro de `callejon9-db` | check_1 a check_9 con el resultado esperado: 0 filas sin tenant, aislamiento A/B, y los tres INSERT cruzados rechazados (dos por RLS, uno por FK compuesta). El script limpia sus tenants al final |
| Flujo same-origin real | alta, login, `/auth/me`, `/tables` y `/admin` a través de `:13000` | 201, 200, 200, 200 y 200; `access_token` y `refresh_token` llegan como cookies `HttpOnly` |
| Persistencia | marcador en `tenants` → `down` (sin `-v`) → `up --wait` | El volumen `cal11_pgdata` sobrevive, el marcador sigue presente y Flyway no reaplica nada (14 filas) |
| Reset explícito | `docker compose -p cal11 down -v` | Elimina contenedores, red y solo el volumen `cal11_pgdata` |
| Portabilidad | búsqueda de `C:\` y `5433` en `docker-compose.yml`, `.env.example`, Dockerfiles, `scripts/setup-db.sql` y esta guía | Sin coincidencias; todos los puertos publicados son configurables por variable |

La contraseña de la cuenta de prueba se generó al vuelo en una variable de shell y no se imprimió ni se guardó. El usuario no-root de las imágenes no se volvió a revisar aquí: lo cubre `scripts/verify-images.sh` (CAL-17).
