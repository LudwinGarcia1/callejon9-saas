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
| - RLS forzado en 15 tablas de datos                          |
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
- **Dockerfile:** Multi-stage build (`eclipse-temurin:21-jdk-jammy` para compilación con Maven Wrapper; `eclipse-temurin:21-jre-jammy` para runtime).
- **Seguridad:** Usuario no privilegiado `appuser` (UID 10001, GID 10001).
- **Conectividad:** Configura `DB_HOST=db`, `DB_PORT=5432`, `DB_NAME=callejon9`.
- **Dependencia:** `depends_on.db.condition: service_healthy`.
- **Healthcheck:** Monitorea `http://localhost:8080/actuator/health` cada 10 s.

### 3. `frontend` (Next.js 15 / React 19)
- **Dockerfile:** Multi-stage build (`node:24-slim` para resolución con `pnpm`, compilación con turbopack y runtime).
- **Seguridad:** Usuario no privilegiado `nextjs` (UID 10001, GID 10001).
- **Conectividad:** Configura `BACKEND_ORIGIN=http://backend:8080`.
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

### Sembrado de datos demo
Para poblar datos de prueba iniciales:
```powershell
.\scripts\seed-demo.ps1
```

### Detención y limpieza
- Detener los contenedores:
  ```bash
  docker compose down
  ```
- Detener y eliminar los volúmenes persistentes de datos:
  ```bash
  docker compose down -v
  ```
