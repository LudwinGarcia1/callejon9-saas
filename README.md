# Callejón 9 — SaaS multi-restaurante

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
3. **Las políticas RLS** de las 14 tablas usan `USING` y `WITH CHECK`. La primera filtra lecturas, actualizaciones y borrados; la segunda impide escribir dentro de otro inquilino. Ambas leen `nullif(current_setting('app.tenant_id', true), '')::uuid`, porque una variable de sesión personalizada que ya se usó vuelve a cadena vacía y no a nula.
4. **Dos roles de base de datos.** `callejon9_owner` solo ejecuta migraciones; `callejon9_app` corre la aplicación y **no es dueño de ninguna tabla**, por lo que las políticas sí le aplican. Además todas las tablas llevan `FORCE ROW LEVEL SECURITY`, que somete incluso al dueño.

Ese cuarto punto no es teórico: la migración `V6` tuvo que publicar el inquilino con `set_config` antes de insertar el super-administrador, porque Flyway corre como el dueño y la política lo rechazaba. La política es lo bastante real como para obligar a la propia migración.

---

## Arquitectura

```
┌─────────────────────────────┐
│  Next.js 15 · React 19      │  8 pantallas
└──────────┬──────────────────┘
           │ /api/v1/*  reescrito por Next hacia :8080
           │ (mismo origen: no hace falta CORS)
┌──────────▼──────────────────┐
│  Spring Boot 3.4 · Java 21  │
│    Controller  → solo HTTP
│    Service     → negocio, @Transactional
│    Repository  → Spring Data JPA
│  TenantFilter → TenantContext
└──────────┬──────────────────┘
           │ set_config('app.tenant_id', …, true)
┌──────────▼──────────────────┐
│  PostgreSQL 16 + RLS        │
└─────────────────────────────┘
```

El frontend nunca llama a `:8080` directamente. Un `rewrites()` en `next.config.ts` hace que el navegador solo hable con `:3000`, lo que elimina CORS por completo y deja la cookie de sesión como estrictamente de primera parte.

### Estructura

```
backend/    Spring Boot · Maven Wrapper · 174 tests · 31 rutas de API
frontend/   Next.js · TypeScript · Tailwind · shadcn/ui · 9 pantallas
scripts/    setup-db.sql, verify-rls.sql, run-dev.ps1
docs/       glosario, guion de demo, specs y planes de implementación
```

Los paquetes del backend se organizan por funcionalidad, no por capa: `auth`, `user`, `table`, `catalog`, `order`, `kitchen`, `sale`, `ticket`, `inventory`, `analytics`, `realtime`, `platform`, más `tenancy` y `shared`. Cada uno contiene `web/`, `domain/`, `service/` y `repository/`.

---

## Puesta en marcha desde cero

### Requisitos

| Herramienta | Versión | Nota |
|---|---|---|
| JDK | 21 (Temurin) | `winget install EclipseAdoptium.Temurin.21.JDK` |
| PostgreSQL | 16 | `winget install PostgreSQL.PostgreSQL.16` |
| Node.js | 24 | |
| pnpm | 10 | `corepack enable` lo resuelve desde el campo `packageManager` |
| Maven | — | No hace falta: el proyecto trae Maven Wrapper |
| Docker | — | No se usa |

Esta máquina de desarrollo ya tenía un PostgreSQL 18 ocupando el puerto 5432, así que el 16 quedó en el **5433**. El puerto se resuelve con la variable `DB_PORT`, que en CI apunta al 5432 del contenedor de servicio.

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

Flyway aplica las seis migraciones al arrancar. La API queda en `http://localhost:8080` y la documentación navegable en `http://localhost:8080/swagger-ui.html`.

El perfil `demo` extiende el token de acceso a dos horas. **El valor de producción son 15 minutos y así se queda**: el perfil existe solo para que una sesión no expire a mitad de una presentación.

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

El inquilino `platform` no es un restaurante: es un registro técnico al que pertenece quien administra la plataforma. Existe porque `users.tenant_id` es `NOT NULL` y el login resuelve el inquilino por slug antes de buscar al usuario — precisamente para que un correo válido en un restaurante no sirva para entrar a otro. Hacer la columna nulable habría debilitado el aislamiento justo donde más importa.

Los demás restaurantes se crean desde `/signup`.

---

## Pruebas

```powershell
cd backend
.\mvnw.cmd verify
```

120 pruebas contra un PostgreSQL 16 real. **No se usa H2**, y la razón es de fondo: H2 no soporta Row Level Security, así que probar contra H2 invalidaría exactamente la garantía que el proyecto demuestra.

Las que más importan:

| Prueba | Qué demuestra |
|---|---|
| `TenantIsolationTest` | Los cinco casos de aislamiento impuesto por el motor |
| `UserServiceConcurrencyTest` | Dos hilos desactivando administradores distintos no pueden dejar al restaurante sin ninguno |
| `PlanLimitServiceConcurrencyTest` | Dos altas simultáneas no pueden exceder el tope del plan |
| `TenantFilterHttpTest` | Autorización sobre Tomcat embebido real, no MockMvc |
| `BcryptCompatibilityTest` | Un hash generado por la librería `bcrypt` de Python valida bajo Spring Security |
| `TenantSubscriptionInterceptorTest` | Un inquilino no puede suscribirse al canal de tiempo real de otro |
| `TenantOnboardingServiceCompensationTest` | El alta revierte el inquilino si falla la creación del administrador |

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

**Infraestructura.** Autenticación con JWT en cookie `httpOnly`, canal STOMP con validación de inquilino por suscripción, y documentación OpenAPI navegable.

### Diferido, con su diseño

Delivery y rastreo en vivo; analítica y aprendizaje automático (K-means, RandomForest, Apriori, Pareto); sensores IoT; integración real con MercadoPago; segundo factor TOTP —el servicio existe, falta el endpoint—; rotación de tokens de refresco; inventario con descuento por receta; notificaciones push; y el ETL de migración desde MongoDB.

### Deuda registrada

`FolioGenerator` garantiza unicidad por JVM, no entre instancias: dos instancias podrían colisionar dentro del mismo segundo para el mismo restaurante. `addItems` no verifica que el producto siga activo, así que un platillo retirado puede agregarse a una comanda si se conoce su identificador. `GET /orders` y `GET /orders/{id}` no llevan `@PreAuthorize`: cualquier rol autenticado lee cualquier comanda de su propio inquilino. No hay cambio de contraseña por el propio usuario ni recuperación de contraseña.

Tres carencias que sí se cerraron y vale la pena nombrar, porque marcaban la diferencia entre una demostración y un sistema: no existía forma de crear usuarios —los cinco roles eran teoría—, nada podía editarse ni darse de baja, y `PlanLimitService` estaba escrito sin que ningún endpoint lo llamara.

---

## Documentación

- [`docs/glosario-es-en.md`](docs/glosario-es-en.md) — correspondencia entre el modelado original en español y el código en inglés
- [`docs/guion-demo.md`](docs/guion-demo.md) — recorrido paso a paso
- [`docs/superpowers/specs/`](docs/superpowers/specs/) — diseño y decisiones de arquitectura
- [`docs/superpowers/plans/`](docs/superpowers/plans/) — plan de implementación

El código fuente está en inglés y la documentación en español. Es la convención habitual en Java: las anotaciones, los tipos y las librerías ya están en inglés, y mezclar idiomas dentro de una clase se lee mal.
