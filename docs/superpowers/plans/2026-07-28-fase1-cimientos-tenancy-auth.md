# Fase 1 — Cimientos, Tenancy y Autenticación: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Levantar el monorepo nuevo con un backend Spring Boot que impone aislamiento multi-tenant a nivel de motor PostgreSQL (RLS), con autenticación por JWT + 2FA TOTP y el control plane de tenants, planes y suscripciones.

**Architecture:** Monorepo `callejon9-saas` con `backend/` (Spring Boot 3.4, Java 21, Maven Wrapper). El aislamiento se logra con cuatro piezas encadenadas: `TenantFilter` extrae el tenant del JWT hacia un `TenantContext` (ThreadLocal, fail-closed); `TenantAwareTransactionManager` publica ese tenant a Postgres con `set_config('app.tenant_id', …, true)` al abrir cada transacción; las políticas RLS de cada tabla filtran por esa variable; y el rol de runtime `callejon9_app` no es dueño de las tablas, por lo que no puede saltarse las políticas.

## Estado de ejecución

Actualizado 2026-07-29. Repositorio: **https://github.com/LudwinGarcia1/callejon9-saas** (privado).

| Tarea | Estado |
|---|---|
| 1. Scaffold y conexión | **Completa y verificada.** `BUILD SUCCESS`, `contextLoads` en verde |
| 2. Control plane V1 | **Aplicada.** Flyway v1 |
| 3. Plano de datos V2 | **Aplicada.** Flyway v2, con `updated_at` en las 14 tablas |
| 4. RLS V3 y V4 | **Aplicada y verificada.** Flyway v3 y v4 |
| 5 a 11 | Pendientes |

**Sin bloqueadores.** JDK 21.0.11 y PostgreSQL 16 operativos. Toolchain verificado con Maven Wrapper descargando Maven 3.9.16 y resolviendo el árbol completo de dependencias.

**Puerta de aislamiento: PASADA** (2026-07-29). `scripts/verify-rls.sql` ejecutado como `callejon9_app` contra `callejon9_test`:

```
check_1  sin tenant -> 0
check_2  tenant A ve -> 1
check_3  tenant B ve -> 0
check_4  ERROR: new row violates row-level security policy for table "users"
```

Los cuatro resultados coinciden con lo especificado. El aislamiento entre tenants lo impone PostgreSQL.

**Nota sobre la ejecución en paralelo.** Las Tasks 5 a 11 forman una cadena de dependencias casi lineal (5 → 6 y 7 → 8 → 9 y 10 → 11), y además comparten una única base `callejon9_test`: dos agentes corriendo `mvnw test` a la vez se pisarían los datos sembrados. Por eso se ejecutan **secuencialmente**, un subagente por tarea con revisión entre cada una. El paralelismo real está entre el backend y el frontend (Plan 4), no dentro de esta fase.

**Corrección al plan original:** la migración `V5__refresh_tokens_updated_at.sql` mencionada en la Task 11 Step 3 **ya no es necesaria**: `V2__data_plane.sql` incluye `updated_at` en todas las tablas del plano de datos, así que la tercera trampa documentada en la auto-revisión queda eliminada de raíz.

---

**Tech Stack:** Java 21 (Temurin), Spring Boot 3.4.13, Spring Web, Spring Security, Spring Data JPA, Hibernate 6, Flyway, PostgreSQL 16 nativo, Lombok, `dev.samstevens.totp`, springdoc-openapi, JUnit 5, AssertJ, Maven Wrapper.

## Global Constraints

- **Código en inglés**: clases, métodos, variables, ramas y mensajes de commit. Documentación en español.
- **Paquete raíz**: `com.callejon9`. Estructura por feature, no por capa: cada feature contiene `web/`, `domain/`, `service/`, `repository/`.
- **Java 21** exacto (`<java.version>21</java.version>`). Spring Boot **3.4.13** (último parche de la línea 3.4; ver §4.4 del spec para por qué no 4.x).
- **Ruta del repositorio nuevo**: `C:\Users\pingu\callejon9-saas`. El proyecto Flask original queda intacto en `C:\Users\pingu\Restaurante-Callejon-9`.
- **Sin Docker**. PostgreSQL 16 nativo en `localhost:5432`. Base de desarrollo `callejon9`, base de tests `callejon9_test`.
- **Dos roles de base de datos**: `callejon9_owner` (solo Flyway) y `callejon9_app` (runtime, sujeto a RLS, sin `BYPASSRLS`).
- **TDD obligatorio**: test que falla primero, luego el mínimo código que lo hace pasar.
- **`spring.jpa.hibernate.ddl-auto: validate`**. El esquema lo define Flyway, nunca Hibernate.
- **`spring.jpa.open-in-view: false`**.
- Nunca commitear contraseñas. Todas por variable de entorno, con `.env.example` documentado.
- Un commit por tarea, mensaje en inglés con prefijo convencional (`feat:`, `test:`, `chore:`, `docs:`).

---

## Estructura de archivos

```
C:\Users\pingu\callejon9-saas\
├── .gitignore
├── .env.example
├── README.md
├── scripts/
│   ├── setup-db.sql                  crea roles y bases
│   └── run-dev.ps1                   levanta backend + frontend
├── .github/workflows/ci.yml          build + tests con service container postgres:16
└── backend/
    ├── mvnw, mvnw.cmd, .mvn/         Maven Wrapper (generado por Spring Initializr)
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/callejon9/
        │   │   ├── Callejon9Application.java
        │   │   ├── config/
        │   │   │   ├── SecurityConfig.java
        │   │   │   ├── PersistenceConfig.java      registra el TransactionManager
        │   │   │   └── OpenApiConfig.java
        │   │   ├── tenancy/
        │   │   │   ├── TenantContext.java
        │   │   │   ├── NoTenantContextException.java
        │   │   │   ├── TenantFilter.java
        │   │   │   └── TenantAwareTransactionManager.java
        │   │   ├── shared/
        │   │   │   ├── domain/BaseEntity.java
        │   │   │   ├── domain/TenantScopedEntity.java
        │   │   │   ├── error/BusinessRuleException.java
        │   │   │   ├── error/ResourceNotFoundException.java
        │   │   │   └── error/GlobalExceptionHandler.java
        │   │   ├── platform/
        │   │   │   ├── plan/         Plan, PlanRepository, PlanService, PlanController
        │   │   │   ├── tenant/       Tenant, TenantRepository, TenantOnboardingService, TenantController
        │   │   │   └── subscription/ Subscription, SubscriptionRepository
        │   │   ├── user/             User, UserRole, UserRepository, UserService
        │   │   └── auth/
        │   │       ├── domain/RefreshToken.java
        │   │       ├── repository/RefreshTokenRepository.java
        │   │       ├── service/JwtService.java
        │   │       ├── service/TotpService.java
        │   │       ├── service/AuthService.java
        │   │       └── web/AuthController.java + DTOs
        │   └── resources/
        │       ├── application.yml
        │       └── db/migration/
        │           ├── V1__control_plane.sql
        │           ├── V2__data_plane.sql
        │           ├── V3__row_level_security.sql
        │           └── V4__app_role_grants.sql
        └── test/
            ├── java/com/callejon9/
            │   ├── tenancy/TenantContextTest.java
            │   ├── tenancy/TenantIsolationTest.java        ← prueba estrella
            │   ├── auth/BcryptCompatibilityTest.java
            │   ├── auth/JwtServiceTest.java
            │   ├── auth/AuthControllerTest.java
            │   └── platform/TenantOnboardingServiceTest.java
            └── resources/application-test.yml
```

**Responsabilidad de cada pieza clave:**

| Archivo | Única responsabilidad |
|---|---|
| `TenantContext` | Guardar y exponer el tenant activo del hilo. Falla cerrado si no hay. |
| `TenantFilter` | Traducir el JWT autenticado a `TenantContext`. Nada más. |
| `TenantAwareTransactionManager` | Publicar el tenant a la sesión de Postgres al abrir transacción. |
| `V3__row_level_security.sql` | Definir las políticas. Es el único lugar donde vive la regla de aislamiento en SQL. |
| `TenantIsolationTest` | Demostrar que el aislamiento funciona. Es la evidencia del proyecto. |

---

## Task 1: Monorepo, scaffold del backend y arranque contra Postgres

**Files:**
- Create: `C:\Users\pingu\callejon9-saas\.gitignore`
- Create: `C:\Users\pingu\callejon9-saas\.env.example`
- Create: `C:\Users\pingu\callejon9-saas\README.md`
- Create: `C:\Users\pingu\callejon9-saas\scripts\setup-db.sql`
- Create: `backend/` completo vía Spring Initializr (incluye `pom.xml`, `mvnw`, `Callejon9Application.java`)
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/resources/application-test.yml`
- Test: `backend/src/test/java/com/callejon9/Callejon9ApplicationTests.java` (generado, se ajusta)

**Interfaces:**
- Consumes: nada (primera tarea)
- Produces: proyecto Maven compilable con `./mvnw`; bases `callejon9` y `callejon9_test`; roles `callejon9_owner` y `callejon9_app`; perfil `test` apuntando a `callejon9_test`

- [ ] **Step 1: Verificar que JDK 21 y PostgreSQL 16 quedaron instalados**

```powershell
java -version
psql --version
```
Expected: `openjdk version "21..."` y `psql (PostgreSQL) 16...`. Si `java` sigue en 17, cierra y reabre la terminal para que tome el `PATH` nuevo.

- [ ] **Step 2: Crear el repositorio local**

```powershell
New-Item -ItemType Directory -Force C:\Users\pingu\callejon9-saas\scripts
Set-Location C:\Users\pingu\callejon9-saas
git init -b main
```

- [ ] **Step 3: Escribir el script de creación de roles y bases**

Crear `scripts/setup-db.sql`:

```sql
-- Roles: el owner solo migra, el app corre la aplicacion sujeto a RLS.
CREATE ROLE callejon9_owner LOGIN PASSWORD 'owner_dev_pwd';
CREATE ROLE callejon9_app   LOGIN PASSWORD 'app_dev_pwd';

CREATE DATABASE callejon9      OWNER callejon9_owner;
CREATE DATABASE callejon9_test OWNER callejon9_owner;

\connect callejon9
GRANT USAGE ON SCHEMA public TO callejon9_app;

\connect callejon9_test
GRANT USAGE ON SCHEMA public TO callejon9_app;
```

- [ ] **Step 4: Ejecutar el script**

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -f scripts\setup-db.sql
```
Te pedirá la contraseña de `postgres` que definiste en el instalador.
Expected: `CREATE ROLE` ×2, `CREATE DATABASE` ×2, `GRANT` ×2.

- [ ] **Step 5: Verificar que el rol de aplicación NO puede saltarse RLS**

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -d callejon9 -c "SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname LIKE 'callejon9%';"
```
Expected: ambos roles con `rolsuper = f` y `rolbypassrls = f`. Si `callejon9_app` tuviera `rolbypassrls = t`, todo el aislamiento sería decorativo.

- [ ] **Step 6: Generar el scaffold del backend con Spring Initializr**

```powershell
Invoke-WebRequest -Uri "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.1.0.RELEASE&javaVersion=21&groupId=com.callejon9&artifactId=backend&name=callejon9&packageName=com.callejon9&dependencies=web,security,data-jpa,validation,flyway,postgresql,websocket,actuator,lombok" -OutFile backend.zip
Expand-Archive backend.zip -DestinationPath .
Remove-Item backend.zip
```
Expected: los archivos del proyecto (`pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`, `src/`) quedan en la **raíz**, no dentro de `backend/`: el zip de Initializr no envuelve en una carpeta. Muévelos a `backend/` con:

```powershell
New-Item -ItemType Directory -Force backend | Out-Null
foreach ($i in @('.mvn','src','mvnw','mvnw.cmd','pom.xml','HELP.md','.gitattributes')) {
    if (Test-Path $i) { Move-Item -Path $i -Destination backend\ -Force }
}
Remove-Item -Force backend\HELP.md, backend\src\main\resources\application.properties
```

**Importante**: Initializr solo sirve la línea 4.x, que trae otros nombres de starter (`-webmvc`) y sin `spring-boot-starter-test`. El siguiente paso reemplaza el `pom.xml` completo por uno de la línea 3.4.

- [ ] **Step 7: Añadir las dependencias que Initializr no ofrece**

En `backend/pom.xml`, dentro de `<dependencies>`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.7.0</version>
</dependency>
<dependency>
    <groupId>dev.samstevens.totp</groupId>
    <artifactId>totp</artifactId>
    <version>1.7.1</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 8: Escribir `application.yml`**

Crear `backend/src/main/resources/application.yml`. Nota clave: `spring.datasource` usa el rol de aplicación (sujeto a RLS) y `spring.flyway` usa el owner (que debe poder crear tablas). Esta separación es la que hace efectivo el aislamiento.

```yaml
spring:
  application:
    name: callejon9
  datasource:
    url: jdbc:postgresql://localhost:5432/callejon9
    username: callejon9_app
    password: ${DB_APP_PASSWORD:app_dev_pwd}
    hikari:
      maximum-pool-size: 10
  flyway:
    enabled: true
    user: callejon9_owner
    password: ${DB_OWNER_PASSWORD:owner_dev_pwd}
    schemas: public
    locations: classpath:db/migration
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          time_zone: UTC

app:
  jwt:
    secret: ${JWT_SECRET:dev-only-secret-change-me-min-32-bytes-long!!}
    access-token-minutes: 15
    refresh-token-days: 7

server:
  port: 8080

logging:
  level:
    org.flywaydb: INFO
```

- [ ] **Step 9: Escribir `application-test.yml`**

Crear `backend/src/test/resources/application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/callejon9_test
    username: callejon9_app
    password: ${DB_APP_PASSWORD:app_dev_pwd}
  flyway:
    user: callejon9_owner
    password: ${DB_OWNER_PASSWORD:owner_dev_pwd}
    clean-disabled: false
  jpa:
    hibernate:
      ddl-auto: validate
```

- [ ] **Step 10: Escribir `.gitignore`, `.env.example` y `README.md`**

`.gitignore`:
```
target/
node_modules/
.next/
.env
*.log
.idea/
.vscode/
```

`.env.example`:
```
DB_APP_PASSWORD=app_dev_pwd
DB_OWNER_PASSWORD=owner_dev_pwd
JWT_SECRET=cambia-esto-por-un-secreto-de-al-menos-32-bytes
```

`README.md`: título, una línea de propósito, requisitos (JDK 21, PostgreSQL 16, Node 24), los pasos de `scripts/setup-db.sql`, cómo correr `./mvnw spring-boot:run`, y un enlace al repositorio Flask original para trazabilidad.

- [ ] **Step 11: Ajustar el test de contexto para usar el perfil `test`**

Reemplazar `backend/src/test/java/com/callejon9/Callejon9ApplicationTests.java`:

```java
package com.callejon9;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class Callejon9ApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 12: Correr el test — debe fallar**

```powershell
Set-Location backend; .\mvnw.cmd test
```
Expected: FALLA. Hibernate con `ddl-auto: validate` no encuentra tablas y Flyway no tiene migraciones. Este fallo es el esperado: confirma que la validación de esquema está activa y que la Task 2 tiene trabajo real.

- [ ] **Step 13: Commit**

```bash
git add .
git commit -m "chore: scaffold monorepo and Spring Boot backend

Adds Maven Wrapper project on Java 21 with Spring Boot 3.4.13, split
datasource roles (app user for runtime, owner for Flyway migrations)
and the database bootstrap script."
```

---

## Task 2: Esquema del control plane (Flyway V1)

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__control_plane.sql`

**Interfaces:**
- Consumes: bases y roles de la Task 1
- Produces: tablas `plans`, `tenants`, `subscriptions`. Estas tablas **no llevan `tenant_id` y no tienen RLS**: son el catálogo global del SaaS.

- [ ] **Step 1: Escribir la migración**

Crear `backend/src/main/resources/db/migration/V1__control_plane.sql`:

```sql
-- ============================================================
-- CONTROL PLANE: catalogo global del SaaS.
-- Estas tablas NO son tenant-scoped y NO llevan RLS: son
-- precisamente el registro de tenants y sus planes.
-- El acceso se restringe por rol SUPER_ADMIN en la aplicacion.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE plans (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            varchar(40)  NOT NULL UNIQUE,
    name            varchar(120) NOT NULL,
    price_monthly   numeric(10,2) NOT NULL DEFAULT 0,
    max_users       integer      NOT NULL,
    max_tables      integer      NOT NULL,
    features        jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE tenants (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(160) NOT NULL,
    slug        varchar(80)  NOT NULL UNIQUE,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    plan_id              uuid NOT NULL REFERENCES plans(id),
    status               varchar(30) NOT NULL,
    started_at           timestamptz NOT NULL DEFAULT now(),
    current_period_end   timestamptz,
    canceled_at          timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT subscriptions_status_check
        CHECK (status IN ('ACTIVE', 'PAST_DUE', 'CANCELED', 'TRIALING'))
);

CREATE UNIQUE INDEX subscriptions_one_active_per_tenant
    ON subscriptions (tenant_id) WHERE status IN ('ACTIVE', 'TRIALING');

-- Planes iniciales del SaaS.
INSERT INTO plans (code, name, price_monthly, max_users, max_tables, features) VALUES
  ('FREE',    'Gratis',      0.00,   3,  5,  '{"reports": false, "delivery": false}'),
  ('PRO',     'Profesional', 499.00, 15, 30, '{"reports": true,  "delivery": false}'),
  ('PREMIUM', 'Premium',     999.00, 60, 120,'{"reports": true,  "delivery": true}');
```

- [ ] **Step 2: Correr Flyway y verificar que aplica**

```powershell
.\mvnw.cmd flyway:info -Dflyway.configFiles=
```
Si el plugin de Flyway no está configurado en el `pom.xml`, usa en su lugar el arranque de la aplicación, que corre las migraciones automáticamente:
```powershell
.\mvnw.cmd spring-boot:run
```
Expected: en el log, `Migrating schema "public" to version "1 - control plane"` y `Successfully applied 1 migration`. Detén la aplicación con `Ctrl+C`.

- [ ] **Step 3: Verificar los planes sembrados**

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U callejon9_owner -d callejon9 -c "SELECT code, max_users, max_tables FROM plans ORDER BY price_monthly;"
```
Expected: tres filas — `FREE 3 5`, `PRO 15 30`, `PREMIUM 60 120`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V1__control_plane.sql
git commit -m "feat: add control plane schema with plans, tenants and subscriptions"
```

---

## Task 3: Esquema del plano de datos (Flyway V2)

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__data_plane.sql`

**Interfaces:**
- Consumes: `tenants` de la Task 2
- Produces: 14 tablas tenant-scoped, todas con columna `tenant_id uuid NOT NULL REFERENCES tenants(id)`. Todavía **sin** RLS; las políticas llegan en la Task 4.

- [ ] **Step 1: Escribir la migración**

Crear `backend/src/main/resources/db/migration/V2__data_plane.sql`:

```sql
-- ============================================================
-- PLANO DE DATOS: todas las tablas llevan tenant_id.
-- Las politicas RLS se agregan en V3.
-- Correcciones sobre el modelo Mongo original:
--   * restaurant_tables.number pasa a integer (antes int|str mezclados)
--   * orders referencia la mesa por FK (antes por mesa_numero)
--   * products unifica las colecciones productos y platillos
-- ============================================================

CREATE TABLE users (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email          varchar(180) NOT NULL,
    password_hash  varchar(100) NOT NULL,
    full_name      varchar(160) NOT NULL,
    role           varchar(20)  NOT NULL,
    active         boolean      NOT NULL DEFAULT true,
    totp_secret    varchar(64),
    totp_enabled   boolean      NOT NULL DEFAULT false,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT users_email_unique_per_tenant UNIQUE (tenant_id, email),
    CONSTRAINT users_role_check
        CHECK (role IN ('SUPER_ADMIN','ADMIN','WAITER','KITCHEN','CASHIER'))
);

CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  varchar(100) NOT NULL UNIQUE,
    expires_at  timestamptz  NOT NULL,
    revoked_at  timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE restaurant_tables (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    number      integer      NOT NULL,
    capacity    integer      NOT NULL DEFAULT 4,
    status      varchar(20)  NOT NULL DEFAULT 'FREE',
    waiter_id   uuid REFERENCES users(id) ON DELETE SET NULL,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT restaurant_tables_number_unique_per_tenant UNIQUE (tenant_id, number),
    CONSTRAINT restaurant_tables_status_check
        CHECK (status IN ('FREE','OCCUPIED','RESERVED','CLEANING'))
);

CREATE TABLE categories (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        varchar(120) NOT NULL,
    sort_order  integer      NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT categories_name_unique_per_tenant UNIQUE (tenant_id, name)
);

CREATE TABLE products (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    category_id  uuid REFERENCES categories(id) ON DELETE SET NULL,
    name         varchar(160)  NOT NULL,
    description  text,
    price        numeric(10,2) NOT NULL CHECK (price >= 0),
    active       boolean       NOT NULL DEFAULT true,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now()
);

CREATE TABLE customers (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    full_name   varchar(160) NOT NULL,
    phone       varchar(40),
    email       varchar(180),
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    folio                varchar(40) NOT NULL,
    table_id             uuid REFERENCES restaurant_tables(id) ON DELETE SET NULL,
    waiter_id            uuid REFERENCES users(id) ON DELETE SET NULL,
    guest_count          integer      NOT NULL DEFAULT 1,
    status               varchar(20)  NOT NULL DEFAULT 'NEW',
    total                numeric(10,2) NOT NULL DEFAULT 0,
    opened_at            timestamptz  NOT NULL DEFAULT now(),
    sent_to_kitchen_at   timestamptz,
    closed_at            timestamptz,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT orders_folio_unique_per_tenant UNIQUE (tenant_id, folio),
    CONSTRAINT orders_status_check
        CHECK (status IN ('NEW','SENT','READY','PAID','CANCELED'))
);

CREATE TABLE order_items (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    order_id        uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      uuid REFERENCES products(id) ON DELETE SET NULL,
    product_name    varchar(160)  NOT NULL,
    unit_price      numeric(10,2) NOT NULL CHECK (unit_price >= 0),
    quantity        integer       NOT NULL CHECK (quantity > 0),
    kitchen_status  varchar(20)   NOT NULL DEFAULT 'PENDING',
    notes           text,
    ordered_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT order_items_kitchen_status_check
        CHECK (kitchen_status IN ('PENDING','IN_PREPARATION','READY','DELIVERED'))
);

CREATE TABLE sales (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    order_id        uuid NOT NULL REFERENCES orders(id),
    table_id        uuid REFERENCES restaurant_tables(id) ON DELETE SET NULL,
    cashier_id      uuid REFERENCES users(id) ON DELETE SET NULL,
    status          varchar(20)   NOT NULL DEFAULT 'PENDING',
    payment_method  varchar(20)   NOT NULL,
    subtotal        numeric(10,2) NOT NULL DEFAULT 0,
    tip             numeric(10,2) NOT NULL DEFAULT 0,
    total           numeric(10,2) NOT NULL DEFAULT 0,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT sales_status_check
        CHECK (status IN ('PENDING','COMPLETED','CANCELED')),
    CONSTRAINT sales_payment_method_check
        CHECK (payment_method IN ('CASH','CARD','TRANSFER','MIXED','MERCADOPAGO'))
);

CREATE TABLE payments (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sale_id               uuid NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    provider              varchar(40)   NOT NULL,
    provider_payment_id   varchar(120),
    method                varchar(20)   NOT NULL,
    amount                numeric(10,2) NOT NULL CHECK (amount >= 0),
    status                varchar(20)   NOT NULL,
    raw_response          jsonb,
    created_at            timestamptz   NOT NULL DEFAULT now()
);

CREATE TABLE tickets (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sale_id         uuid NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    order_id        uuid NOT NULL REFERENCES orders(id),
    folio           varchar(40)   NOT NULL,
    items_snapshot  jsonb         NOT NULL,
    subtotal        numeric(10,2) NOT NULL,
    tip             numeric(10,2) NOT NULL DEFAULT 0,
    tip_percent     numeric(5,2)  NOT NULL DEFAULT 0,
    total           numeric(10,2) NOT NULL,
    payment_method  varchar(20)   NOT NULL,
    closed_at       timestamptz   NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT tickets_folio_unique_per_tenant UNIQUE (tenant_id, folio)
);

CREATE TABLE inventory_items (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        varchar(160)  NOT NULL,
    unit        varchar(20)   NOT NULL,
    stock       numeric(12,3) NOT NULL DEFAULT 0,
    min_stock   numeric(12,3) NOT NULL DEFAULT 0,
    unit_cost   numeric(10,2) NOT NULL DEFAULT 0,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT inventory_items_name_unique_per_tenant UNIQUE (tenant_id, name)
);

CREATE TABLE inventory_movements (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    inventory_item_id   uuid NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    movement_type       varchar(20)   NOT NULL,
    quantity            numeric(12,3) NOT NULL,
    reason              varchar(200),
    user_id             uuid REFERENCES users(id) ON DELETE SET NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT inventory_movements_type_check
        CHECK (movement_type IN ('IN','OUT','ADJUSTMENT','WASTE'))
);

CREATE TABLE notifications (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     uuid REFERENCES users(id) ON DELETE CASCADE,
    title       varchar(160) NOT NULL,
    body        text,
    read_at     timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

-- Indices por tenant: toda consulta filtra por tenant_id, asi que va primero.
CREATE INDEX idx_users_tenant               ON users (tenant_id);
CREATE INDEX idx_refresh_tokens_tenant_user ON refresh_tokens (tenant_id, user_id);
CREATE INDEX idx_tables_tenant              ON restaurant_tables (tenant_id);
CREATE INDEX idx_categories_tenant          ON categories (tenant_id);
CREATE INDEX idx_products_tenant            ON products (tenant_id);
CREATE INDEX idx_customers_tenant           ON customers (tenant_id);
CREATE INDEX idx_orders_tenant_status       ON orders (tenant_id, status);
CREATE INDEX idx_order_items_tenant_order   ON order_items (tenant_id, order_id);
CREATE INDEX idx_sales_tenant_created       ON sales (tenant_id, created_at DESC);
CREATE INDEX idx_payments_tenant_sale       ON payments (tenant_id, sale_id);
CREATE INDEX idx_tickets_tenant_created     ON tickets (tenant_id, created_at DESC);
CREATE INDEX idx_inventory_items_tenant     ON inventory_items (tenant_id);
CREATE INDEX idx_inventory_mov_tenant_item  ON inventory_movements (tenant_id, inventory_item_id);
CREATE INDEX idx_notifications_tenant_user  ON notifications (tenant_id, user_id);
```

- [ ] **Step 2: Aplicar la migración**

```powershell
.\mvnw.cmd spring-boot:run
```
Expected: `Migrating schema "public" to version "2 - data plane"` y `Successfully applied 1 migration`. Detén con `Ctrl+C`.

- [ ] **Step 3: Verificar que las 17 tablas existen**

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U callejon9_owner -d callejon9 -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name NOT LIKE 'flyway%';"
```
Expected: `17`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V2__data_plane.sql
git commit -m "feat: add tenant-scoped data plane schema

Fourteen tables carrying tenant_id, normalising three defects from the
Mongo model: mixed-type table numbers, orders referencing tables by
number instead of by key, and products split across two collections."
```

---

## Task 4: Políticas RLS y permisos del rol de aplicación (Flyway V3 y V4)

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__row_level_security.sql`
- Create: `backend/src/main/resources/db/migration/V4__app_role_grants.sql`

**Interfaces:**
- Consumes: las 14 tablas de la Task 3
- Produces: aislamiento impuesto por el motor. La variable de sesión que gobierna todo es `app.tenant_id`, y el rol `callejon9_app` queda con permisos DML pero sujeto a las políticas.

- [ ] **Step 1: Escribir las políticas RLS**

Crear `backend/src/main/resources/db/migration/V3__row_level_security.sql`:

```sql
-- ============================================================
-- ROW LEVEL SECURITY
--
-- Cada tabla del plano de datos queda gobernada por la variable
-- de sesion app.tenant_id, que la aplicacion fija por transaccion
-- con set_config('app.tenant_id', <uuid>, true).
--
-- USING      -> filtra SELECT, UPDATE y DELETE
-- WITH CHECK -> impide INSERT o UPDATE hacia otro tenant
--
-- FORCE ROW LEVEL SECURITY es indispensable: sin el, el DUENO de
-- la tabla ignora las politicas, y como Flyway crea las tablas
-- siendo callejon9_owner, ese rol las evadiria por completo.
-- ============================================================

DO $$
DECLARE
    t text;
    tenant_tables text[] := ARRAY[
        'users', 'refresh_tokens', 'restaurant_tables', 'categories',
        'products', 'customers', 'orders', 'order_items', 'sales',
        'payments', 'tickets', 'inventory_items', 'inventory_movements',
        'notifications'
    ];
BEGIN
    FOREACH t IN ARRAY tenant_tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE  ROW LEVEL SECURITY', t);
        EXECUTE format($f$
            CREATE POLICY tenant_isolation ON %I
                USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
                WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $f$, t);
    END LOOP;
END $$;
```

Nota sobre `current_setting('app.tenant_id', true)`: el segundo argumento `true` significa *missing_ok*, así que devuelve `NULL` en lugar de lanzar error cuando la variable no está fijada. Con `NULL`, la comparación `tenant_id = NULL` es `NULL`, que RLS trata como falso: **sin tenant activo no se ve ninguna fila**. Eso es exactamente el comportamiento fail-closed que se busca.

- [ ] **Step 2: Escribir los permisos del rol de aplicación**

Crear `backend/src/main/resources/db/migration/V4__app_role_grants.sql`:

```sql
-- ============================================================
-- Permisos del rol de runtime.
-- callejon9_app recibe DML sobre todas las tablas pero NO es
-- dueno de ninguna, por lo que las politicas RLS si le aplican.
-- ============================================================

GRANT USAGE ON SCHEMA public TO callejon9_app;

GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA public
    TO callejon9_app;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO callejon9_app;

-- Que los objetos creados por futuras migraciones hereden estos permisos.
ALTER DEFAULT PRIVILEGES FOR ROLE callejon9_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO callejon9_app;

ALTER DEFAULT PRIVILEGES FOR ROLE callejon9_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO callejon9_app;
```

- [ ] **Step 3: Aplicar las migraciones**

```powershell
.\mvnw.cmd spring-boot:run
```
Expected: `Successfully applied 2 migrations` (versiones 3 y 4). Detén con `Ctrl+C`.

- [ ] **Step 4: Verificar RLS a mano, con psql, antes de escribir código Java**

Este paso es el que demuestra que el aislamiento es real. Ejecutar como el rol de aplicación:

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U callejon9_owner -d callejon9 -c "INSERT INTO tenants (name, slug) VALUES ('Tenant A', 'tenant-a'), ('Tenant B', 'tenant-b');"
```

Ahora, conectado como `callejon9_app`, sembrar un usuario en cada tenant y comprobar el aislamiento:

```sql
-- Guardar como scripts\verify-rls.sql y ejecutar con:
--   psql -U callejon9_app -d callejon9 -f scripts\verify-rls.sql

\set ON_ERROR_STOP off

-- Sin tenant activo: no debe verse NADA.
SELECT 'sin tenant -> ' || count(*) AS check_1 FROM users;

-- Sembrar un usuario dentro del tenant A.
BEGIN;
  SELECT set_config('app.tenant_id', (SELECT id::text FROM tenants WHERE slug='tenant-a'), true);
  INSERT INTO users (tenant_id, email, password_hash, full_name, role)
  VALUES ((SELECT id FROM tenants WHERE slug='tenant-a'),
          'a@demo.com', 'x', 'Usuario A', 'ADMIN');
  SELECT 'tenant A ve -> ' || count(*) AS check_2 FROM users;
COMMIT;

-- Desde el tenant B, el usuario de A debe ser invisible.
BEGIN;
  SELECT set_config('app.tenant_id', (SELECT id::text FROM tenants WHERE slug='tenant-b'), true);
  SELECT 'tenant B ve -> ' || count(*) AS check_3 FROM users;
COMMIT;

-- Insertar en nombre de OTRO tenant debe fallar por WITH CHECK.
BEGIN;
  SELECT set_config('app.tenant_id', (SELECT id::text FROM tenants WHERE slug='tenant-b'), true);
  INSERT INTO users (tenant_id, email, password_hash, full_name, role)
  VALUES ((SELECT id FROM tenants WHERE slug='tenant-a'),
          'intruso@demo.com', 'x', 'Intruso', 'ADMIN');
ROLLBACK;
```

Expected exactamente:
- `check_1`: `sin tenant -> 0`
- `check_2`: `tenant A ve -> 1`
- `check_3`: `tenant B ve -> 0`
- El último `INSERT` falla con `ERROR: new row violates row-level security policy for table "users"`

Si `check_1` devuelve más de 0, o si el `INSERT` cruzado tiene éxito, **detente y no avances**: algo está mal en las políticas y todo lo que sigue construiría sobre una garantía falsa.

- [ ] **Step 5: Limpiar los datos de verificación**

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U callejon9_owner -d callejon9 -c "DELETE FROM users; DELETE FROM tenants;"
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V3__row_level_security.sql backend/src/main/resources/db/migration/V4__app_role_grants.sql scripts/verify-rls.sql
git commit -m "feat: enforce tenant isolation with PostgreSQL row level security

Adds USING and WITH CHECK policies on all fourteen tenant-scoped tables,
forces RLS so the table owner cannot bypass it, and grants the runtime
role DML without ownership. Includes a psql script that verifies the
guarantee end to end."
```

---

## Task 5: TenantContext — el tenant activo del hilo, fail-closed

**Files:**
- Create: `backend/src/main/java/com/callejon9/tenancy/TenantContext.java`
- Create: `backend/src/main/java/com/callejon9/tenancy/NoTenantContextException.java`
- Test: `backend/src/test/java/com/callejon9/tenancy/TenantContextTest.java`

**Interfaces:**
- Consumes: nada
- Produces:
  - `TenantContext.set(UUID tenantId)` → `void`
  - `TenantContext.require()` → `UUID`, lanza `NoTenantContextException` si no hay
  - `TenantContext.currentOrNull()` → `UUID` o `null`
  - `TenantContext.clear()` → `void`
  - `NoTenantContextException extends RuntimeException`

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/callejon9/tenancy/TenantContextTest.java`:

```java
package com.callejon9.tenancy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requireReturnsTheTenantThatWasSet() {
        UUID tenantId = UUID.randomUUID();

        TenantContext.set(tenantId);

        assertThat(TenantContext.require()).isEqualTo(tenantId);
    }

    @Test
    void requireFailsClosedWhenNoTenantIsSet() {
        assertThatThrownBy(TenantContext::require)
                .isInstanceOf(NoTenantContextException.class)
                .hasMessageContaining("restaurante identificado");
    }

    @Test
    void currentOrNullReturnsNullInsteadOfThrowing() {
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void clearRemovesTheTenant() {
        TenantContext.set(UUID.randomUUID());

        TenantContext.clear();

        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void tenantDoesNotLeakToAnotherThread() throws Exception {
        TenantContext.set(UUID.randomUUID());

        UUID[] seenByOtherThread = new UUID[1];
        Thread other = new Thread(() -> seenByOtherThread[0] = TenantContext.currentOrNull());
        other.start();
        other.join();

        assertThat(seenByOtherThread[0]).isNull();
    }
}
```

- [ ] **Step 2: Correr el test — debe fallar**

```powershell
.\mvnw.cmd test -Dtest=TenantContextTest
```
Expected: FALLA en compilación, `cannot find symbol: class TenantContext`.

- [ ] **Step 3: Escribir la excepción**

Crear `backend/src/main/java/com/callejon9/tenancy/NoTenantContextException.java`:

```java
package com.callejon9.tenancy;

/**
 * Se intento acceder a datos sin un tenant activo. El sistema falla cerrado:
 * ninguna consulta corre sin un restaurante identificado.
 */
public class NoTenantContextException extends RuntimeException {

    public NoTenantContextException() {
        super("No hay un tenant activo en el contexto. "
                + "El acceso a datos requiere un restaurante identificado.");
    }
}
```

- [ ] **Step 4: Escribir el TenantContext**

Crear `backend/src/main/java/com/callejon9/tenancy/TenantContext.java`:

```java
package com.callejon9.tenancy;

import java.util.UUID;

/**
 * Mantiene el tenant activo durante el ciclo de vida de una peticion.
 *
 * Equivale al {@code utils/tenant_context.py} del sistema Flask original, que
 * usaba contextvars. Aqui se usa un ThreadLocal, y como Spring MVC atiende cada
 * peticion en su propio hilo, el aislamiento entre peticiones es el mismo.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID require() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new NoTenantContextException();
        }
        return tenantId;
    }

    public static UUID currentOrNull() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

- [ ] **Step 5: Correr el test — debe pasar**

```powershell
.\mvnw.cmd test -Dtest=TenantContextTest
```
Expected: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/callejon9/tenancy backend/src/test/java/com/callejon9/tenancy
git commit -m "feat: add fail-closed tenant context

Ports the contextvars-based tenant context from the Flask system to a
ThreadLocal, preserving the fail-closed contract: no data access without
an identified restaurant."
```

---

## Task 6: TenantAwareTransactionManager — publicar el tenant a Postgres

**Files:**
- Create: `backend/src/main/java/com/callejon9/tenancy/TenantAwareTransactionManager.java`
- Create: `backend/src/main/java/com/callejon9/config/PersistenceConfig.java`
- Test: `backend/src/test/java/com/callejon9/tenancy/TenantIsolationTest.java` (la prueba estrella)

**Interfaces:**
- Consumes: `TenantContext.currentOrNull()` de la Task 5; tablas y políticas de las Tasks 3 y 4
- Produces: `TenantAwareTransactionManager extends JpaTransactionManager`, registrado como el `PlatformTransactionManager` primario. A partir de aquí, **cualquier** método `@Transactional` corre con `app.tenant_id` fijado.

- [ ] **Step 1: Escribir la prueba estrella, que falla**

Crear `backend/src/test/java/com/callejon9/tenancy/TenantIsolationTest.java`:

```java
package com.callejon9.tenancy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Evidencia central del proyecto: el aislamiento entre restaurantes lo impone
 * PostgreSQL, no la aplicacion.
 *
 * Todas las escrituras pasan por el rol callejon9_app, que no es dueno de las
 * tablas y por lo tanto queda sujeto a las politicas RLS.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Aislamiento multi-tenant impuesto por RLS")
class TenantIsolationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void seedTenants() {
        // La tabla tenants es control plane: no tiene RLS, se puede escribir sin contexto.
        tenantA = jdbcTemplate.queryForObject(
                "INSERT INTO tenants (name, slug) VALUES ('Tenant A', 'tenant-a') RETURNING id",
                UUID.class);
        tenantB = jdbcTemplate.queryForObject(
                "INSERT INTO tenants (name, slug) VALUES ('Tenant B', 'tenant-b') RETURNING id",
                UUID.class);

        insertUserAs(tenantA, "a@demo.com", "Usuario A");
        insertUserAs(tenantB, "b@demo.com", "Usuario B");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        // Sin contexto de tenant, RLS impide borrar users; se borra en cascada
        // eliminando los tenants, que no estan protegidos por RLS.
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN ('tenant-a','tenant-b')");
    }

    private void insertUserAs(UUID tenantId, String email, String name) {
        TenantContext.set(tenantId);
        transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.update("""
                        INSERT INTO users (tenant_id, email, password_hash, full_name, role)
                        VALUES (?, ?, 'x', ?, 'ADMIN')
                        """, tenantId, email, name));
        TenantContext.clear();
    }

    private List<String> readEmailsAs(UUID tenantId) {
        TenantContext.set(tenantId);
        try {
            return transactionTemplate.execute(status ->
                    jdbcTemplate.queryForList("SELECT email FROM users ORDER BY email", String.class));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("cada tenant ve unicamente sus propias filas")
    void eachTenantSeesOnlyItsOwnRows() {
        assertThat(readEmailsAs(tenantA)).containsExactly("a@demo.com");
        assertThat(readEmailsAs(tenantB)).containsExactly("b@demo.com");
    }

    @Test
    @DisplayName("sin tenant activo no se ve ninguna fila")
    void withoutTenantContextNoRowsAreVisible() {
        TenantContext.clear();

        List<String> emails = transactionTemplate.execute(status ->
                jdbcTemplate.queryForList("SELECT email FROM users", String.class));

        assertThat(emails).isEmpty();
    }

    @Test
    @DisplayName("un tenant no puede actualizar filas de otro")
    void aTenantCannotUpdateAnotherTenantsRows() {
        TenantContext.set(tenantA);
        int updated = transactionTemplate.execute(status ->
                jdbcTemplate.update("UPDATE users SET full_name = 'Hackeado' WHERE email = ?",
                        "b@demo.com"));
        TenantContext.clear();

        assertThat(updated).isZero();
        assertThat(readEmailsAs(tenantB)).containsExactly("b@demo.com");
    }

    @Test
    @DisplayName("un tenant no puede borrar filas de otro")
    void aTenantCannotDeleteAnotherTenantsRows() {
        TenantContext.set(tenantA);
        int deleted = transactionTemplate.execute(status ->
                jdbcTemplate.update("DELETE FROM users WHERE email = ?", "b@demo.com"));
        TenantContext.clear();

        assertThat(deleted).isZero();
        assertThat(readEmailsAs(tenantB)).containsExactly("b@demo.com");
    }

    @Test
    @DisplayName("un tenant no puede insertar filas a nombre de otro")
    void aTenantCannotInsertRowsForAnotherTenant() {
        TenantContext.set(tenantA);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.update("""
                        INSERT INTO users (tenant_id, email, password_hash, full_name, role)
                        VALUES (?, 'intruso@demo.com', 'x', 'Intruso', 'ADMIN')
                        """, tenantB)))
                .hasMessageContaining("row-level security");

        TenantContext.clear();
        assertThat(readEmailsAs(tenantB)).containsExactly("b@demo.com");
    }
}
```

- [ ] **Step 2: Correr el test — debe fallar**

```powershell
.\mvnw.cmd test -Dtest=TenantIsolationTest
```
Expected: FALLA. Sin el `TenantAwareTransactionManager`, `app.tenant_id` nunca se fija, así que las políticas ocultan todo y hasta el `INSERT` del `@BeforeEach` es rechazado por `WITH CHECK`. El mensaje típico es `new row violates row-level security policy for table "users"`. Ese fallo confirma que RLS está activo y que falta la pieza que publica el tenant.

- [ ] **Step 3: Escribir el TransactionManager**

Crear `backend/src/main/java/com/callejon9/tenancy/TenantAwareTransactionManager.java`:

```java
package com.callejon9.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Objects;
import java.util.UUID;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publica el tenant activo a la sesion de PostgreSQL al abrir cada transaccion.
 *
 * El tercer argumento de set_config es {@code true}, que hace la variable LOCAL
 * A LA TRANSACCION. Esto es lo que impide que el tenant se filtre a otra
 * peticion cuando HikariCP devuelve la conexion al pool.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    public TenantAwareTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        UUID tenantId = TenantContext.currentOrNull();
        if (tenantId == null) {
            // Sin tenant no se fija la variable: RLS entonces no revela ninguna
            // fila. Fallar cerrado es deliberado.
            return;
        }

        EntityManagerFactory emf = Objects.requireNonNull(getEntityManagerFactory());
        EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(emf);
        EntityManager entityManager = Objects.requireNonNull(holder).getEntityManager();

        entityManager
                .createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, true)")
                .setParameter("tenantId", tenantId.toString())
                .getSingleResult();
    }
}
```

- [ ] **Step 4: Registrarlo como el TransactionManager primario**

Crear `backend/src/main/java/com/callejon9/config/PersistenceConfig.java`:

```java
package com.callejon9.config;

import com.callejon9.tenancy.TenantAwareTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class PersistenceConfig {

    /**
     * Reemplaza el JpaTransactionManager por defecto para que TODA transaccion
     * fije app.tenant_id. Si esta sustitucion no ocurre, RLS oculta todo y la
     * aplicacion deja de funcionar de forma evidente: el fallo es ruidoso, no
     * silencioso.
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new TenantAwareTransactionManager(emf);
    }
}
```

- [ ] **Step 5: Correr la prueba estrella — debe pasar**

```powershell
.\mvnw.cmd test -Dtest=TenantIsolationTest
```
Expected: `Tests run: 5, Failures: 0, Errors: 0`. Los cinco casos —lectura aislada, sin contexto no se ve nada, no se puede actualizar, no se puede borrar, no se puede insertar a nombre de otro— pasan.

- [ ] **Step 6: Correr la suite completa**

```powershell
.\mvnw.cmd test
```
Expected: todos verdes, incluido `contextLoads` y `TenantContextTest`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/callejon9/tenancy/TenantAwareTransactionManager.java backend/src/main/java/com/callejon9/config/PersistenceConfig.java backend/src/test/java/com/callejon9/tenancy/TenantIsolationTest.java
git commit -m "feat: publish active tenant to PostgreSQL per transaction

Replaces the default JpaTransactionManager with one that issues
set_config('app.tenant_id', ..., true) on transaction begin, making the
variable transaction-local so it cannot leak across pooled connections.

TenantIsolationTest proves the guarantee: a tenant cannot read, update,
delete, or insert another tenant's rows, and no rows are visible without
an active tenant."
```

---

## Task 7: Entidades base y del control plane

**Files:**
- Create: `backend/src/main/java/com/callejon9/shared/domain/BaseEntity.java`
- Create: `backend/src/main/java/com/callejon9/shared/domain/TenantScopedEntity.java`
- Create: `backend/src/main/java/com/callejon9/platform/plan/domain/Plan.java`
- Create: `backend/src/main/java/com/callejon9/platform/plan/repository/PlanRepository.java`
- Create: `backend/src/main/java/com/callejon9/platform/tenant/domain/Tenant.java`
- Create: `backend/src/main/java/com/callejon9/platform/tenant/repository/TenantRepository.java`
- Create: `backend/src/main/java/com/callejon9/platform/subscription/domain/Subscription.java`
- Create: `backend/src/main/java/com/callejon9/platform/subscription/domain/SubscriptionStatus.java`
- Create: `backend/src/main/java/com/callejon9/platform/subscription/repository/SubscriptionRepository.java`
- Test: `backend/src/test/java/com/callejon9/platform/ControlPlaneMappingTest.java`

**Interfaces:**
- Consumes: esquema de la Task 2
- Produces:
  - `BaseEntity` con `id: UUID`, `createdAt: Instant`, `updatedAt: Instant`
  - `TenantScopedEntity extends BaseEntity` con `tenantId: UUID`
  - `Plan` con `code: String`, `name: String`, `priceMonthly: BigDecimal`, `maxUsers: int`, `maxTables: int`
  - `Tenant` con `name: String`, `slug: String`, `active: boolean`
  - `Subscription` con `tenantId: UUID`, `planId: UUID`, `status: SubscriptionStatus`, `currentPeriodEnd: Instant`
  - `SubscriptionStatus`: `ACTIVE`, `PAST_DUE`, `CANCELED`, `TRIALING`
  - `PlanRepository.findByCode(String)` → `Optional<Plan>`
  - `TenantRepository.findBySlug(String)` → `Optional<Tenant>`
  - `TenantRepository.existsBySlug(String)` → `boolean`
  - `SubscriptionRepository.findByTenantIdAndStatusIn(UUID, Collection<SubscriptionStatus>)` → `Optional<Subscription>`

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/callejon9/platform/ControlPlaneMappingTest.java`:

```java
package com.callejon9.platform;

import com.callejon9.platform.plan.repository.PlanRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ControlPlaneMappingTest {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void seededPlansAreReadableThroughJpa() {
        var free = planRepository.findByCode("FREE").orElseThrow();

        assertThat(free.getName()).isEqualTo("Gratis");
        assertThat(free.getMaxUsers()).isEqualTo(3);
        assertThat(free.getMaxTables()).isEqualTo(5);
        assertThat(free.getPriceMonthly()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void tenantRoundTripsThroughJpaAndPopulatesAuditFields() {
        Tenant saved = tenantRepository.save(Tenant.builder()
                .name("Mapping Test")
                .slug("mapping-test")
                .active(true)
                .build());

        tenantRepository.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(tenantRepository.existsBySlug("mapping-test")).isTrue();
    }
}
```

- [ ] **Step 2: Correr el test — debe fallar**

```powershell
.\mvnw.cmd test -Dtest=ControlPlaneMappingTest
```
Expected: FALLA en compilación, `package com.callejon9.platform.plan.repository does not exist`.

- [ ] **Step 3: Escribir las clases base**

Crear `backend/src/main/java/com/callejon9/shared/domain/BaseEntity.java`:

```java
package com.callejon9.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
```

Crear `backend/src/main/java/com/callejon9/shared/domain/TenantScopedEntity.java`:

```java
package com.callejon9.shared.domain;

import com.callejon9.tenancy.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Base de toda entidad gobernada por RLS.
 *
 * El tenant se asigna automaticamente al persistir, de modo que ningun servicio
 * tenga que recordarlo. La politica WITH CHECK de PostgreSQL rechazaria de todos
 * modos un tenant incorrecto: esto es comodidad, no la garantia.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @PrePersist
    void assignTenant() {
        if (this.tenantId == null) {
            this.tenantId = TenantContext.require();
        }
    }
}
```

- [ ] **Step 4: Escribir las entidades y repositorios del control plane**

Crear `backend/src/main/java/com/callejon9/platform/plan/domain/Plan.java`:

```java
package com.callejon9.platform.plan.domain;

import com.callejon9.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "price_monthly", nullable = false)
    private BigDecimal priceMonthly;

    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    @Column(name = "max_tables", nullable = false)
    private int maxTables;
}
```

Nota: la columna `features jsonb` existe en la base pero no se mapea todavía, porque ninguna funcionalidad de esta fase la consume. Se agregará cuando haga falta.

Crear `backend/src/main/java/com/callejon9/platform/plan/repository/PlanRepository.java`:

```java
package com.callejon9.platform.plan.repository;

import com.callejon9.platform.plan.domain.Plan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    Optional<Plan> findByCode(String code);
}
```

Crear `backend/src/main/java/com/callejon9/platform/tenant/domain/Tenant.java`:

```java
package com.callejon9.platform.tenant.domain;

import com.callejon9.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false)
    private boolean active;
}
```

Crear `backend/src/main/java/com/callejon9/platform/tenant/repository/TenantRepository.java`:

```java
package com.callejon9.platform.tenant.repository;

import com.callejon9.platform.tenant.domain.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
```

Crear `backend/src/main/java/com/callejon9/platform/subscription/domain/SubscriptionStatus.java`:

```java
package com.callejon9.platform.subscription.domain;

public enum SubscriptionStatus {
    ACTIVE,
    PAST_DUE,
    CANCELED,
    TRIALING
}
```

Crear `backend/src/main/java/com/callejon9/platform/subscription/domain/Subscription.java`:

```java
package com.callejon9.platform.subscription.domain;

import com.callejon9.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "canceled_at")
    private Instant canceledAt;
}
```

Crear `backend/src/main/java/com/callejon9/platform/subscription/repository/SubscriptionRepository.java`:

```java
package com.callejon9.platform.subscription.repository;

import com.callejon9.platform.subscription.domain.Subscription;
import com.callejon9.platform.subscription.domain.SubscriptionStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantIdAndStatusIn(
            UUID tenantId, Collection<SubscriptionStatus> statuses);
}
```

- [ ] **Step 5: Correr el test — debe pasar**

```powershell
.\mvnw.cmd test -Dtest=ControlPlaneMappingTest
```
Expected: `Tests run: 2, Failures: 0, Errors: 0`. Si falla con `Schema-validation: wrong column type`, compara el tipo Java con el SQL de `V1__control_plane.sql`; `ddl-auto: validate` está haciendo su trabajo.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/callejon9/shared backend/src/main/java/com/callejon9/platform backend/src/test/java/com/callejon9/platform
git commit -m "feat: map control plane entities and shared entity bases

TenantScopedEntity assigns the active tenant on persist so services need
not remember it; the WITH CHECK policy remains the actual guarantee."
```

---

## Task 8: Usuario, compatibilidad bcrypt y servicio de JWT

**Files:**
- Create: `backend/src/main/java/com/callejon9/user/domain/User.java`
- Create: `backend/src/main/java/com/callejon9/user/domain/UserRole.java`
- Create: `backend/src/main/java/com/callejon9/user/repository/UserRepository.java`
- Create: `backend/src/main/java/com/callejon9/auth/service/JwtService.java`
- Create: `backend/src/main/java/com/callejon9/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/callejon9/auth/BcryptCompatibilityTest.java`
- Test: `backend/src/test/java/com/callejon9/auth/JwtServiceTest.java`

**Interfaces:**
- Consumes: `TenantScopedEntity` de la Task 7
- Produces:
  - `UserRole`: `SUPER_ADMIN`, `ADMIN`, `WAITER`, `KITCHEN`, `CASHIER`
  - `User` con `email: String`, `passwordHash: String`, `fullName: String`, `role: UserRole`, `active: boolean`, `totpSecret: String`, `totpEnabled: boolean`
  - `UserRepository.findByEmail(String)` → `Optional<User>` (RLS ya limita al tenant activo)
  - `UserRepository.countByTenantId(UUID)` → `long`
  - `JwtService.generateAccessToken(User)` → `String`
  - `JwtService.parse(String token)` → `JwtService.TokenClaims` (record con `userId: UUID`, `tenantId: UUID`, `role: UserRole`)
  - `SecurityConfig` expone un bean `PasswordEncoder` (BCrypt)

- [ ] **Step 1: Generar un hash bcrypt real con el proyecto Flask**

El objetivo es probar que los hashes existentes siguen sirviendo. Genera uno con la misma librería que usa el sistema actual:

```powershell
Set-Location C:\Users\pingu\Restaurante-Callejon-9
python -c "import bcrypt; print(bcrypt.hashpw(b'Demo1234!', bcrypt.gensalt()).decode())"
Set-Location C:\Users\pingu\callejon9-saas\backend
```
Copia el hash que imprime (empieza con `$2b$12$`). Lo pegarás en el siguiente paso donde dice `PYTHON_BCRYPT_HASH`.

- [ ] **Step 2: Escribir el test de compatibilidad bcrypt, que falla**

Crear `backend/src/test/java/com/callejon9/auth/BcryptCompatibilityTest.java`. Sustituye `PYTHON_BCRYPT_HASH` por el hash del paso anterior:

```java
package com.callejon9.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los usuarios del sistema Flask deben poder entrar con su contrasena actual.
 * El hash de este test lo genero la libreria bcrypt de Python.
 */
@DisplayName("Compatibilidad con los hashes bcrypt del sistema Flask")
class BcryptCompatibilityTest {

    private static final String PYTHON_HASH = "PYTHON_BCRYPT_HASH";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void springAcceptsAHashProducedByPythonBcrypt() {
        assertThat(encoder.matches("Demo1234!", PYTHON_HASH)).isTrue();
    }

    @Test
    void springRejectsTheWrongPassword() {
        assertThat(encoder.matches("contrasena-incorrecta", PYTHON_HASH)).isFalse();
    }
}
```

- [ ] **Step 3: Correr el test — debe pasar de inmediato**

```powershell
.\mvnw.cmd test -Dtest=BcryptCompatibilityTest
```
Expected: PASA sin escribir código de producción. Este test no maneja TDD sobre código nuevo: documenta y protege una suposición de la migración. Si **falla**, es un hallazgo importante — significa que la migración de datos tendría que forzar reseteo de contraseñas, y hay que avisarlo antes de seguir.

- [ ] **Step 4: Escribir el test del JwtService, que falla**

Crear `backend/src/test/java/com/callejon9/auth/JwtServiceTest.java`:

```java
package com.callejon9.auth;

import com.callejon9.auth.service.JwtService;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    private User sampleUser(UUID userId, UUID tenantId) {
        User user = User.builder()
                .email("demo@demo.com")
                .passwordHash("x")
                .fullName("Demo")
                .role(UserRole.ADMIN)
                .active(true)
                .build();
        user.setId(userId);
        user.setTenantId(tenantId);
        return user;
    }

    @Test
    void tokenCarriesUserTenantAndRole() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(sampleUser(userId, tenantId));
        JwtService.TokenClaims claims = jwtService.parse(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.tenantId()).isEqualTo(tenantId);
        assertThat(claims.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken(
                sampleUser(UUID.randomUUID(), UUID.randomUUID()));
        String tampered = token.substring(0, token.length() - 4) + "aaaa";

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
```

- [ ] **Step 5: Correr el test — debe fallar**

```powershell
.\mvnw.cmd test -Dtest=JwtServiceTest
```
Expected: FALLA en compilación, `package com.callejon9.auth.service does not exist`.

- [ ] **Step 6: Escribir el rol, la entidad usuario y su repositorio**

Crear `backend/src/main/java/com/callejon9/user/domain/UserRole.java`:

```java
package com.callejon9.user.domain;

public enum UserRole {
    SUPER_ADMIN,
    ADMIN,
    WAITER,
    KITCHEN,
    CASHIER
}
```

Crear `backend/src/main/java/com/callejon9/user/domain/User.java`:

```java
package com.callejon9.user.domain;

import com.callejon9.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends TenantScopedEntity {

    @Column(nullable = false, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;
}
```

Crear `backend/src/main/java/com/callejon9/user/repository/UserRepository.java`:

```java
package com.callejon9.user.repository;

import com.callejon9.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No hace falta filtrar por tenant en las consultas: las politicas RLS de
 * PostgreSQL ya limitan las filas visibles al tenant activo. Es precisamente
 * la ventaja de mover el aislamiento al motor.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    long countByTenantId(UUID tenantId);
}
```

- [ ] **Step 7: Escribir el JwtService**

Crear `backend/src/main/java/com/callejon9/auth/service/JwtService.java`:

```java
package com.callejon9.auth.service;

import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    /** Claims que viajan en el token. */
    public record TokenClaims(UUID userId, UUID tenantId, UserRole role) {
    }

    private static final String CLAIM_TENANT = "tid";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_TENANT, user.getTenantId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new TokenClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get(CLAIM_TENANT, String.class)),
                UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }
}
```

- [ ] **Step 8: Escribir la configuración de seguridad mínima**

Crear `backend/src/main/java/com/callejon9/config/SecurityConfig.java`. En esta tarea solo aporta el `PasswordEncoder` y deja los endpoints abiertos; la cadena de filtros se completa en la Task 9.

```java
package com.callejon9.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /** BCrypt acepta los prefijos $2a$, $2b$ y $2y$, por lo que los hashes
     *  generados por la libreria bcrypt de Python son validos sin conversion. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
```

- [ ] **Step 9: Correr los tests — deben pasar**

```powershell
.\mvnw.cmd test -Dtest=JwtServiceTest+BcryptCompatibilityTest
```
Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 10: Correr la suite completa**

```powershell
.\mvnw.cmd test
```
Expected: todo verde. `ddl-auto: validate` confirma además que el mapeo de `User` coincide con la tabla.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/callejon9/user backend/src/main/java/com/callejon9/auth backend/src/main/java/com/callejon9/config/SecurityConfig.java backend/src/test/java/com/callejon9/auth
git commit -m "feat: add user entity, JWT service and bcrypt-compatible encoder

BcryptCompatibilityTest pins the migration assumption that hashes produced
by Python's bcrypt validate under Spring's BCryptPasswordEncoder, so users
keep their existing passwords."
```

---

## Task 9: TenantFilter y cadena de seguridad por JWT

**Files:**
- Create: `backend/src/main/java/com/callejon9/tenancy/TenantFilter.java`
- Modify: `backend/src/main/java/com/callejon9/config/SecurityConfig.java`
- Create: `backend/src/main/java/com/callejon9/shared/error/BusinessRuleException.java`
- Create: `backend/src/main/java/com/callejon9/shared/error/ResourceNotFoundException.java`
- Create: `backend/src/main/java/com/callejon9/shared/error/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/callejon9/tenancy/TenantFilterTest.java`

**Interfaces:**
- Consumes: `JwtService.parse(String)` de la Task 8; `TenantContext` de la Task 5
- Produces: `TenantFilter extends OncePerRequestFilter`. Tras él, toda petición autenticada tiene `TenantContext` poblado y un `Authentication` con autoridad `ROLE_<UserRole>`. `GlobalExceptionHandler` traduce excepciones a `ProblemDetail` (RFC 7807).

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/callejon9/tenancy/TenantFilterTest.java`:

```java
package com.callejon9.tenancy;

import com.callejon9.auth.service.JwtService;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.Cookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String tokenFor(UserRole role) {
        User user = User.builder()
                .email("demo@demo.com").passwordHash("x").fullName("Demo")
                .role(role).active(true).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        return jwtService.generateAccessToken(user);
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/platform/plans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithInvalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/platform/plans")
                        .cookie(new Cookie("access_token", "not-a-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminIsForbiddenFromPlatformEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/platform/plans")
                        .cookie(new Cookie("access_token", tokenFor(UserRole.ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed());
    }
}
```

Nota sobre el último caso: `GET` a un endpoint que solo acepta `POST` devuelve `405`, no `401`. Eso demuestra que la petición **atravesó** la seguridad y llegó al router, que es justo lo que se quiere verificar.

- [ ] **Step 2: Correr el test — debe fallar**

```powershell
.\mvnw.cmd test -Dtest=TenantFilterTest
```
Expected: FALLA. Con la `SecurityConfig` actual (`permitAll`), el primer caso devuelve `404` en lugar de `401`.

- [ ] **Step 3: Escribir el TenantFilter**

Crear `backend/src/main/java/com/callejon9/tenancy/TenantFilter.java`:

```java
package com.callejon9.tenancy;

import com.callejon9.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Traduce el JWT de la peticion a un Authentication de Spring Security y al
 * TenantContext. Es la unica pieza que decide cual es el tenant de la peticion.
 *
 * Siempre limpia el TenantContext en el finally: si el ThreadLocal sobreviviera
 * al final de la peticion, el siguiente uso de ese hilo del pool heredaria el
 * tenant anterior.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtService jwtService;

    public TenantFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        try {
            readToken(request).ifPresent(token -> authenticate(token, request));
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            JwtService.TokenClaims claims = jwtService.parse(token);

            TenantContext.set(claims.tenantId());

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()));
            var authentication = new UsernamePasswordAuthenticationToken(
                    claims.userId(), null, authorities);
            authentication.setDetails(request.getRequestURI());

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException tokenIsNotUsable) {
            // Token invalido, expirado o manipulado: la peticion sigue anonima y
            // la cadena de autorizacion la rechaza con 401.
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<String> readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> ACCESS_TOKEN_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }
}
```

- [ ] **Step 4: Completar la SecurityConfig**

Reemplazar el método `filterChain` de `backend/src/main/java/com/callejon9/config/SecurityConfig.java` y añadir los imports necesarios. El bean `passwordEncoder` queda igual:

```java
package com.callejon9.config;

import com.callejon9.tenancy.TenantFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final TenantFilter tenantFilter;

    public SecurityConfig(TenantFilter tenantFilter) {
        this.tenantFilter = tenantFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // La API es stateless y se autentica con una cookie httpOnly de
                // SameSite=Strict, que ya impide el envio cross-site.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/api/v1/signup").permitAll()
                        .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**")
                            .permitAll()
                        .requestMatchers("/api/v1/platform/**").hasRole("SUPER_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

- [ ] **Step 5: Escribir las excepciones de dominio y el handler global**

Crear `backend/src/main/java/com/callejon9/shared/error/BusinessRuleException.java`:

```java
package com.callejon9.shared.error;

/** Una regla de negocio impide completar la operacion. Se traduce a HTTP 409. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
```

Crear `backend/src/main/java/com/callejon9/shared/error/ResourceNotFoundException.java`:

```java
package com.callejon9.shared.error;

/** El recurso solicitado no existe en el tenant activo. Se traduce a HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

Crear `backend/src/main/java/com/callejon9/shared/error/GlobalExceptionHandler.java`:

```java
package com.callejon9.shared.error;

import com.callejon9.tenancy.NoTenantContextException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de la aplicacion a ProblemDetail (RFC 7807).
 *
 * Reemplaza el patron del sistema Flask, donde un try/except con print()
 * devolvia None o lista vacia y hacia indistinguible un fallo de base de datos
 * de un resultado vacio.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidationError(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La solicitud contiene campos invalidos.");
        problem.setTitle("Validacion fallida");

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", fieldErrors);

        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail onNotFound(ResourceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Recurso no encontrado");
        return problem;
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail onBusinessRule(BusinessRuleException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Regla de negocio");
        return problem;
    }

    @ExceptionHandler(NoTenantContextException.class)
    ProblemDetail onMissingTenant(NoTenantContextException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Sin restaurante activo");
        return problem;
    }
}
```

- [ ] **Step 6: Correr el test — debe pasar**

```powershell
.\mvnw.cmd test -Dtest=TenantFilterTest
```
Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/callejon9/tenancy/TenantFilter.java backend/src/main/java/com/callejon9/config/SecurityConfig.java backend/src/main/java/com/callejon9/shared/error backend/src/test/java/com/callejon9/tenancy/TenantFilterTest.java
git commit -m "feat: authenticate requests by JWT cookie and bind the tenant

TenantFilter translates the token into both a Spring Security
Authentication and the TenantContext, clearing the ThreadLocal in a
finally block so a pooled thread cannot inherit the previous tenant.

Adds RFC 7807 error responses, replacing the Flask pattern where a
database failure was indistinguishable from an empty result."
```

---

## Task 10: Onboarding de tenants y límites de plan

**Files:**
- Create: `backend/src/main/java/com/callejon9/platform/tenant/service/TenantOnboardingService.java`
- Create: `backend/src/main/java/com/callejon9/platform/tenant/service/PlanLimitService.java`
- Create: `backend/src/main/java/com/callejon9/platform/tenant/web/SignupController.java`
- Create: `backend/src/main/java/com/callejon9/platform/tenant/web/dto/SignupRequest.java`
- Create: `backend/src/main/java/com/callejon9/platform/tenant/web/dto/SignupResponse.java`
- Create: `backend/src/main/java/com/callejon9/platform/plan/web/PlanController.java`
- Test: `backend/src/test/java/com/callejon9/platform/TenantOnboardingServiceTest.java`

**Interfaces:**
- Consumes: `TenantRepository`, `PlanRepository`, `SubscriptionRepository` (Task 7); `UserRepository` (Task 8); `PasswordEncoder` (Task 8); `TenantContext` (Task 5)
- Produces:
  - `TenantOnboardingService.onboard(String restaurantName, String slug, String adminEmail, String adminFullName, String rawPassword, String planCode)` → `Tenant`
  - `PlanLimitService.assertCanAddUser(UUID tenantId)` → `void`, lanza `BusinessRuleException` al exceder
  - `POST /api/v1/signup` → `201` con `SignupResponse(tenantId, slug, adminEmail)`
  - `GET /api/v1/platform/plans` → lista de planes, solo `SUPER_ADMIN`

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/callejon9/platform/TenantOnboardingServiceTest.java`:

```java
package com.callejon9.platform;

import com.callejon9.platform.subscription.domain.SubscriptionStatus;
import com.callejon9.platform.subscription.repository.SubscriptionRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.PlanLimitService;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Onboarding de un restaurante nuevo")
class TenantOnboardingServiceTest {

    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private PlanLimitService planLimitService;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug LIKE 'onboarding-%'");
    }

    @Test
    void createsTenantAdminUserAndActiveSubscription() {
        Tenant tenant = onboardingService.onboard(
                "Restaurante Onboarding", "onboarding-uno",
                "admin@onboarding.com", "Admin Uno", "Secreto123!", "FREE");

        assertThat(tenant.getId()).isNotNull();
        assertThat(tenant.isActive()).isTrue();

        var subscription = subscriptionRepository
                .findByTenantIdAndStatusIn(tenant.getId(), List.of(SubscriptionStatus.ACTIVE))
                .orElseThrow();
        assertThat(subscription.getPlanId()).isNotNull();

        TenantContext.set(tenant.getId());
        var admin = transactionTemplate.execute(status ->
                userRepository.findByEmail("admin@onboarding.com").orElseThrow());

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getTenantId()).isEqualTo(tenant.getId());
        assertThat(passwordEncoder.matches("Secreto123!", admin.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsADuplicateSlug() {
        onboardingService.onboard("Primero", "onboarding-dup",
                "a@onboarding.com", "A", "Secreto123!", "FREE");

        assertThatThrownBy(() -> onboardingService.onboard("Segundo", "onboarding-dup",
                "b@onboarding.com", "B", "Secreto123!", "FREE"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("onboarding-dup");
    }

    @Test
    void rejectsAnUnknownPlanCode() {
        assertThatThrownBy(() -> onboardingService.onboard("Sin plan", "onboarding-noplan",
                "c@onboarding.com", "C", "Secreto123!", "PLAN_INEXISTENTE"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("el plan FREE topa en 3 usuarios")
    void enforcesTheUserLimitOfThePlan() {
        Tenant tenant = onboardingService.onboard(
                "Con limite", "onboarding-limite",
                "admin@limite.com", "Admin", "Secreto123!", "FREE");

        TenantContext.set(tenant.getId());
        // El onboarding ya creo 1 usuario; el plan FREE permite 3.
        transactionTemplate.executeWithoutResult(status -> {
            planLimitService.assertCanAddUser(tenant.getId());
            jdbcTemplate.update("""
                    INSERT INTO users (tenant_id, email, password_hash, full_name, role)
                    VALUES (?, 'u2@limite.com', 'x', 'U2', 'WAITER'),
                           (?, 'u3@limite.com', 'x', 'U3', 'WAITER')
                    """, tenant.getId(), tenant.getId());
        });

        assertThatThrownBy(() -> planLimitService.assertCanAddUser(tenant.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite");
    }
}
```

- [ ] **Step 2: Correr el test — debe fallar**

```powershell
.\mvnw.cmd test -Dtest=TenantOnboardingServiceTest
```
Expected: FALLA en compilación, `package com.callejon9.platform.tenant.service does not exist`.

- [ ] **Step 3: Escribir el TenantOnboardingService**

Crear `backend/src/main/java/com/callejon9/platform/tenant/service/TenantOnboardingService.java`:

```java
package com.callejon9.platform.tenant.service;

import com.callejon9.platform.plan.domain.Plan;
import com.callejon9.platform.plan.repository.PlanRepository;
import com.callejon9.platform.subscription.domain.Subscription;
import com.callejon9.platform.subscription.domain.SubscriptionStatus;
import com.callejon9.platform.subscription.repository.SubscriptionRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Da de alta un restaurante nuevo en el SaaS: crea el tenant, su suscripcion
 * activa y el usuario administrador inicial.
 *
 * Todo ocurre en una sola transaccion. El usuario administrador se crea DESPUES
 * de fijar el TenantContext, porque la politica WITH CHECK de PostgreSQL exige
 * que el tenant_id de la fila coincida con el tenant de la sesion.
 */
@Service
public class TenantOnboardingService {

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TenantOnboardingService(
            TenantRepository tenantRepository,
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Tenant onboard(
            String restaurantName,
            String slug,
            String adminEmail,
            String adminFullName,
            String rawPassword,
            String planCode) {

        if (tenantRepository.existsBySlug(slug)) {
            throw new BusinessRuleException(
                    "Ya existe un restaurante con el identificador '" + slug + "'.");
        }

        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new BusinessRuleException(
                        "El plan '" + planCode + "' no existe."));

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(restaurantName)
                .slug(slug)
                .active(true)
                .build());

        Instant now = Instant.now();
        subscriptionRepository.save(Subscription.builder()
                .tenantId(tenant.getId())
                .planId(plan.getId())
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(now)
                .currentPeriodEnd(now.plus(30, ChronoUnit.DAYS))
                .build());

        // A partir de aqui se escribe en tablas con RLS: hace falta el tenant activo.
        TenantContext.set(tenant.getId());
        userRepository.save(User.builder()
                .tenantId(tenant.getId())
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(adminFullName)
                .role(UserRole.ADMIN)
                .active(true)
                .totpEnabled(false)
                .build());

        return tenant;
    }
}
```

**Advertencia de implementación**: el `TenantContext.set()` a mitad de la transacción no cambia `app.tenant_id` en Postgres, porque `TenantAwareTransactionManager` lo fija al **abrir** la transacción. Si el `INSERT` del usuario falla con `row-level security policy`, la causa es esa. La solución es fijar el contexto **antes** de abrir la transacción: el controlador de la Task 10 llama a `onboard`, así que mueve el `TenantContext.set` fuera de este método creando el tenant y la suscripción en una transacción, y el usuario administrador en una segunda. Verifícalo con el test antes de dar la tarea por buena.

- [ ] **Step 4: Escribir el PlanLimitService**

Crear `backend/src/main/java/com/callejon9/platform/tenant/service/PlanLimitService.java`:

```java
package com.callejon9.platform.tenant.service;

import com.callejon9.platform.plan.domain.Plan;
import com.callejon9.platform.plan.repository.PlanRepository;
import com.callejon9.platform.subscription.domain.SubscriptionStatus;
import com.callejon9.platform.subscription.repository.SubscriptionRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hace que los planes signifiquen algo: sin esto serian filas decorativas. */
@Service
public class PlanLimitService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;

    public PlanLimitService(
            SubscriptionRepository subscriptionRepository,
            PlanRepository planRepository,
            UserRepository userRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public void assertCanAddUser(UUID tenantId) {
        Plan plan = activePlanOf(tenantId);
        long currentUsers = userRepository.countByTenantId(tenantId);

        if (currentUsers >= plan.getMaxUsers()) {
            throw new BusinessRuleException(
                    "Alcanzaste el limite de " + plan.getMaxUsers()
                            + " usuarios del plan " + plan.getCode() + ".");
        }
    }

    private Plan activePlanOf(UUID tenantId) {
        var subscription = subscriptionRepository
                .findByTenantIdAndStatusIn(tenantId,
                        List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
                .orElseThrow(() -> new BusinessRuleException(
                        "El restaurante no tiene una suscripcion activa."));

        return planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new BusinessRuleException(
                        "La suscripcion apunta a un plan inexistente."));
    }
}
```

- [ ] **Step 5: Escribir los DTOs y el controlador de signup**

Crear `backend/src/main/java/com/callejon9/platform/tenant/web/dto/SignupRequest.java`:

```java
package com.callejon9.platform.tenant.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(max = 160) String restaurantName,

        @NotBlank
        @Pattern(regexp = "^[a-z0-9-]{3,80}$",
                 message = "Solo minusculas, numeros y guiones, entre 3 y 80 caracteres.")
        String slug,

        @NotBlank @Email @Size(max = 180) String adminEmail,
        @NotBlank @Size(max = 160) String adminFullName,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank String planCode) {
}
```

Crear `backend/src/main/java/com/callejon9/platform/tenant/web/dto/SignupResponse.java`:

```java
package com.callejon9.platform.tenant.web.dto;

import java.util.UUID;

public record SignupResponse(UUID tenantId, String slug, String adminEmail) {
}
```

Crear `backend/src/main/java/com/callejon9/platform/tenant/web/SignupController.java`:

```java
package com.callejon9.platform.tenant.web;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.platform.tenant.web.dto.SignupRequest;
import com.callejon9.platform.tenant.web.dto.SignupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Alta publica de un restaurante en el SaaS. */
@RestController
@RequestMapping("/api/v1/signup")
public class SignupController {

    private final TenantOnboardingService onboardingService;

    public SignupController(TenantOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        Tenant tenant = onboardingService.onboard(
                request.restaurantName(),
                request.slug(),
                request.adminEmail(),
                request.adminFullName(),
                request.password(),
                request.planCode());

        return new SignupResponse(tenant.getId(), tenant.getSlug(), request.adminEmail());
    }
}
```

- [ ] **Step 6: Escribir el controlador de planes**

Crear `backend/src/main/java/com/callejon9/platform/plan/web/PlanController.java`:

```java
package com.callejon9.platform.plan.web;

import com.callejon9.platform.plan.domain.Plan;
import com.callejon9.platform.plan.repository.PlanRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo SUPER_ADMIN: la regla vive en SecurityConfig, ruta /api/v1/platform/**. */
@RestController
@RequestMapping("/api/v1/platform/plans")
public class PlanController {

    public record PlanView(String code, String name, BigDecimal priceMonthly,
                           int maxUsers, int maxTables) {
    }

    private final PlanRepository planRepository;

    public PlanController(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @GetMapping
    public List<PlanView> list() {
        return planRepository.findAll().stream()
                .map(this::toView)
                .toList();
    }

    private PlanView toView(Plan plan) {
        return new PlanView(plan.getCode(), plan.getName(), plan.getPriceMonthly(),
                plan.getMaxUsers(), plan.getMaxTables());
    }
}
```

- [ ] **Step 7: Correr el test — debe pasar**

```powershell
.\mvnw.cmd test -Dtest=TenantOnboardingServiceTest
```
Expected: `Tests run: 4, Failures: 0, Errors: 0`. Si el primer caso falla con `row-level security policy for table "users"`, aplica la corrección descrita en la advertencia del Step 3.

- [ ] **Step 8: Probar el signup de punta a punta**

```powershell
.\mvnw.cmd spring-boot:run
```
En otra terminal:
```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/signup -ContentType 'application/json' -Body '{"restaurantName":"Callejon 9","slug":"callejon9","adminEmail":"admin@callejon9.com","adminFullName":"Ludwin","password":"Secreto123!","planCode":"PREMIUM"}'
```
Expected: respuesta `201` con `tenantId`, `slug` y `adminEmail`. Repetir la misma llamada debe devolver `409` con un `ProblemDetail` cuyo `detail` menciona el slug duplicado. Detén la aplicación con `Ctrl+C`.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/callejon9/platform backend/src/test/java/com/callejon9/platform/TenantOnboardingServiceTest.java
git commit -m "feat: onboard tenants and enforce plan limits

Signing up creates the tenant, an active subscription and the initial
admin user. PlanLimitService rejects users beyond the plan allowance with
409, so plans are enforced rather than decorative."
```

---

## Task 11: Login con 2FA TOTP, refresh de sesión, OpenAPI y CI

**Files:**
- Create: `backend/src/main/java/com/callejon9/auth/domain/RefreshToken.java`
- Create: `backend/src/main/java/com/callejon9/auth/repository/RefreshTokenRepository.java`
- Create: `backend/src/main/java/com/callejon9/auth/service/TotpService.java`
- Create: `backend/src/main/java/com/callejon9/auth/service/AuthService.java`
- Create: `backend/src/main/java/com/callejon9/auth/web/AuthController.java`
- Create: `backend/src/main/java/com/callejon9/auth/web/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/callejon9/auth/web/dto/LoginResponse.java`
- Create: `backend/src/main/java/com/callejon9/config/OpenApiConfig.java`
- Create: `.github/workflows/ci.yml`
- Create: `scripts/run-dev.ps1`
- Test: `backend/src/test/java/com/callejon9/auth/AuthControllerTest.java`

**Interfaces:**
- Consumes: `UserRepository`, `JwtService`, `PasswordEncoder` (Task 8); `TenantRepository` (Task 7)
- Produces:
  - `POST /api/v1/auth/login` con `LoginRequest(slug, email, password)` → `200` + cookie `access_token`, o `202` con `challengeToken` si el usuario tiene TOTP
  - `POST /api/v1/auth/2fa` con `TwoFactorRequest(challengeToken, code)` → `200` + cookie
  - `POST /api/v1/auth/logout` → `204`, revoca el refresh token
  - `TotpService.verify(String secret, String code)` → `boolean`

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/callejon9/auth/AuthControllerTest.java`:

```java
package com.callejon9.auth;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Login")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Tenant tenant;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Login Test", "login-test",
                "admin@login.com", "Admin", "Secreto123!", "FREE");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'login-test'");
    }

    @Test
    void validCredentialsReturnAnHttpOnlyCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"login-test","email":"admin@login.com","password":"Secreto123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"login-test","email":"admin@login.com","password":"incorrecta"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un email valido de OTRO restaurante no sirve para entrar")
    void credentialsFromAnotherTenantAreRejected() throws Exception {
        onboardingService.onboard("Otro", "login-otro",
                "admin@otro.com", "Otro", "Secreto123!", "FREE");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"login-test","email":"admin@otro.com","password":"Secreto123!"}
                                """))
                .andExpect(status().isUnauthorized());

        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'login-otro'");
    }

    @Test
    void unknownTenantSlugIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"no-existe","email":"admin@login.com","password":"Secreto123!"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
```

El tercer caso es el más valioso: demuestra que el aislamiento también cubre la autenticación. Sin resolver el tenant antes de buscar el usuario, un correo de otro restaurante podría autenticar.

- [ ] **Step 2: Correr el test — debe fallar**

```powershell
.\mvnw.cmd test -Dtest=AuthControllerTest
```
Expected: FALLA con `404` en lugar de `200`: el endpoint no existe.

- [ ] **Step 3: Escribir la entidad y el repositorio de refresh tokens**

Crear `backend/src/main/java/com/callejon9/auth/domain/RefreshToken.java`:

```java
package com.callejon9.auth.domain;

import com.callejon9.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Se guarda el hash, nunca el token en claro. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 100)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
```

Nota: `V2__data_plane.sql` ya incluye `updated_at` en `refresh_tokens`, así que no hace falta ninguna migración adicional para que el mapeo heredado de `TenantScopedEntity` valide.

Crear `backend/src/main/java/com/callejon9/auth/repository/RefreshTokenRepository.java`:

```java
package com.callejon9.auth.repository;

import com.callejon9.auth.domain.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
```

- [ ] **Step 4: Escribir el TotpService**

Crear `backend/src/main/java/com/callejon9/auth/service/TotpService.java`:

```java
package com.callejon9.auth.service;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;

/** Reemplaza pyotp. Los secretos base32 del sistema Flask siguen siendo validos. */
@Service
public class TotpService {

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier codeVerifier =
            new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public boolean verify(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }
}
```

- [ ] **Step 5: Escribir los DTOs de autenticación**

Crear `backend/src/main/java/com/callejon9/auth/web/dto/LoginRequest.java`:

```java
package com.callejon9.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * El slug identifica el restaurante. Es obligatorio porque el correo solo es
 * unico dentro de un tenant: sin el, no se sabria en que restaurante buscar.
 */
public record LoginRequest(
        @NotBlank String slug,
        @NotBlank String email,
        @NotBlank String password) {
}
```

Crear `backend/src/main/java/com/callejon9/auth/web/dto/LoginResponse.java`:

```java
package com.callejon9.auth.web.dto;

import java.util.UUID;

public record LoginResponse(UUID userId, String fullName, String role, boolean twoFactorRequired) {
}
```

- [ ] **Step 6: Escribir el AuthService**

Crear `backend/src/main/java/com/callejon9/auth/service/AuthService.java`:

```java
package com.callejon9.auth.service;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Resultado de una autenticacion exitosa. */
    public record AuthenticatedUser(User user, String accessToken) {
    }

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Resuelve el tenant por slug ANTES de buscar al usuario. Ese orden es lo que
     * impide que un correo valido de otro restaurante sirva para entrar.
     */
    public AuthenticatedUser authenticate(String slug, String email, String rawPassword) {
        Tenant tenant = findActiveTenant(slug);

        TenantContext.set(tenant.getId());
        try {
            User user = loadUser(email);

            if (!user.isActive() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
                throw new BadCredentialsException("Credenciales invalidas.");
            }

            return new AuthenticatedUser(user, jwtService.generateAccessToken(user));
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional(readOnly = true)
    protected User loadUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas."));
    }

    private Tenant findActiveTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .filter(Tenant::isActive)
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas."));
    }
}
```

Advertencia: `loadUser` es `protected` y llamado desde dentro de la misma clase, así que el proxy de Spring **no aplica** `@Transactional`. Debe extraerse a un componente aparte, o anotarse el método público `authenticate` con `@Transactional`. Confírmalo con el test: si `findByEmail` no ve al usuario, ésta es la causa.

- [ ] **Step 7: Escribir el AuthController**

Crear `backend/src/main/java/com/callejon9/auth/web/AuthController.java`:

```java
package com.callejon9.auth.web;

import com.callejon9.auth.service.AuthService;
import com.callejon9.auth.web.dto.LoginRequest;
import com.callejon9.auth.web.dto.LoginResponse;
import com.callejon9.tenancy.TenantFilter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final long accessTokenMinutes;

    public AuthController(AuthService authService,
                          @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.authService = authService;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var authenticated = authService.authenticate(
                request.slug(), request.email(), request.password());

        ResponseCookie cookie = ResponseCookie.from(
                        TenantFilter.ACCESS_TOKEN_COOKIE, authenticated.accessToken())
                .httpOnly(true)
                .secure(false)          // en produccion: true, detras de HTTPS
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMinutes(accessTokenMinutes))
                .build();

        var user = authenticated.user();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponse(user.getId(), user.getFullName(),
                        user.getRole().name(), false));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cleared = ResponseCookie.from(TenantFilter.ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true).path("/").maxAge(Duration.ZERO).build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Void> onBadCredentials() {
        // Mensaje deliberadamente vacio: no se revela si fallo el correo, la
        // contrasena o el restaurante.
        return ResponseEntity.status(401).build();
    }
}
```

Nota de alcance: el flujo de 2FA (`POST /api/v1/auth/2fa`) y la rotación de refresh tokens quedan preparados por `TotpService` y `RefreshTokenRepository`, pero se cablean en el Plan 2. `LoginResponse.twoFactorRequired` ya existe para que el frontend no cambie de forma cuando se active.

- [ ] **Step 8: Correr el test — debe pasar**

```powershell
.\mvnw.cmd test -Dtest=AuthControllerTest
```
Expected: `Tests run: 4, Failures: 0, Errors: 0`. Presta atención al caso `credentialsFromAnotherTenantAreRejected`: si pasa, el aislamiento cubre también el login.

- [ ] **Step 9: Configurar OpenAPI**

Crear `backend/src/main/java/com/callejon9/config/OpenApiConfig.java`:

```java
package com.callejon9.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI callejon9OpenApi() {
        return new OpenAPI().info(new Info()
                .title("Callejon 9 SaaS API")
                .version("v1")
                .description("API multi-tenant para gestion de restaurantes. "
                        + "El aislamiento entre restaurantes lo impone PostgreSQL "
                        + "mediante Row Level Security."));
    }
}
```

Verificar: arranca la aplicación y abre `http://localhost:8080/swagger-ui.html`. Deben aparecer `/api/v1/signup`, `/api/v1/auth/login` y `/api/v1/platform/plans`.

- [ ] **Step 10: Escribir el script de desarrollo**

Crear `scripts/run-dev.ps1`:

```powershell
# Levanta el backend contra el PostgreSQL local.
# Uso: .\scripts\run-dev.ps1
$ErrorActionPreference = 'Stop'

if (-not $env:DB_APP_PASSWORD)   { $env:DB_APP_PASSWORD   = 'app_dev_pwd' }
if (-not $env:DB_OWNER_PASSWORD) { $env:DB_OWNER_PASSWORD = 'owner_dev_pwd' }

Write-Host 'Iniciando backend en http://localhost:8080 ...' -ForegroundColor Cyan
Set-Location "$PSScriptRoot\..\backend"
.\mvnw.cmd spring-boot:run
```

- [ ] **Step 11: Escribir el workflow de CI**

Crear `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  backend:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_PASSWORD: postgres
        ports: ['5432:5432']
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Create roles and test database
        env:
          PGPASSWORD: postgres
        run: |
          psql -h localhost -U postgres -c "CREATE ROLE callejon9_owner LOGIN PASSWORD 'owner_dev_pwd';"
          psql -h localhost -U postgres -c "CREATE ROLE callejon9_app   LOGIN PASSWORD 'app_dev_pwd';"
          psql -h localhost -U postgres -c "CREATE DATABASE callejon9_test OWNER callejon9_owner;"
          psql -h localhost -U postgres -d callejon9_test -c "GRANT USAGE ON SCHEMA public TO callejon9_app;"

      - name: Run tests
        working-directory: backend
        env:
          DB_APP_PASSWORD: app_dev_pwd
          DB_OWNER_PASSWORD: owner_dev_pwd
          JWT_SECRET: ci-only-secret-at-least-32-bytes-long!!
        run: ./mvnw --batch-mode verify
```

- [ ] **Step 12: Correr la suite completa**

```powershell
.\mvnw.cmd verify
```
Expected: todos los tests verdes. Confirma explícitamente que aparecen `TenantIsolationTest`, `TenantContextTest`, `TenantFilterTest`, `BcryptCompatibilityTest`, `JwtServiceTest`, `ControlPlaneMappingTest`, `TenantOnboardingServiceTest` y `AuthControllerTest`.

- [ ] **Step 13: Commit**

```bash
git add backend/src/main/java/com/callejon9/auth backend/src/main/java/com/callejon9/config/OpenApiConfig.java backend/src/test/java/com/callejon9/auth/AuthControllerTest.java scripts/run-dev.ps1 .github/workflows/ci.yml
git commit -m "feat: add tenant-scoped login, OpenAPI docs and CI pipeline

Login resolves the tenant by slug before looking up the user, so an email
valid in another restaurant cannot authenticate. Adds a Swagger UI and a
GitHub Actions pipeline running the suite against a postgres:16 service
container."
```

---

## Puerta de salida de la Fase 1

Antes de pasar al Plan 2, verificar:

- [ ] `./mvnw verify` pasa con los ocho archivos de test en verde
- [ ] `TenantIsolationTest` cubre los cinco casos: lectura aislada, sin contexto no se ve nada, no se puede actualizar, borrar ni insertar a nombre de otro tenant
- [ ] `POST /api/v1/signup` crea tenant, suscripción activa y administrador; repetirlo devuelve `409`
- [ ] `POST /api/v1/auth/login` devuelve cookie `httpOnly`; un correo de otro tenant devuelve `401`
- [ ] `GET /api/v1/platform/plans` devuelve `403` con rol `ADMIN` y `401` sin token
- [ ] Swagger UI responde en `/swagger-ui.html`
- [ ] `git log --oneline` muestra un commit por tarea, todos en inglés

**Decisión pendiente del usuario antes de publicar el repositorio remoto:** nombre definitivo (propuesto `callejon9-saas`) y visibilidad (público o privado). No crear el repositorio en GitHub sin esa confirmación.

---

## Auto-revisión del plan

**Cobertura del spec.** Las secciones §5.1 (estructura de paquetes), §6.1 y §6.2 (modelo de datos completo), §8.1 (flujo de autenticación, con el 2FA cableado en el Plan 2), §8.2 (los cuatro eslabones del aislamiento), §8.3 (control plane y `PlanLimitService`), §10 (manejo de errores), §11 (testing contra Postgres real y CI con service container), §13 (estructura del repositorio) y §4.3 (prerrequisitos) quedan cubiertas por las Tasks 1 a 11. Fuera de esta fase, por diseño: §9 (tiempo real, Plan 2), §12 (ETL, Plan 5), y el dominio de pedidos y cobro (Planes 2 y 3).

**Placeholders.** El único valor a sustituir es `PYTHON_BCRYPT_HASH` en la Task 8, y el Step 1 de esa tarea da el comando exacto que lo genera. No es un placeholder pendiente sino un dato que debe producirse en la máquina.

**Consistencia de tipos.** `TenantContext` expone `set`, `require`, `currentOrNull` y `clear`, y esos son los nombres usados en las Tasks 6, 7, 9, 10 y 11. `JwtService.TokenClaims(userId, tenantId, role)` se consume con esos mismos nombres en `TenantFilter` y en `JwtServiceTest`. `TenantFilter.ACCESS_TOKEN_COOKIE` se reutiliza en `AuthController`.

**Tres trampas documentadas de forma deliberada**, con su síntoma y su corrección, porque cada una produce un fallo real durante la implementación:
1. Task 10, Step 3 — `TenantContext.set()` a mitad de transacción no cambia `app.tenant_id`, porque el manager lo fija al abrir.
2. Task 11, Step 6 — `@Transactional` en un método `protected` llamado desde la propia clase no lo intercepta el proxy de Spring.
3. Task 11, Step 3 — `refresh_tokens` carece de `updated_at`, que `TenantScopedEntity` da por hecho; se corrige con la migración V5.
