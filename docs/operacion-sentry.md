# Sentry: errores, trazas, logs y métricas

RestoHub envía a Sentry sus errores, trazas, logs y métricas desde los cuatro componentes. Sin DSN, ningún SDK se inicializa: desarrollo, pruebas y CI no mandan nada y no hace falta cuenta de Sentry para trabajar.

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

Las trazas usan una tasa de `0.1` por defecto: una de cada diez peticiones, navegaciones o pantallas. Con `1.0` se trazan todas (útil en local para verificar) y con `0` se apagan. El backend acepta además `SENTRY_LOGS_ENABLED=false` para dejar de enviar logs.

La versión (`release`) sale del commit: `callejon9-backend@<sha>` y `callejon9-frontend@<sha>` en las imágenes Docker; las apps usan su versión de Expo.

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

## Trazas, logs y métricas

| Señal | Backend | Web | Apps |
|---|---|---|---|
| **Trazas** | Cada petición HTTP con sus consultas; `GET /actuator/health` se ignora | Carga de página, navegación, middleware y llamadas a `/api/v1`; Web Vitals | Arranque (`App Start`) y una transacción por pantalla con sus llamadas a la API |
| **Logs** | Logback: todo log de `INFO` en adelante, vía `sentry-logback` | `console.warn` y `console.error` del navegador y del servidor | `console.warn` y `console.error` de JavaScript |
| **Métricas** | `orders_opened`, `orders_sent_to_kitchen`, `checkouts_completed`, `login_failures` | `session_refresh_failures` (la sesión venció y el usuario vuelve al login) | Clientes: `offline_orders_queued`, `offline_orders_sent`, `orders_rejected`. Meseros: `push_registrations` con `status` |

- **Traza distribuida:** el navegador agrega `sentry-trace` y `baggage` a sus llamadas a `/api/v1`, y el rewrite de Next las entrega a la API. Las apps hacen lo mismo hacia `EXPO_PUBLIC_API_URL`. Así, una petición lenta se ve completa del navegador o la app a la consulta SQL.
- **Logs ligados:** cada log lleva el identificador de la traza en la que ocurrió.
- **Métricas:** son solo conteos, sin montos ni identificadores de restaurante, porque las cifras de venta de cada restaurante no salen hacia un servicio externo. En el backend se registran al confirmarse la transacción (`BusinessMetrics`), para no contar operaciones revertidas.
- **Privacidad:** el backend (`SentryPrivacyConfig`), la web y las apps (`redactLog`) tachan correos y JWT de cada log y de cada error antes de enviarlo. Un log como `Alta fallida de ana@restaurante.mx` llega como `Alta fallida de [correo]`.

Verificación del 27 de noviembre, con la API en Docker, la web compilada para producción y la app de clientes en el emulador:

| Componente | Trazas | Logs | Métricas |
|---|---|---|---|
| API | `POST /api/v1/auth/login` y las demás peticiones | 29 logs de Spring, todos con traza y sin correos | `login_failures` = 3 |
| Web | Carga de `/login`, middleware y llamadas a `/api/v1`, en la misma traza que la API | `console.warn` con el correo tachado | `session_refresh_failures` = 2, más Web Vitals |
| App de clientes | `App Start`, `boot` y `setup` | `console.warn` con el correo tachado | Métrica de prueba temporal, ya eliminada |

La app de meseros comparte el mismo código de configuración y no se probó por separado.

Consultas útiles con el CLI de Sentry (`sentry api` devuelve JSON):

```sh
sentry trace list restohub/restohub-server --period 1h --fresh
sentry api organizations/restohub/events/ -X GET -f dataset=ourlogs -f field=message -f statsPeriod=1h
sentry api organizations/restohub/events/ -X GET -f dataset=tracemetrics -f field=metric.name -f 'field=count(metric.name)' -f statsPeriod=24h
```

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
- **API:** la suscripción original se deshabilitó por falta de crédito. La API se recreó con Terraform en `rg-callejon9-prod-cus-001` (Central US) con `TF_VAR_sentry_dsn` (DSN de `restohub-server`) y se publicó desde `main` el 27 de noviembre. Sentry está activo en ella.
- La web publicada se compiló con trazas en 0 y antes de los logs y métricas; los activa el próximo despliegue.

## Consultar errores desde la terminal

Con el CLI de Sentry (`curl -fsSL https://cli.sentry.dev/install | bash`, luego `sentry auth login`):

```sh
sentry issue list restohub/restohub-server --period 24h   # errores recientes de la API
sentry issue view RESTOHUB-FRONTEND-2                     # detalle de un issue
sentry dsn list restohub/restohub-frontend                 # DSN de un proyecto
```

## Consultar errores desde el agente

`.mcp.json` declara el servidor `sentry` (`https://mcp.sentry.dev/mcp`) y `.codex/config.toml` lo repite para Codex. Se autentica con OAuth: `/mcp` en Claude Code, `codex mcp login sentry` en Codex. Las herramientas que modifican Sentry (resolver o asignar issues, crear proyectos, equipos o DSN) piden confirmación en `.claude/settings.json`.
