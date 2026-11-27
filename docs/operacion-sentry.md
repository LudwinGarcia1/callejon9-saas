# Sentry: reporte de errores

RestoHub envía sus errores a Sentry desde los cuatro componentes. Sin DSN, ningún SDK se inicializa: desarrollo, pruebas y CI no mandan nada y no hace falta cuenta de Sentry para trabajar.

## Proyectos en Sentry

Organización `restohub` en sentry.io, región de Estados Unidos (`ingest.us.sentry.io`). Se creó el 27 de noviembre de 2026 con el CLI de Sentry.

| Proyecto | Plataforma | Componente | Variable del DSN |
|---|---|---|---|
| `restohub-server` | `java-spring-boot` | `backend/` | `SENTRY_DSN` |
| `restohub-frontend` | `javascript-nextjs` | `frontend/` | `NEXT_PUBLIC_SENTRY_DSN` |
| `restohub-meseros` | `react-native` | `mobile/` | `EXPO_PUBLIC_SENTRY_DSN` |
| `restohub-clientes` | `react-native` | `mobile-clientes/` | `EXPO_PUBLIC_SENTRY_DSN` |

Los DSN no se versionan. Se leen con `sentry dsn list restohub/<proyecto>`, o en *Settings > Projects > [proyecto] > Client Keys (DSN)*.

## Qué se reporta

| Componente | SDK | Qué llega a Sentry | Dónde se configura |
|---|---|---|---|
| Backend | `sentry-spring-boot-starter-jakarta` | Excepciones que ningún `@ExceptionHandler` atiende (los 500) y cada `log.error` | `backend/src/main/resources/application.yml` §`sentry` |
| Frontend | `@sentry/nextjs` | Errores del navegador, de Server Components, Route Handlers y middleware, y los que llegan a `app/error.tsx` | `frontend/src/lib/sentry.ts`, `src/instrumentation*.ts` |
| App de meseros | `@sentry/react-native` | Errores de JavaScript y fallos nativos | `mobile/src/monitoring/` |
| App de clientes | `@sentry/react-native` | Errores de JavaScript y fallos nativos | `mobile-clientes/src/monitoring/` |

Los 4xx que devuelve `GlobalExceptionHandler` (validación, reglas de negocio, 404, 429) son respuestas esperadas y no se reportan.

## Privacidad

- `send-default-pii` / `sendDefaultPii` está en `false` en los cuatro: el SDK no adjunta cookies, cabeceras de sesión, IP ni el usuario. El backend tampoco envía el cuerpo de las peticiones (`max-request-body-size: none`).
- Los cuatro proyectos de Sentry tienen activado **Prevent Storing of IP Addresses** (*Settings > Security & Privacy*). Hace falta aunque el SDK no envíe PII: el túnel reenvía la IP del navegador y Sentry la guardaría con el evento. Con la opción activa, Sentry la descarta al recibirlo. Un proyecto nuevo debe activarla también.
- El navegador no llama a Sentry directamente. Envía sus eventos a `/sentry-tunnel`, una ruta del propio frontend que los reenvía sin las cabeceras de la petición. Por eso la CSP conserva `connect-src 'self'`. El túnel solo acepta el DSN configurado y responde 403 a cualquier otro.
- Las pruebas `SentryConfigurationTest` (backend), `sentry.test.ts` (frontend y apps) fallan si alguien activa el envío de PII o deja el SDK encendido sin DSN.

## Variables

Cada componente es un proyecto distinto en Sentry, con su propio DSN. El DSN no es secreto (el del navegador y el de las apps viajan dentro del bundle), pero no se versiona: se pasa por variable.

| Componente | Variable | Momento |
|---|---|---|
| Backend | `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `SENTRY_TRACES_SAMPLE_RATE` | Al arrancar. En Azure, `TF_VAR_sentry_dsn` y `sentry_traces_sample_rate` de `infra/terraform/azure-backend` |
| Frontend | `NEXT_PUBLIC_SENTRY_DSN`, `NEXT_PUBLIC_SENTRY_ENVIRONMENT`, `NEXT_PUBLIC_SENTRY_TRACES_SAMPLE_RATE` | Al compilar, como `BACKEND_ORIGIN`. `deploy-frontend-azure.ps1 -SentryDsn …` o argumento de build de la imagen |
| Apps móviles | `EXPO_PUBLIC_SENTRY_DSN`, `EXPO_PUBLIC_SENTRY_ENVIRONMENT`, `EXPO_PUBLIC_SENTRY_TRACES_SAMPLE_RATE` | Al compilar el bundle, desde `.env` |

Las trazas de rendimiento están apagadas (tasa 0). Con `0.1` se traza una de cada diez peticiones.

La versión (`release`) sale del commit: `callejon9-backend@<sha>` y `callejon9-frontend@<sha>` en las imágenes Docker; las apps usan su versión de Expo.

## Source maps

Sin source maps los errores del frontend y de las apps llegan con el código minificado. Para subirlos, el build necesita `SENTRY_AUTH_TOKEN`, `SENTRY_ORG` y `SENTRY_PROJECT` en el entorno. El token es secreto: nunca va en `.env.example`, en un argumento de `docker build` ni en el repositorio.

- Frontend: sin token, `withSentryConfig` no sube nada y la compilación no contacta a Sentry.
- Apps: `metro.config.js` agrega los Debug IDs. En una build nativa de release sin token, define `SENTRY_DISABLE_AUTO_UPLOAD=true` para que Gradle no intente la subida. Después de agregar el plugin hay que regenerar `android/` con `npx expo prebuild --clean`.

## Probar la conexión

1. Los proyectos ya existen en la organización `restohub`: `restohub-server`, `restohub-frontend`, `restohub-meseros` y `restohub-clientes`. Sus DSN se leen con `sentry dsn list restohub/<proyecto>`.
2. Backend: arranca con `SENTRY_DSN=…` y fuerza un 500. El evento aparece en el proyecto del backend.
3. Frontend: compila con `NEXT_PUBLIC_SENTRY_DSN=…` y en la consola del navegador ejecuta `setTimeout(() => { throw new Error("prueba Sentry") })`. En la pestaña Red, la petición va a `/sentry-tunnel`, no a `sentry.io`.
4. Apps: pon `EXPO_PUBLIC_SENTRY_DSN` en `.env`, reinicia Metro con `--clear` y provoca un error.

## Verificación del 27 de noviembre de 2026

Se levantó la aplicación como arranca normalmente (API con `docker compose`, web con `pnpm dev`), se disparó un error con un mensaje identificable desde rutas temporales y se confirmó con `sentry issue list`. Las rutas temporales se eliminaron después.

| Origen | Issue | Resultado |
|---|---|---|
| API (Spring Boot) | `RESTOHUB-SERVER-1` | Llegó; sin IP, cookies ni cuerpo |
| Servidor de Next | `RESTOHUB-FRONTEND-1` | Llegó |
| Navegador | `RESTOHUB-FRONTEND-2` | Llegó por `/sentry-tunnel`; traza legible hasta `page.tsx` |
| App de meseros (emulador Android 17) | `RESTOHUB-MESEROS-1` | Llegó; sin IP; traza hasta `index.ts` |
| App de clientes (emulador Android 17) | `RESTOHUB-CLIENTES-1` | Llegó; sin IP; traza hasta `index.ts` |
| Web en producción (`restohub.azurewebsites.net`) | `RESTOHUB-FRONTEND-3` | Llegó por el túnel con `environment: production` y `release: callejon9-frontend@5e63176`; sin IP |

El primer evento del navegador llegó con la IP del cliente. Por eso se activó **Prevent Storing of IP Addresses** en los cuatro proyectos; el evento siguiente llegó sin IP.

Las apps se probaron con una build de desarrollo (`expo run:android`) y un error temporal en `index.ts`, ya eliminado. El de producción se disparó desde la consola de la página publicada, sin desplegar código de prueba.

## Alertas

Cada proyecto tiene la regla **Issue nuevo o regresión en producción**: cuando aparece un issue nuevo o vuelve uno resuelto en eventos con la etiqueta `environment: production`, Sentry envía un correo al equipo `restohub`, como máximo cada 30 minutos. Corresponde a la alerta P3 del plan de monitoreo. Se listan con `sentry alert issues list restohub/`.

Los cuatro componentes reportan producción con el mismo nombre de entorno, `production`: el frontend y las apps por defecto, y la API porque Terraform traduce `environment = "prod"` a `production`.

## Source maps

Hacen falta para leer los errores de producción del frontend y de las apps, que llegan minificados. La subida usa un token de organización con el alcance `org:ci`, que crea una persona del equipo y nunca se versiona:

```sh
sentry token create restohub/sourcemaps-ci   # imprime el token una sola vez
export SENTRY_AUTH_TOKEN=<token>
```

- **Frontend en Azure:** con `SENTRY_AUTH_TOKEN` en el entorno, `deploy-frontend-azure.ps1` lo pasa al contenedor temporal que hace la compilación publicada, y `withSentryConfig` sube los source maps a `restohub/restohub-frontend`. El contenedor se borra al terminar.
- **Imagen Docker:** `docker build --secret id=sentry_auth_token,env=SENTRY_AUTH_TOKEN …`. El secreto solo existe durante el `RUN` de la compilación y no queda en la imagen.
- **Apps:** el plugin de `app.json` ya trae la organización y el proyecto. Una build de release con `SENTRY_AUTH_TOKEN` sube los source maps sola; sin token, usa `SENTRY_DISABLE_AUTO_UPLOAD=true`.

## Estado del despliegue (27 de noviembre de 2026)

- **Web:** publicada desde `main` (`5e63176`) con Sentry activo. Sin token, todavía sin source maps.
- **API:** no se pudo desplegar. La suscripción de Azure donde vive `app-callejon9-api-prod-3878c6` está deshabilitada y en solo lectura, y PostgreSQL está detenido. Cuando se reactive, se despliega con `TF_VAR_sentry_dsn` (DSN de `restohub-server`) y `terraform apply`, o con `az webapp config appsettings set` si se aplica fuera de Terraform.
- La tasa de trazas sigue en 0 en producción.

## Consultar errores desde la terminal

Con el CLI de Sentry (`curl -fsSL https://cli.sentry.dev/install | bash`, luego `sentry auth login`):

```sh
sentry issue list restohub/restohub-server --period 24h   # errores recientes de la API
sentry issue view RESTOHUB-FRONTEND-2                     # detalle de un issue
sentry dsn list restohub/restohub-frontend                 # DSN de un proyecto
```

## Consultar errores desde el agente

`.mcp.json` declara el servidor `sentry` (`https://mcp.sentry.dev/mcp`) y `.codex/config.toml` lo repite para Codex. Se autentica con OAuth: `/mcp` en Claude Code, `codex mcp login sentry` en Codex. Las herramientas que modifican Sentry (resolver o asignar issues, crear proyectos, equipos o DSN) piden confirmación en `.claude/settings.json`.
