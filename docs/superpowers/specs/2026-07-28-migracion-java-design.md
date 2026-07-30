# Diseño — Migración de Callejón 9 a Java (SaaS multi-tenant)

- **Fecha:** 2026-07-28
- **Entrega:** sábado 2026-08-01 (defensa académica)
- **Repositorio destino:** nuevo monorepo, historia limpia, nombre de trabajo `callejon9-saas`
- **Estado:** diseño aprobado por el usuario

---

## 1. Contexto y punto de partida

El sistema actual es una aplicación **Flask 3.1 + MongoDB**, no Django. Inventario real del código:

| Capa | Tamaño |
|---|---|
| Vistas Jinja2 (87 archivos HTML) | ~31.300 líneas |
| Controllers | ~8.200 líneas |
| Models | ~3.100 líneas |
| Services | ~2.000 líneas |
| Routes | ~1.300 líneas |
| Tests | ~400 líneas |

Dependencias relevantes: `Flask-SocketIO` (78 puntos de emisión), `pymongo`, `bcrypt`, `PyJWT`, `pyotp` + `qrcode` (2FA TOTP), `mercadopago`, `fpdf`, y el stack de ciencia de datos (`scikit-learn`, `pandas`, `numpy`, `statsmodels`, `mlxtend`).

Colecciones Mongo en uso: `usuarios`, `ventas`, `ordenes`, `pedidos`, `comandas`, `platillos`, `productos`, `mesas`, `insumos`, `movimientos_inventario`, `tickets`, `clientes`, `restaurantes`, `notificaciones`.

### 1.1 Estado real del multi-tenancy

Las **bases existen y están bien diseñadas**:

- `utils/tenant_context.py` mantiene el tenant activo con `contextvars`, y falla cerrado con `NoTenantContextError`.
- `models/base_model.py` inyecta `tenant_id` automáticamente en filtros y documentos nuevos.
- `models/restaurante_model.py` es el registro global de tenants, con `ensure_default()` idempotente.

El problema es la **adopción**: de 19 modelos, solo dos extienden `BaseModel` (`inventario_model` y `restaurante_model`). Los modelos del núcleo del negocio — `comanda`, `venta`, `mesa`, `ticket`, `usuarios`, `producto` — acceden a Mongo directamente, sin filtro de tenant. El aislamiento es por lo tanto una convención que depende de que cada modelo recuerde heredar de la clase correcta.

Esta observación es el eje de la migración: la intención de diseño ya es exactamente Row Level Security, implementada a mano en la capa de aplicación. Migrar a PostgreSQL con RLS no cambia la intención, **endurece la garantía** moviéndola de la aplicación al motor.

### 1.2 Deuda técnica identificada (se corrige en la migración)

1. **`mesas.numero` tiene tipos mixtos** (int y str según el documento). `Mesa.find_by_numero` hace `{"numero": {"$in": [int(n), str(n)]}}` para sobrevivirlo.
2. **`comandas` referencia `mesa_numero`, no un id.** No hay integridad referencial: renumerar una mesa huérfana el histórico.
3. **`productos` y `platillos` son dos colecciones para el mismo concepto.**
4. **Errores silenciados.** El patrón `try/except` con `print()` que devuelve `None` o `[]` hace que un fallo de base de datos sea indistinguible de "no hay resultados".
5. **Eventos SocketIO emitidos dentro de la transacción lógica**, por lo que la cocina puede observar estado que después se revierte.
6. **Los topics de SocketIO no validan tenant**: cualquier cliente conectado puede escuchar eventos de cualquier restaurante.

---

## 2. Decisiones tomadas

| Decisión | Elección | Justificación resumida |
|---|---|---|
| Naturaleza de la entrega | Demo / defensa académica | Prioriza happy path sólido, impacto visual y arquitectura defendible |
| Frontend | SPA nueva en Next.js 15 | Decisión explícita del usuario, asumiendo el costo de reconstruir pantallas |
| Base de datos | **PostgreSQL 16** | Ver §3 |
| Estrategia multi-tenant | **Schema compartido + `tenant_id` + RLS** | Aislamiento impuesto por el motor, onboarding de un `INSERT`, 1 sola migración |
| Alcance del sábado | Flujo core de pedido + SaaS multi-tenant real | Ver §7 |
| Idioma | Código en inglés, documentación en español | Convención universal de Java/Spring; docs coherentes con los manuales existentes |

### 2.1 Nota sobre el sustento arquitectónico

La separación Controller → Service → Repository se sustenta como **arquitectura estándar de Spring Boot** y en los principios SRP y separación de preocupaciones. No se cita ninguna norma corporativa: este es un proyecto académico.

---

## 3. PostgreSQL sobre MongoDB: justificación

La elección de "SaaS multi-tenant real" decide la pregunta:

1. **El aislamiento lo garantiza el motor, no el código.** Postgres tiene Row Level Security: la política se define una vez y la base de datos impide físicamente el acceso cruzado, incluso ante un query mal escrito. Mongo no tiene equivalente; un solo `find()` sin filtro de tenant es una fuga de datos entre clientes.
2. **El cobro es una transacción multi-entidad.** Toca `orders`, `sales`, `tickets`, `inventory_movements` y `restaurant_tables` a la vez. En Postgres es un `@Transactional`. En Mongo requiere transacciones multi-documento, que exigen replica set y traen límites propios.
3. **Planes y suscripciones son datos relacionales puros** (tenant → plan → límites), con integridad referencial garantizada por foreign keys.
4. **El modelo de datos actual ya es relacional disfrazado.** Las colecciones son tablas con llaves foráneas, no documentos agregados: se paga el costo de Mongo sin usar su ventaja.
5. **El ecosistema Java vive en SQL.** Spring Data JPA + Hibernate + Flyway es el camino más rápido y mejor documentado.
6. **Flyway produce un artefacto mostrable**: historial de esquema versionado y reproducible. Mongo schemaless no tiene equivalente.

Mongo habría ganado en un solo escenario del proyecto —telemetría de sensores IoT, con escrituras masivas y esquema variable— y ese módulo queda fuera del alcance.

---

## 4. Stack

### 4.1 Backend

| Componente | Elección |
|---|---|
| Lenguaje | Java 21 LTS (virtual threads) |
| Framework | Spring Boot 3.4.13 (ver §4.4) |
| API | Spring Web REST + springdoc-openapi (Swagger UI) |
| Persistencia | Spring Data JPA + Hibernate 6 |
| Base de datos | PostgreSQL 16 |
| Migraciones | Flyway |
| Seguridad | Spring Security + JWT en cookie `httpOnly` |
| 2FA | `dev.samstevens.totp` (reemplaza `pyotp` + `qrcode`) |
| Hashing | `BCryptPasswordEncoder` — **compatible con los hashes bcrypt existentes** |
| Tiempo real | WebSocket + STOMP (reemplaza Flask-SocketIO) |
| Mapeo DTO | MapStruct + Lombok |
| PDF | OpenPDF (reemplaza `fpdf`) |
| Pagos | `com.mercadopago:sdk-java` |
| Rate limiting | Bucket4j (reemplaza `flask-limiter`) |
| Tests | JUnit 5 + AssertJ contra PostgreSQL 16 local |
| Build | Maven Wrapper (`mvnw`) — no requiere Maven instalado |
| Entorno local | PostgreSQL nativo en Windows (sin Docker) |

### 4.2 Frontend

| Componente | Elección |
|---|---|
| Framework | Next.js 15 (App Router) + TypeScript |
| Estilos | Tailwind CSS + shadcn/ui |
| Datos | TanStack Query |
| Gráficas | Recharts |
| Tiempo real | `@stomp/stompjs` |

### 4.3 Prerrequisitos del entorno

Inventario verificado en la máquina de desarrollo el 2026-07-28:

| Herramienta | Estado inicial | Acción |
|---|---|---|
| Node.js 24.13 / npm 11.8 | Presente | Ninguna |
| git 2.52 / gh 2.93 (autenticado) | Presente | Ninguna |
| JDK | 17.0.12 | **Instalar JDK 21 LTS** (`winget install EclipseAdoptium.Temurin.21.JDK`) |
| Maven | Ausente | No se instala: se usa el Maven Wrapper (`mvnw`) |
| PostgreSQL | **18 ya instalado**, ocupando el puerto 5432 | Instalar PostgreSQL 16, que toma el **5433**. Ver abajo |
| Docker | Ausente | No se instala. Ver §11 para el impacto en la estrategia de tests |

Ambas instalaciones requieren permisos de administrador y las ejecuta el usuario.

**Convivencia de dos clusters.** La máquina ya tenía un PostgreSQL 18 sirviendo en el 5432, por lo que el instalador del 16 tomó el siguiente puerto libre. Ambos servicios quedan corriendo:

| Servicio | Versión | Puerto | Uso |
|---|---|---|---|
| `postgresql-x64-18` | 18 | 5432 | Preexistente, ajeno a este proyecto. No se modifica |
| `postgresql-x64-16` | 16 | **5433** | Este proyecto |

Los dos datasources apuntan explícitamente al **5433** en lugar de confiar en el puerto por defecto. Depender del default habría conectado silenciosamente al cluster equivocado.

**Recuperación del superusuario.** `winget` ignoró el flag `--interactive` y ejecutó la instalación desatendida, dejando la contraseña de `postgres` en un valor desconocido. Se recuperó poniendo `pg_hba.conf` en `trust` de forma temporal, fijando la contraseña con `ALTER USER`, y restaurando el archivo original desde un respaldo. La ventana de `trust` duró segundos y solo afectó conexiones locales al 5433.

**Verificación obligatoria antes de construir.** Los dos roles deben tener `rolsuper = f` y `rolbypassrls = f`; de lo contrario el aislamiento sería decorativo. Confirmado el 2026-07-29:

```
callejon9_app    rolsuper=f  rolbypassrls=f  rolcanlogin=t
callejon9_owner  rolsuper=f  rolbypassrls=f  rolcanlogin=t
```

### 4.4 Por qué Spring Boot 3.4 y no 4.x

Spring Initializr ya no ofrece la línea 3.4; sus versiones disponibles son `4.1.0.RELEASE` (default) y `4.0.7.RELEASE`. El scaffold inicial se generó con 4.1.0 y se repintó el `parent` a **3.4.13**, el último parche de la línea 3.4 disponible en Maven Central.

La razón es concreta, no conservadurismo:

1. **springdoc-openapi no tiene versión para Spring Framework 7.** Su último release es `2.8.6`, que targetea Spring Framework 6, es decir Spring Boot 3.x. Con Boot 4 se perdería Swagger UI, que es un entregable del proyecto (§15.6).
2. **Spring Security 7 cambia la API de configuración.** Migrar la cadena de filtros y la autorización por método a la nueva API es trabajo de alcance desconocido, y el calendario no lo admite.
3. **Los starters se renombraron** en la 4.x: `spring-boot-starter-web` pasó a `-webmvc`, y `spring-boot-starter-test` se dividió en slices por módulo (`-webmvc-test`, `-data-jpa-test`, …).

El costo aceptado es usar una versión que no es la vigente. Para el objetivo del proyecto —demostrar aislamiento multi-tenant impuesto por el motor— la versión del framework es irrelevante, y el riesgo de calendario sí es material.

---

## 5. Arquitectura

```
┌─────────────────────────────┐
│  Next.js 15 SPA             │  ~20 pantallas
└──────────┬──────────────────┘
           │ REST /api/v1  (JWT en cookie httpOnly)
           │ STOMP /ws     (cocina en vivo)
┌──────────▼──────────────────┐
│  Spring Boot 3.4 / Java 21  │
│    Controller  → solo HTTP y orquestación
│    Service     → negocio, cálculos, @Transactional
│    Repository  → acceso a datos (Spring Data JPA)
│  TenantFilter → TenantContext (tenant desde el JWT)
└──────────┬──────────────────┘
           │ set_config('app.tenant_id', …, true) por transacción
┌──────────▼──────────────────┐
│  PostgreSQL 16 + RLS        │
└─────────────────────────────┘
```

Correspondencia de responsabilidades: `Controller` maneja exclusivamente protocolo HTTP; `DTO` + Bean Validation hacen validación estricta y transformación; `Service` centraliza la lógica de negocio; `Entity` contiene el comportamiento inherente de la entidad.

### 5.1 Estructura de paquetes (por feature)

```
com.callejon9
├── config/       SecurityConfig, WebSocketConfig, OpenApiConfig
├── tenancy/      TenantContext, TenantFilter, TenantAwareTransactionManager
├── shared/       BaseEntity, ApiResponse, GlobalExceptionHandler
│
├── platform/     ── CONTROL PLANE (sin tenant_id) ──
│   ├── tenant/       registro de restaurantes + onboarding
│   ├── plan/         planes y límites
│   └── subscription/ suscripciones
│
├── auth/         login, JWT, refresh, TOTP 2FA
├── user/         empleados y roles
├── table/        mesas
├── catalog/      categorías + productos
├── order/        comandas + items + flujo de cocina
├── kitchen/      tablero en vivo
├── sale/         cobro y propinas
├── payment/      MercadoPago
├── ticket/       ticket + PDF
└── inventory/    insumos y movimientos
```

Cada feature contiene `web/` (controller + DTOs), `domain/` (entity + enums), `service/` y `repository/`. Ninguna clase concentra más de una razón para cambiar, y ningún archivo debe crecer al punto de no poder leerse de una sentada.

### 5.2 Estructura del frontend

```
app/
├── (auth)/       login, 2fa
├── signup/       onboarding de tenant
├── (app)/
│   ├── waiter/   tables, order/[id]
│   ├── kitchen/  board
│   ├── cashier/  checkout/[orderId]
│   └── admin/    dashboard, users, products, tables, inventory
└── (platform)/   super-admin: tenants, plans, subscriptions
```

---

## 6. Modelo de datos

### 6.1 Control plane (sin `tenant_id`, sin RLS)

| Tabla | Campos clave |
|---|---|
| `plans` | `code`, `name`, `price_monthly`, `max_users`, `max_tables`, `features jsonb` |
| `tenants` | `name`, `slug` (único), `active`, `created_at` |
| `subscriptions` | `tenant_id`, `plan_id`, `status`, `current_period_end` |

### 6.2 Plano de datos (14 tablas con `tenant_id` + RLS)

| Tabla | Origen Mongo | Notas de migración |
|---|---|---|
| `users` | `usuarios` | `unique(tenant_id, email)`; conserva hashes bcrypt |
| `refresh_tokens` | `flask_session` | reemplaza sesiones en disco |
| `restaurant_tables` | `mesas` | `unique(tenant_id, number)`; `number` pasa a `int` (corrige tipos mixtos) |
| `categories` | campo `categoria` | se normaliza |
| `products` | `productos` + `platillos` | dos colecciones fusionadas |
| `orders` | `comandas` | `folio`, `status`, FK real a mesa |
| `order_items` | array embebido | tabla propia con `kitchen_status` |
| `sales` | `ventas` | `status`, `payment_method`, `subtotal`, `tip`, `total` |
| `payments` | `pago_movil` | `provider_payment_id`, `raw_response jsonb` |
| `tickets` | `tickets` | `items_snapshot jsonb`, inmutable a propósito |
| `inventory_items` | `insumos` | ya era tenant-scoped |
| `inventory_movements` | `movimientos_inventario` | |
| `customers` | `clientes` | |
| `notifications` | `notificaciones` | |

### 6.3 Enums

Los estados que hoy son strings sueltos pasan a tipos Java:

- `OrderStatus`: `NEW`, `SENT`, `READY`, `PAID`, `CANCELED`
- `KitchenItemStatus`: `PENDING`, `IN_PREPARATION`, `READY`, `DELIVERED`
- `SaleStatus`: `PENDING`, `COMPLETED`, `CANCELED`
- `PaymentMethod`: `CASH`, `CARD`, `TRANSFER`, `MIXED`, `MERCADOPAGO`
- `UserRole`: `SUPER_ADMIN`, `ADMIN`, `WAITER`, `KITCHEN`, `CASHIER`

### 6.4 Glosario español ↔ inglés

Se publica en `docs/` para que el jurado siga el hilo entre el modelado original y el código nuevo.

| Español (actual) | Inglés (nuevo) |
|---|---|
| comanda | Order |
| mesa | RestaurantTable |
| venta | Sale |
| insumo | InventoryItem |
| movimiento de inventario | InventoryMovement |
| platillo / producto | Product |
| mesero | WAITER |
| propina | tip |
| folio | folio |
| restaurante (tenant) | Tenant |

---

## 7. Alcance

### 7.1 Dentro del alcance para el sábado

- Autenticación con roles, JWT y 2FA TOTP
- SaaS multi-tenant real: onboarding de restaurantes, aislamiento por tenant, planes, suscripciones y panel de super-admin
- Flujo core de pedido: mesas → comanda → cocina en tiempo real → cobro → ticket PDF
- Pagos con MercadoPago (con proveedor mock conmutable por feature flag)
- Inventario: insumos y movimientos manuales
- ETL de migración desde MongoDB

### 7.2 Fuera del alcance (fase 2, documentada)

- Delivery y tracking en vivo
- Analytics y ML: K-means, RandomForest, Apriori, Pareto, predicción
- Sensores IoT / wearable
- Backups automatizados
- Notificaciones push

El descuento de inventario basado en recetas queda también en fase 2: para el sábado, los movimientos de inventario son manuales.

---

## 8. Seguridad y aislamiento

### 8.1 Flujo de autenticación

```
POST /api/v1/auth/login   { email, password }
   → BCryptPasswordEncoder.matches()
   → si TOTP habilitado: 202 + challengeToken
POST /api/v1/auth/2fa     { challengeToken, code }
   → access JWT   (15 min, cookie httpOnly, SameSite=Strict)
   → refresh token (7 días, hasheado en BD, rotativo)
```

Claims del JWT: `sub` (userId), `tid` (tenantId), `role`, `exp`.

### 8.2 Los cuatro eslabones del aislamiento

1. **`TenantFilter`** — `OncePerRequestFilter` posterior a la autenticación. Extrae `tid` del JWT y lo coloca en `TenantContext`. Equivalente directo de `utils/tenant_context.py`, preservando la semántica *fail-closed*: sin tenant activo, `403`.

2. **`TenantAwareTransactionManager`** — extiende `JpaTransactionManager` y al abrir cada transacción ejecuta:
   ```sql
   SELECT set_config('app.tenant_id', :tid, true)
   ```
   El tercer parámetro `true` hace la variable **local a la transacción**, impidiendo que se filtre a otra petición cuando HikariCP devuelve la conexión al pool.

3. **Políticas RLS**, una por cada tabla del plano de datos:
   ```sql
   ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
   ALTER TABLE orders FORCE  ROW LEVEL SECURITY;

   CREATE POLICY tenant_isolation ON orders
     USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
     WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
   ```
   `USING` filtra lecturas, updates y deletes. `WITH CHECK` impide insertar filas con el `tenant_id` de otro tenant. Ambas cláusulas son necesarias.

4. **Dos roles de base de datos.** El dueño de una tabla ignora las políticas RLS por defecto; ésta es la trampa que invalida la mayoría de las implementaciones:

   | Rol | Uso | RLS |
   |---|---|---|
   | `callejon9_owner` | solo migraciones Flyway | la ignora (correcto) |
   | `callejon9_app` | runtime de la aplicación | sujeto a RLS, sin `BYPASSRLS` |

   `FORCE ROW LEVEL SECURITY` actúa como segundo cinturón.

### 8.3 Control plane y autorización

Las tablas `tenants`, `plans` y `subscriptions` no llevan RLS: son el catálogo global. Se protegen por rol `SUPER_ADMIN` con `@PreAuthorize` a nivel de método.

`PlanLimitService` verifica los límites del plan antes de crear usuarios o mesas; al exceder devuelve `409 Conflict`. Rate limiting con Bucket4j en los endpoints de autenticación.

---

## 9. Tiempo real

STOMP sobre WebSocket en `/ws`, con handshake autenticado por JWT. Topics tenant-scoped:

```
/topic/tenant.{tenantId}.kitchen
/topic/tenant.{tenantId}.tables
```

Un `ChannelInterceptor` valida en cada `SUBSCRIBE` que el tenant del destino coincida con el del principal autenticado — es el equivalente del RLS para el canal de tiempo real, y cubre un hueco que existe hoy en la implementación de SocketIO.

Los eventos se publican con `@TransactionalEventListener(phase = AFTER_COMMIT)`, de modo que la interfaz nunca observa estado no confirmado.

---

## 10. Manejo de errores

`@RestControllerAdvice` global que devuelve RFC 7807 `ProblemDetail` (nativo en Spring 6):

| Situación | Respuesta |
|---|---|
| Bean Validation falla | `400` + errores por campo |
| Recurso inexistente | `404` |
| Regla de negocio violada | `409` |
| Sin tenant en contexto | `403` |

Excepciones de dominio: `BusinessRuleException`, `ResourceNotFoundException`, `NoTenantContextException` (espejo de `NoTenantContextError`). Logging con SLF4J, con `tenantId` y `userId` en el MDC.

---

## 11. Testing

**JUnit 5 + AssertJ contra PostgreSQL 16 real.** No se usa H2: H2 no soporta RLS, por lo que testear contra H2 invalidaría precisamente la garantía que el proyecto demuestra.

El entorno de la máquina de desarrollo no tiene Docker, así que no se usa Testcontainers. En su lugar:

- **En local**: los tests corren contra una base dedicada `callejon9_test` del Postgres nativo, con `@Sql` para sembrar y limpiar entre casos.
- **En CI (GitHub Actions)**: se usa un *service container* `postgres:16`, que Actions provee sin necesidad de Docker local.
- La URL de conexión se resuelve por perfil de Spring (`application-test.yml`), de modo que el mismo código de test sirve en ambos entornos.

Si en algún momento se instala Docker, migrar a Testcontainers es cambiar la fuente del `DataSource` de test, sin tocar los tests.

Cobertura priorizada:

1. **`TenantIsolationTest`** — siembra dos tenants y verifica que, con el contexto del tenant A, no se pueda leer, actualizar, borrar ni insertar filas del tenant B. Incluye un caso sin contexto de tenant que debe fallar cerrado. Es la evidencia central del proyecto.
2. **Transacción de cobro** — `order → sale → ticket → inventory_movement` en una sola transacción, con rollback completo si el pago falla.
3. **Matriz de autorización** — `@WebMvcTest` por controller: qué rol accede a qué endpoint.
4. **Autorización de suscripción WebSocket.**

El objetivo no es cobertura del 100%, sino cubrir la garantía multi-tenant, el camino del dinero y la autenticación.

La implementación sigue TDD: test primero, luego el código que lo hace pasar.

---

## 12. Migración de datos

`etl/mongo_to_postgres.py` reutiliza `pymongo` y escribe en Postgres con `psycopg`. Asigna todo el histórico al tenant `callejon9`, replicando lo que hoy hace `Restaurante.ensure_default()`.

Transformaciones que resuelve:

- Normaliza `mesa.numero` de tipos mixtos a `int`
- Fusiona `productos` + `platillos` en `products`, normalizando `categoria` a `categories`
- Desdobla los items embebidos de `comandas` en filas de `order_items`
- Traslada los hashes bcrypt intactos, de modo que los usuarios conservan su contraseña

Es idempotente y emite un reporte de validación (conteo por colección frente a conteo por tabla) que prueba que no se perdieron registros.

---

## 13. Repositorio

Monorepo nuevo con historia limpia y enlace al repositorio original en el README para trazabilidad. Nombre de trabajo: `callejon9-saas`.

```
callejon9-saas/
├── backend/            Spring Boot 3.4 · Java 21 · Maven Wrapper
├── frontend/           Next.js 15 · TypeScript
├── etl/                mongo_to_postgres.py
├── docs/               arquitectura, glosario ES↔EN, manual
├── scripts/            setup-db.sql, run-dev.ps1
└── .github/workflows/  CI: build + tests con service container postgres:16
```

**Pendiente de confirmar con el usuario antes de crear el repositorio remoto:** nombre definitivo y visibilidad (público o privado). El trabajo local no depende de esta decisión y puede avanzar mientras se resuelve.

---

## 14. Cronograma

Hoy es martes 28 de julio; la demo es el sábado 1 de agosto.

| Día | Fase | Entregable verificable |
|---|---|---|
| Mar 28 | Cimientos | Repo local, scaffold, `mvnw spring-boot:run` operativo contra Postgres local, Flyway V1 con 17 tablas + RLS + 2 roles, CI en verde |
| Mié 29 | Tenancy + Auth | `TenantIsolationTest` verde. Login + 2FA + JWT. Control plane con onboarding. Prueba: dos tenants creados por API que no se ven entre sí |
| Jue 30 | Dominio core | users, tables, catalog, orders, flujo de cocina, WebSocket. Frontend: login, mesero, cocina |
| Vie 31 | Cobro | sales, tips, MercadoPago, ticket PDF, inventory. Frontend: caja, admin, super-admin. ETL ejecutado |
| Sáb 1 (AM) | Pulido | Seed de demo, Swagger, README + glosario, ensayo del recorrido completo dos veces |

Backend y frontend avanzan **en paralelo** desde el miércoles mediante subagentes trabajando en features independientes. El usuario autorizó explícitamente esta paralelización.

### 14.1 Riesgos y mitigación

| Riesgo | Mitigación |
|---|---|
| Las ~20 pantallas SPA son la parte más apretada del cronograma | shadcn/ui aporta los componentes; sin diseño desde cero |
| MercadoPago sandbox puede fallar durante la demo | Feature flag con proveedor mock |
| RLS ignorado por el dueño de la tabla | Rol `callejon9_app` no-owner + `FORCE ROW LEVEL SECURITY` |
| Backend y frontend en serie no caben en 4 días | Paralelización con subagentes desde el miércoles |
| Alcance mayor al tiempo disponible | Módulos de fase 2 explícitamente fuera, documentados con su ruta de migración |

---

## 15. Criterios de éxito

El proyecto se considera listo para la defensa cuando:

1. `scripts/run-dev.ps1` levanta backend y frontend desde cero contra el Postgres local, con las migraciones aplicadas.
2. `TenantIsolationTest` pasa, demostrando aislamiento impuesto por el motor.
3. Se puede recorrer sin errores: registro de un restaurante nuevo → login → mesero abre comanda → cocina la marca lista en tiempo real → caja cobra → se descarga el ticket PDF.
4. El panel de super-admin muestra los tenants, sus planes y sus suscripciones.
5. La suite de tests pasa en CI.
6. La documentación explica la arquitectura, incluye el glosario ES↔EN y justifica la elección de PostgreSQL con RLS.
