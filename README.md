# Callejón 9 — SaaS multi-restaurante

> Para trabajo asistido por agentes, consulta [`AGENTS.md`](AGENTS.md) y el [`workflow/README.md`](workflow/README.md). El mismo harness corre en Codex y en Claude Code —este último entra por [`CLAUDE.md`](CLAUDE.md)— e incluye skills de build, review, ship y planeación, especialistas opcionales y una conexión de proyecto independiente para Linear.

Migración a Java del sistema de gestión de restaurantes [Callejón 9](https://github.com/Ludwingarcia14/Restaurante-Callejon-9), originalmente construido en Flask con MongoDB, convertido en una plataforma SaaS multi-inquilino.

## La tesis del proyecto

> **El aislamiento entre restaurantes lo impone PostgreSQL, no el código de la aplicación.**

Esta es la afirmación que el proyecto defiende, y todo lo demás está subordinado a ella. La diferencia importa: un sistema que filtra por `tenant_id` en cada consulta depende de que ningún programador olvide nunca ese filtro. Uno que usa Row Level Security depende de una política declarada una vez, que el motor aplica aunque la consulta esté mal escrita.

El sistema Flask original ya tenía la intención correcta: `utils/tenant_context.py` mantenía el inquilino activo y `models/base_model.py` inyectaba el filtro automáticamente. Pero de 19 modelos, solo 2 heredaban de esa clase base. Los del núcleo del negocio —comandas, ventas, mesas, tickets, usuarios— consultaban Mongo directamente, sin filtro. La garantía era una convención, y las convenciones se olvidan.

Esta versión mueve esa garantía al motor.

### Cómo se comprueba

```powershell
$env:PGPASSWORD='app_dev_pwd'
& 'C:\Program Files\PostgreSQL\16\bin\psql.exe' -U callejon9_app -p 5433 -d callejon9_test -f scripts\verify-rls.sql
```

Resultado esperado, ejecutado como el rol de la aplicación:

```
check_1  sin tenant -> 0
check_2  tenant A ve -> 1
check_3  tenant B ve -> 0
check_4  ERROR: new row violates row-level security policy for table "users"
```

El cuarto resultado es un error a propósito: PostgreSQL rechaza que un inquilino inserte una fila a nombre de otro. La prueba equivalente en Java es `TenantIsolationTest`, que cubre los cinco casos —lectura, ausencia de contexto, actualización, borrado e inserción cruzada— contra una base real.

### Las cuatro piezas del aislamiento

1. **`TenantFilter`** traduce la cookie JWT al `TenantContext` y limpia el `ThreadLocal` en un `finally`, para que un hilo reutilizado del pool no herede el inquilino anterior.
2. **`TenantAwareTransactionManager`** publica el inquilino a PostgreSQL al abrir cada transacción con `set_config('app.tenant_id', …, true)`. El tercer argumento hace la variable local a la transacción, así que no puede filtrarse a otra petición cuando HikariCP devuelve la conexión.
3. **Las políticas RLS** de las 17 tablas usan `USING` y `WITH CHECK`. La primera filtra lecturas, actualizaciones y borrados; la segunda impide escribir dentro de otro inquilino. Ambas leen `nullif(current_setting('app.tenant_id', true), '')::uuid`, porque una variable de sesión personalizada que ya se usó vuelve a cadena vacía y no a nula.
4. **Dos roles de base de datos.** `callejon9_owner` solo ejecuta migraciones; `callejon9_app` corre la aplicación y **no es dueño de ninguna tabla**, por lo que las políticas sí le aplican. Además todas las tablas llevan `FORCE ROW LEVEL SECURITY`, que somete incluso al dueño.

Ese cuarto punto no es teórico: la migración `V6` tuvo que publicar el inquilino con `set_config` antes de insertar el super-administrador, porque Flyway corre como el dueño y la política lo rechazaba. La política es lo bastante real como para obligar a la propia migración.

---

## Arquitectura

```
┌────────────────────────────┐      ┌────────────────────────────┐
│  Next.js 15 · React 19     │      │  Apps Expo (React Native)  │
│  web del personal + QR     │      │  Meseros · Clientes        │
└─────────────┬──────────────┘      └─────────────┬──────────────┘
              │ /api/v1/* reescrito por Next      │ HTTPS directo
              │ (mismo origen: sin CORS)          │ a la API
┌─────────────▼───────────────────────────────────▼──────────────┐
│  Spring Boot 3.4 · Java 21                                     │
│    Controller → solo HTTP                                      │
│    Service    → negocio, @Transactional                        │
│    Repository → Spring Data JPA                                │
│    TenantFilter → TenantContext                                │
└─────────────────────────────┬──────────────────────────────────┘
                              │ set_config('app.tenant_id', …, true)
┌─────────────────────────────▼──────────────────────────────────┐
│  PostgreSQL 16 + Row Level Security en 17 tablas               │
└────────────────────────────────────────────────────────────────┘

La web, las dos apps y la API envían errores, trazas, logs y métricas a Sentry.
```

El frontend nunca llama a `:8080` directamente. Un `rewrites()` en `next.config.ts` hace que el navegador solo hable con `:3000`, lo que elimina CORS por completo y deja la cookie de sesión como estrictamente de primera parte.

### Estructura

```
backend/          Spring Boot · Maven Wrapper · 866 pruebas en 96 clases · 59 rutas de API
frontend/         Next.js · TypeScript · Tailwind · shadcn/ui · 15 pantallas
mobile/           App Expo de meseros: mesas, comandas, catálogo y notificaciones push
mobile-clientes/  App Expo de comensales: menú, pedido y llamada al mesero, sin conexión
infra/            Terraform de Azure (API, web, CI) y Jenkins como código
scripts/          setup-db.sql, verify-rls.sql, verify-images.sh, despliegue y operación
docs/             pruebas (UT, SIT, UAT), operación, entregas, specs y planes
```

Las cifras son de `main` al 28 de noviembre de 2026. Cada app móvil tiene su propio README con instrucciones para ejecutarla y generar el APK.

Los paquetes del backend se organizan por funcionalidad, no por capa: `auth`, `user`, `table`, `catalog`, `order`, `kitchen`, `sale`, `ticket`, `inventory`, `analytics`, `realtime`, `platform`, más `tenancy` y `shared`. Cada uno contiene `web/`, `domain/`, `service/` y `repository/`.

---

## Producción en Azure

| Componente | Recurso | Dirección |
|---|---|---|
| Web | App Service Linux (Node 24), plan F1, en West US 3 | https://restohub.azurewebsites.net |
| API | App Service Java 21, plan B1, en Central US, con health check en `/actuator/health/liveness` | `app-callejon9-api-prod-70af93.azurewebsites.net` |
| Base de datos | Azure Database for PostgreSQL Flexible Server 16 (B1ms), respaldo de 7 días | Privada para la API |
| Secretos | Key Vault, leído con la identidad administrada de la API | — |
| Monitoreo | Application Insights y Log Analytics para la API; Sentry para los cuatro componentes | — |
| CI | GitHub Actions y Jenkins en una VM de Azure | Checks en cada PR |

Todo se provisiona con Terraform (`infra/terraform/azure-backend`, `azure-frontend` y `azure-ci`); los procedimientos están en el README de cada carpeta. La web se publica con `scripts/deploy-frontend-azure.ps1`. El monitoreo está descrito en [`docs/operacion-sentry.md`](docs/operacion-sentry.md) y en el plan de monitoreo de `docs/entregas/`.

> **Estado al 27 de noviembre de 2026.** La suscripción original de la API se quedó sin crédito y Azure la deshabilitó. Con el mismo Terraform, la API y PostgreSQL se recrearon en la suscripción de la web, en Central US, porque la política de la suscripción solo permite cinco regiones y West US 3 rechazó la SKU B1ms de PostgreSQL. La base nueva tiene el restaurante de pruebas `pruebas`, cargado por la API; su contraseña está en el secreto `demo-password` del Key Vault `kv-c9-prod-70af93`.

---

## Puesta en marcha desde cero

### Requisitos

| Herramienta | Versión | Nota |
|---|---|---|
| JDK | 21 (Temurin) | `winget install EclipseAdoptium.Temurin.21.JDK` |
| PostgreSQL | 16 | Para ejecutar la aplicación local; opcional para las pruebas |
| Node.js | 24 | |
| pnpm | 10 | `corepack enable` lo resuelve desde el campo `packageManager` |
| Maven | — | No hace falta: el proyecto trae Maven Wrapper |
| Docker | Motor compatible con Testcontainers | Necesario para las pruebas por defecto; no requiere PostgreSQL instalado |

Esta máquina de desarrollo ya tenía un PostgreSQL 18 ocupando el puerto 5432, así que el 16 quedó en el **5433**. Para la aplicación local y las pruebas en modo externo, el puerto se resuelve con `DB_PORT`. Las pruebas con Testcontainers usan un puerto dinámico asignado por Docker.

### 1. Roles y bases

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -p 5433 -f scripts\setup-db.sql
```

Crea `callejon9_owner` y `callejon9_app`, más las bases `callejon9` y `callejon9_test`. Conviene verificar que ninguno de los dos roles pueda evadir RLS:

```sql
SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname LIKE 'callejon9%';
```

Ambos deben dar `f` en las dos columnas. Si `callejon9_app` tuviera `rolbypassrls = t`, todo el aislamiento sería decorativo.

### 2. Backend

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:DB_APP_PASSWORD = 'app_dev_pwd'
$env:DB_OWNER_PASSWORD = 'owner_dev_pwd'
$env:JWT_SECRET = 'dev-only-secret-change-me-min-32-bytes-long!!'

cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Flyway aplica las migraciones pendientes al arrancar. La API queda en `http://localhost:8080` y la documentación navegable en `http://localhost:8080/swagger-ui.html`.

La sesión se sostiene con un segundo token. El login emite dos cookies `httpOnly` y `SameSite=Strict`: `access_token` (JWT de 15 minutos) y `refresh_token` (limitada a `/api/v1/auth`). Cuando una petición recibe 401, el cliente llama una sola vez a `POST /api/v1/auth/refresh`, que consume el refresh token, emite un par nuevo y repite la petición original. Presentar un refresh token ya consumido revoca la sesión completa. La sesión dura como máximo `app.jwt.refresh-token-days` (7 días) desde el login: renovar no la alarga. En la base solo se guarda el SHA-256 del secreto del token.

El logout invalida la sesión en el servidor, no solo en el navegador. Cada access token lleva el identificador de su sesión (claim `sid`). En cada petición HTTP y en el handshake del WebSocket, el backend comprueba que esa sesión siga vigente. Logout, la detección de reutilización o el vencimiento de la sesión cortan al instante todos los access tokens de esa sesión, aunque su JWT todavía no expire. Las demás sesiones del mismo usuario siguen activas. Desactivar a un usuario revoca todas sus sesiones en la misma transacción, así que pierde el acceso en su siguiente petición; si la desactivación se rechaza (último administrador o autodesactivación), no se revoca nada. La comprobación cuesta una lectura indexada por petición y corre con el tenant del token, así que RLS impide que un `sid` de otro restaurante encuentre una sesión ajena. El logout exige una sesión vigente; si el access token ya venció, el cliente renueva la sesión y repite el logout para que se revoque en el servidor.

Las conexiones WebSocket ya abiertas también se cortan. Cada instancia del backend lleva el registro de sus conexiones con el `sid` de cada una, y cada `app.realtime.session-check-interval` (30 s por defecto) consulta en la base cuáles de esas sesiones siguen vigentes y cierra las demás con el código 1008. Como la fuente de verdad es la base, una revocación hecha en otra instancia se detecta igual. La latencia máxima entre revocar y cortar es ese intervalo; el costo es una consulta indexada por restaurante con conexiones abiertas en cada ciclo, sin consultas por mensaje.

El token de acceso dura 15 minutos en todos los perfiles, incluido `demo`. La renovación automática evita que la sesión se corte a media presentación.

Las cookies llevan el atributo `Secure` salvo que `AUTH_COOKIE_SECURE=false`. `scripts/run-dev.ps1` y el perfil `demo` lo desactivan porque en local no hay HTTPS. En cualquier otro entorno la API debe servirse detrás de HTTPS.

El WebSocket `/ws` solo acepta el handshake desde los orígenes de `APP_ALLOWED_ORIGINS` (separados por coma, sin comodines). Por omisión es `http://localhost:3000`; en cualquier otro entorno debe ser el dominio público del frontend. Con la lista vacía o con `*`, el backend no arranca.

El navegador no abre `/ws` en el host de la API, sino en el del frontend. `next.config.ts` reenvía ese handshake al backend igual que `/api/v1/*`, así que la cookie `SameSite=Strict` llega sola y el token nunca pasa por JavaScript (CAL-172). El tablero de cocina y la vista de orden del mesero se suscriben al tópico de su restaurante y, con cada evento, vuelven a pedir sus datos. La consulta periódica se queda como respaldo: cada 30 s con el canal abierto, y cada 5 s (cocina) o 10 s (orden en cocina) cuando el canal no está disponible. En Azure, el App Service del frontend necesita WebSockets activados (`websockets_enabled` en `infra/terraform/azure-frontend`).

#### Perfiles

`application.yml` trae valores por defecto para que el proyecto corra en local sin preparar nada. Esos valores (secreto JWT, contraseñas de `callejon9_app` y `callejon9_owner`) están publicados en el repositorio, así que solo sirven en una máquina de desarrollo.

| Perfil | Uso | Secretos | Cookies | Actuator y OpenAPI |
|---|---|---|---|---|
| ninguno | Desarrollo local, `scripts/run-dev.ps1`, Compose | Valores por defecto de desarrollo | `Secure` salvo `AUTH_COOKIE_SECURE=false` | `health` y Swagger UI públicos |
| `demo` | Presentación en `http://localhost` | Valores por defecto de desarrollo | Sin `Secure` | `health` y Swagger UI públicos |
| `prod` | Cualquier entorno real, detrás de HTTPS | Obligatorios, sin valor por defecto | Siempre `Secure`; `AUTH_COOKIE_SECURE` no aplica | Solo `/actuator/health`, sin detalles; OpenAPI y Swagger UI apagados |

Sin `prod`, si el arranque usa el secreto JWT publicado o el perfil `demo`, el backend lo advierte en el log con una línea `WARN` que empieza por `Configuracion NO apta para produccion`.

Con `SPRING_PROFILES_ACTIVE=prod`, el backend se niega a arrancar, antes de conectarse a la base y de abrir el puerto, si:

- falta `JWT_SECRET`, tiene menos de 32 bytes o es uno de los valores publicados en el repositorio;
- falta `DB_APP_PASSWORD` o `DB_OWNER_PASSWORD`, o es la contraseña de desarrollo de `scripts/setup-db.sql`;
- falta `APP_ALLOWED_ORIGINS` o alguno de sus orígenes no es `https://`;
- `app.auth.cookie-secure` se forzó a `false`;
- `APP_PROXY_SECRET`, que es opcional, está definida con menos de 32 bytes;
- `PLATFORM_ADMIN_PASSWORD`, que es opcional, está definida con menos de 16 caracteres o es la contraseña publicada del super-administrador;
- se activa junto a `demo` o `dev`.

El error enumera cada problema por el nombre de la variable, nunca por su valor. `prod` fija además `server.forward-headers-strategy=none`: la IP del cliente la resuelve `ClientIp` con su propio modelo de confianza (red local, `APP_TRUSTED_PROXIES` y la IP que reenvía el frontend con `APP_PROXY_SECRET`), y así Spring Boot no lo cambia por su cuenta al detectar una plataforma en la nube. La validación vive en `ProductionConfigurationGuard`.

Ya conectado a la base, y antes de abrir el puerto, `PlatformAdminPasswordGuard` revisa la cuenta `super@callejon9.com` que siembra `V6`. Si conserva la contraseña publicada, la reemplaza con `PLATFORM_ADMIN_PASSWORD`; si la variable no está definida, detiene el arranque. Una contraseña distinta no se toca (CAL-109).

### 3. Frontend

```powershell
cd frontend
pnpm install
pnpm dev
```

Queda en `http://localhost:3000`.

El gestor de paquetes es **pnpm** y el lockfile versionado es `pnpm-lock.yaml`. Mezclarlo con `npm install` deja el `node_modules` a medias —pnpm aparta lo que instaló npm en `node_modules/.ignored`— y hay que borrar el directorio para recuperarse.

Para una demostración conviene usar `pnpm build` seguido de `pnpm start` en lugar de `pnpm dev`: en modo desarrollo Turbopack compila cada ruta la primera vez que se visita, y esa pausa de unos segundos se nota. Los dos comandos comparten el directorio `.next`, así que no deben ejecutarse a la vez.

### Cuentas

| Restaurante (slug) | Correo | Contraseña | Rol |
|---|---|---|---|
| `platform` | `super@callejon9.com` | `Callejon9Demo!` | `SUPER_ADMIN` |

Esta contraseña está publicada y sirve solo para desarrollo y la demo local. La siembra `V6__seed_platform_admin.sql` en cualquier base nueva. Con el perfil `prod`, la aplicación no arranca mientras la cuenta la conserve: la reemplaza al arrancar con `PLATFORM_ADMIN_PASSWORD` (16 caracteres o más) o se detiene con un mensaje que nombra esa variable. Si la contraseña ya es otra, no la toca (CAL-109). En Azure la variable llega desde Key Vault; ver `infra/terraform/azure-backend/README.md`.

El inquilino `platform` no es un restaurante: es un registro técnico al que pertenece quien administra la plataforma. Existe porque `users.tenant_id` es `NOT NULL` y el login resuelve el inquilino por slug antes de buscar al usuario — precisamente para que un correo válido en un restaurante no sirva para entrar a otro. Hacer la columna nulable habría debilitado el aislamiento justo donde más importa.

Los demás restaurantes se crean desde `/signup`. Cada IP puede intentar como máximo `SIGNUP_MAX_PER_IP` altas (5 por omisión) dentro de `SIGNUP_WINDOW` (1 hora); al pasarse, el alta responde `429` con `Retry-After` sin crear nada. Cuentan las altas creadas y las rechazadas por slug ocupado; un error de formato no. El contador vive en memoria de cada instancia. Detalle y pruebas en `docs/practica-seguridad-signup.md`.

---

## Pruebas

```powershell
cd backend
.\mvnw.cmd verify
```

Las pruebas levantan PostgreSQL 16 real con Testcontainers por defecto. Basta tener
Docker disponible: no requieren instalar PostgreSQL ni preparar roles a mano.
Un contenedor se comparte entre todas las clases dentro de la misma JVM y se
elimina al terminar mediante el limpiador de Testcontainers. No se reutiliza entre
ejecuciones. Flyway usa `callejon9_owner`; la aplicación usa `callejon9_app`, sin
superusuario, BYPASSRLS ni propiedad de tablas. Las credenciales del contenedor
son aleatorias y efímeras. **No se usa H2**, porque no demuestra RLS.

Para usar una base externa preparada con `scripts/setup-db.sql`:

```powershell
cd backend
.\mvnw.cmd -B "-Dtest.database.mode=external" -DDB_PORT=5433 verify
```

También se puede definir `TEST_DATABASE_MODE=external` y `TEST_DATABASE_URL`
(URL JDBC de una base exclusiva de pruebas). `DB_APP_PASSWORD` y
`DB_OWNER_PASSWORD` configuran las credenciales externas. `DB_PORT` por sí solo
no desactiva Testcontainers. El modo por defecto falla si Docker no está
disponible; no omite las pruebas ni cambia silenciosamente a la base local.

CI y Jenkins pueden ejecutar `./mvnw -B verify` con acceso a Docker, sin servicio
PostgreSQL adicional. La primera ejecución descarga la imagen y dependencias.

Las que más importan:

| Prueba | Qué demuestra |
|---|---|
| `TenantIsolationTest` | Los cinco casos de aislamiento impuesto por el motor |
| `UserServiceConcurrencyTest` | Dos hilos desactivando administradores distintos no pueden dejar al restaurante sin ninguno |
| `PlanLimitServiceConcurrencyTest` | Dos altas simultáneas no pueden exceder el tope del plan |
| `TenantFilterHttpTest` | Autorización sobre Tomcat embebido real, no MockMvc |
| `BcryptCompatibilityTest` | Un hash generado por la librería `bcrypt` de Python valida bajo Spring Security |
| `TenantSubscriptionInterceptorTest` | Un inquilino no puede suscribirse al canal de tiempo real de otro |
| `RevokedSessionSweeperTest` | Revocar una sesión, aun desde otra instancia, cierra sus conexiones WebSocket sin tocar las demás |
| `TenantOnboardingServiceCompensationTest` | El alta revierte el inquilino si falla la creación del administrador |
| `ProductionConfigurationGuardTest` | El perfil `prod` no arranca con secretos ausentes, cortos o de desarrollo, y el error no imprime el valor |

`BcryptCompatibilityTest` merece una nota: confirma que los usuarios del sistema Flask conservan su contraseña tras la migración. Dejó de ser una suposición del diseño y pasó a ser una prueba automatizada.

### Una divergencia que vale la pena conocer

`TenantFilterHttpTest` existe porque MockMvc y un contenedor real no se comportan igual. Un `sendError(403)` en Tombat dispara un redespacho interno a `/error` que vuelve a correr la cadena de seguridad; `OncePerRequestFilter` la omite por defecto, el contexto llega vacío y el 403 correcto se sobrescribe con un 401. MockMvc nunca lo reproduce porque su `sendError` es solo contabilidad. La prueba original daba verde afirmando algo que el sistema desplegado no hacía.

---

## Alcance

### Implementado

**Plataforma.** Alta de restaurantes con su suscripción, planes con límites que se aplican de verdad, y panel de super-administrador.

**Personal.** Alta de usuarios por rol —administrador, mesero, cocina, caja—, con baja lógica que deja a la persona fuera de inmediato. Un administrador no puede desactivarse a sí mismo ni dejar al restaurante sin ningún administrador activo.

**Operación.** Mesas, catálogo, comandas con precio congelado al agregarse, tablero de cocina con transiciones solo hacia adelante, cobro con propina, ticket en PDF, e historial de ventas con búsqueda por folio.

**Correcciones.** Editar y dar de baja productos, categorías y mesas; cancelar una comanda y liberar su mesa. Nada se borra físicamente: los productos y las mesas están referenciados por el histórico.

**Caja y cobro digital.** Turnos de caja con arqueo, pagos mixtos y divididos, cambio, propina y pasarela de cobro digital con Mercado Pago (CAL-196 y CAL-197) mediante checkout QR dinámico de comandas en terminal POS y Menú QR de comensales, con webhooks idempotentes y conciliación automática.

**Inventario y reportes.** Movimientos de inventario, historial de ventas paginado y analítica de ventas.

**Tiempo real.** Cocina y meseros reciben las comandas por WebSocket (STOMP) sin recargar la página.

**Apps móviles.** App de meseros con sesión, mesas, comandas, catálogo y notificaciones push; app de comensales que pide desde la mesa y guarda los pedidos sin conexión hasta que vuelve la red.

**Infraestructura.** Autenticación con JWT en cookie `httpOnly` y rotación de tokens de refresco, canal STOMP con validación de inquilino por suscripción, documentación OpenAPI navegable, despliegue en Azure con Terraform, CI en GitHub Actions y Jenkins, y observabilidad con Sentry.

### En curso

**Suscripción SaaS del restaurante con Mercado Pago (CAL-29 y CAL-31).** Cobro mensual recurrente por suscripción de la plataforma para nuevos restaurantes (alta de planes, máquina de estados y conciliación de suscripciones). Planeado para cerrar el 11 de diciembre de 2026.

### Diferido, con su diseño

Delivery y rastreo en vivo; aprendizaje automático (K-means, RandomForest, Apriori, Pareto); sensores IoT; segundo factor TOTP —el servicio existe, falta el endpoint—; inventario con descuento por receta; envío automático de notificaciones push desde la API —hoy se envían con `pnpm push:send` desde `mobile/`—; y el ETL de migración desde MongoDB.

### Deuda registrada

| Defecto o limitación | Impacto | Por qué sigue abierto |
|---|---|---|
| `GET /orders` y `GET /orders/{id}` permiten a mesero, caja y administración leer cualquier comanda de su restaurante, no solo las propias | Bajo: no cruza restaurantes, que lo impide RLS | Caja y cocina necesitan ver las comandas de todos los meseros |
| La llamada al mesero solo llega a las pantallas de mesero abiertas en ese momento, no se guarda | Bajo: si nadie tiene abierta la vista de mesas, la llamada se pierde; el comensal puede volver a llamar | Guardarla requiere una tabla nueva con RLS; para la operación en sala basta el aviso en vivo |
| No hay cambio de contraseña por el propio usuario ni recuperación por correo | Medio: un administrador debe restablecerla | Requiere un servicio de correo transaccional, fuera del alcance |
| La web corre en plan F1 de App Service | Medio: Azure descarga la app inactiva y limita la CPU a 60 minutos al día | Costo; para el SLA hace falta B1 (ver `docs/entregas/`) |

Ya resueltos y que antes figuraban aquí: los productos inactivos se rechazan al agregarse a una comanda (CAL-10), las rutas de comandas exigen rol (`@PreAuthorize`), la colisión de folios entre instancias se corrigió con secuencias atómicas en PostgreSQL (CAL-13), el correo de los usuarios ya no distingue mayúsculas (CAL-180), «Llamar al mesero» ya avisa en tiempo real a la pantalla del mesero con el número de mesa (tópico `/topic/tenant.{id}.waiter-calls`, como máximo un aviso por mesa cada 3 s, para absorber el doble toque), y el cobro digital con pasarela Mercado Pago en caja y menú QR está integrado de extremo a extremo con webhook verificado (CAL-196 y CAL-197).

Tres carencias que sí se cerraron y vale la pena nombrar, porque marcaban la diferencia entre una demostración y un sistema: no existía forma de crear usuarios —los cinco roles eran teoría—, nada podía editarse ni darse de baja, y `PlanLimitService` estaba escrito sin que ningún endpoint lo llamara.

---

## Documentación

- [`docs/glosario-es-en.md`](docs/glosario-es-en.md) — correspondencia entre el modelado original en español y el código en inglés
- [`docs/guion-demo.md`](docs/guion-demo.md) — recorrido paso a paso
- [`docs/operacion-probes.md`](docs/operacion-probes.md) — probes de vida y disponibilidad para el orquestador
- [`docs/operacion-sentry.md`](docs/operacion-sentry.md) — errores, trazas, logs y métricas en Sentry: variables, privacidad y cómo probar la conexión
- [`docs/pruebas/`](docs/pruebas/) — evidencia de pruebas unitarias, de integración (SIT) y de aceptación (UAT)
- [`docs/entregas/`](docs/entregas/) — entregables: plan de monitoreo, SLA, integración y liberación, roles del equipo
- [`docs/superpowers/specs/`](docs/superpowers/specs/) — diseño y decisiones de arquitectura
- [`docs/superpowers/plans/`](docs/superpowers/plans/) — plan de implementación

El código fuente está en inglés y la documentación en español. Es la convención habitual en Java: las anotaciones, los tipos y las librerías ya están en inglés, y mezclar idiomas dentro de una clase se lee mal.
