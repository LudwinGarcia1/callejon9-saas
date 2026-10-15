# Auditoría: integración de la app con un servicio en la nube por API — Callejón 9

- **Fecha:** 2026-10-14
- **Base auditada:** rama `main`, commit `ac71b10`
- **Alcance:** comparar el proyecto con la actividad "Integración de la aplicación móvil con un servicio en la nube mediante una API" (consumo de datos, envío de datos y autenticación remota, con medidas de seguridad).

## 1. Resumen general

El proyecto cubre casi toda la parte funcional de la actividad, pero le faltan tres cosas: no es una app móvil, no está en la nube y no usa HTTPS.

La actividad supone una app que hoy usa datos locales o simulados. Este proyecto no es así: es un SaaS web (Next.js + Spring Boot + PostgreSQL) que ya trabaja con su propia API REST. Las tres partes (consultar con GET, enviar con POST e iniciar sesión) ya existen, pasan pruebas automatizadas y tienen buen manejo de errores y seguridad.

**Fortalezas**
- Una API REST propia y documentada (`/api/v1`, con Swagger), con más de 30 rutas.
- Un solo cliente HTTP (`frontend/src/lib/api.ts`) que lee las respuestas JSON y los errores en formato estándar (RFC 7807).
- El login es sólido. Usa BCrypt y el token de sesión (JWT) va en cookies `httpOnly` con `SameSite=Strict`. Tiene renovación automática del token, cierre de sesión en el servidor, límite de intentos fallidos (429) y la misma respuesta 401 para cualquier dato incorrecto, sin revelar cuál falló.
- La interfaz es responsive y la documentación anterior del equipo ya la describe en uso desde tablet o teléfono (`docs/practica-seguridad-login.md`).

**Lo que falta**
1. **No hay un proyecto móvil.** No existe Android, iOS, React Native, Flutter, Capacitor ni PWA (no hay manifest ni service worker). El "cliente móvil" es la web vista en el navegador del teléfono.
2. **La API no está en la nube.** Solo corre en `localhost` o con `docker-compose`. Lo único en Azure (`infra/terraform/azure-ci`) es el servidor de CI con Jenkins, no la aplicación.
3. **No hay HTTPS en ningún entorno.** El código está listo para usarlo (cookies `Secure`, cabecera HSTS), pero el perfil `demo` y `docker-compose` desactivan `Secure` y no hay TLS configurado. Por eso hoy no se puede demostrar que las credenciales viajan por una conexión segura.

**Qué se verificó y qué no**
- No se modificó ningún archivo del repositorio durante la auditoría. Las pruebas solo generaron archivos de compilación en `backend/target/`, que git ignora.
- Se corrieron todas las pruebas del frontend: **118 de 118 pasaron.**
- Se corrieron 6 clases de pruebas del backend sobre login, seguridad y errores, con PostgreSQL real en Docker: **26 de 26 pasaron.** No se corrió el `mvnw verify` completo.
- **No se probó la aplicación en vivo.** No estaba corriendo, y el puerto 8080 que usa el backend por defecto lo ocupa otro proceso Java (Jenkins).

## 2. Actividades implementadas

**P1.1 · API identificada**
- **Cómo funciona:** el backend propio en Spring Boot expone `/api/v1/*`. El navegador llama rutas relativas y `next.config.ts` las reenvía al backend.
- **Archivos:** `frontend/next.config.ts:33-40`, `frontend/src/lib/endpoints.ts`, `README.md:123`.
- **Evidencia:** está el reenvío de `/api/v1/:path*` al backend y la documentación navegable en `/swagger-ui.html`.
- **Limitación:** no tiene URL pública (ver sección 4).

**P1.2 · Endpoint de consulta**
- **Cómo funciona:** `GET /api/v1/tables` devuelve las mesas del restaurante. También hay otros GET: `/orders`, `/products`, `/sales`, `/analytics` y `/auth/me`.
- **Archivos:** `backend/src/main/java/com/callejon9/table/web/TableController.java:35`.

**P1.4 · Procesamiento de la respuesta JSON**
- **Cómo funciona:** `request()` lee el cuerpo con `response.json()` y lo convierte al tipo esperado. Si la respuesta es un error, lo lee como `ProblemDetail` y lanza un `ApiError` con el código de estado.
- **Archivos:** `frontend/src/lib/api.ts:158-181`, `frontend/src/lib/types.ts`.
- **Evidencia:** `frontend/src/test/api.test.ts` cubre el error estándar, un cuerpo que no es JSON y una falla de red.

**P1.5 · Datos mostrados en pantalla**
- **Cómo funciona:** `WaiterView` consulta las mesas y las órdenes cada 10 s y las muestra como cuadrícula con estados de carga, error y vacío.
- **Archivos:** `frontend/src/app/(authenticated)/waiter/waiter-view.tsx:66-76, 143-169`, `frontend/src/components/shared/query-state.tsx`.
- **Observación:** en móvil la cuadrícula pasa a una columna (`grid-cols-1`).

**P2.1 · Método HTTP de envío**
- **Cómo funciona:** `POST /api/v1/orders` abre una orden en una mesa. Hay otros envíos: POST `/orders/{id}/items`, `/checkout` e `/inventory/movements`, y PUT y PATCH en el catálogo.
- **Archivos:** `backend/src/main/java/com/callejon9/order/web/OrderController.java:36`.

**P2.2 · Preparación y envío de los datos**
- **Cómo funciona:** el formulario arma `{tableId, guestCount}`, y `api.post` lo convierte con `JSON.stringify` y lo envía con `Content-Type: application/json` y la cookie de sesión.
- **Archivos:** `frontend/src/app/(authenticated)/waiter/open-table-dialog.tsx:57-68`, `frontend/src/lib/api.ts:130-144`.

**P2.4 · Procesamiento de la respuesta del servidor**
- **Cómo funciona:** al responder bien, el backend devuelve `OrderResponse` con el folio. La app refresca la lista de mesas y órdenes y navega a la orden.
- **Archivos:** `open-table-dialog.tsx:43-49`.

**P2.5 · Mensajes de éxito o error**
- **Cómo funciona:**
  - Éxito: aviso `Mesa N ocupada. Orden F abierta.`
  - Error: aviso con el detalle del servidor, más una alerta dentro del diálogo. Por ejemplo, un 409 cuando dos meseros abren la misma mesa.
- **Archivos:** `open-table-dialog.tsx:46, 50-54, 90-95`.

**P3.1 · Captura de credenciales**
- **Cómo funciona:** el formulario pide el identificador del restaurante (slug), el correo y la contraseña, y los valida en el cliente antes de enviarlos.
- **Archivos:** `frontend/src/app/login/login-view.tsx:48-76, 140-197`, `frontend/src/lib/login-validation.ts`.
- **Observación:** pide un dato extra, el slug del restaurante. Conviene explicarlo en la ficha.

**P3.3 · Procesamiento de la respuesta de login**
- **Cómo funciona:**
  - **200:** el backend envía las cookies y el cuerpo `{id, nombre, rol}`.
  - **401:** sin cuerpo.
  - **400:** con un error por campo.
  - **429:** con `Retry-After`.
- **Archivos:** `backend/src/main/java/com/callejon9/auth/web/AuthController.java:60-82, 157-194`, `login-view.tsx:78-138`.

**P3.4 · Autenticación exitosa**
- **Cómo funciona:** el backend emite las cookies `httpOnly`. La app actualiza la sesión con `/auth/me` y redirige según el rol (por ejemplo, ADMIN va a `/admin` y WAITER a `/waiter`).
- **Archivos:** `login-view.tsx:39-46`, `frontend/src/hooks/use-session.ts:12-18`.
- **Evidencia de pruebas:** `validCredentialsReturnAnHttpOnlyCookie` (pasó).

**P3.5 · Credenciales incorrectas o usuario no encontrado**
- **Cómo funciona:** el backend responde el mismo 401 si el restaurante no existe, si el correo no existe o si la contraseña es incorrecta. Es intencional, para no revelar qué cuentas existen. La app muestra *"Restaurante, correo o contraseña incorrectos."*
- **Archivos:** `backend/src/main/java/com/callejon9/auth/service/AuthService.java:65-116`, `login-view.tsx:26, 135`.
- **Evidencia de pruebas:** `wrongPasswordIsUnauthorized`, `unknownTenantSlugIsUnauthorized`, `credentialsFromAnotherTenantAreRejected` y `unauthorizedResponseHasNoBody` (todas pasaron).
- **Observación:** cumple como "respuesta equivalente", pero hay que justificarlo en la ficha.

**P3.7 · La contraseña no queda visible**
- **Cómo funciona:**
  - El campo es `type="password"`.
  - Si el login falla, se vacía (`login-view.tsx:69-74`). Si tiene éxito, la pantalla cambia y el formulario desaparece.
  - El token nunca está al alcance de JavaScript.
  - El backend no repite el cuerpo de la petición en errores ni en los logs (`GlobalExceptionHandler.java:61-70`).
- **Limitaciones:**
  - Si el usuario activó "Mostrar", la contraseña se ve en texto plano mientras la petición está en curso.
  - Ninguna prueba automatizada comprueba que el campo se vacíe.

**Seguridad · Tokens y sesiones**
- **Cómo funciona:**
  - El token de sesión (JWT) dura 15 minutos y va en una cookie `httpOnly` con `SameSite=Strict`.
  - El token de renovación cambia en cada uso y detecta si alguien lo reutiliza.
  - El cierre de sesión se hace en el servidor (campo `sid` del token).
  - En la base de datos solo se guarda el SHA-256 del token de renovación.
- **Archivos:** `backend/src/main/java/com/callejon9/auth/web/AuthCookies.java`, `backend/src/main/java/com/callejon9/auth/service/RefreshTokenService.java`, `frontend/src/lib/api.ts:60-156`.
- **Evidencia de pruebas:** las clases `RefreshTokenRotationTest` y `SessionRevocationTest` existen, pero no se corrieron en esta auditoría.

**Seguridad · Contraseñas**
- **Cómo funciona:** se guardan con BCrypt. Hay una política de contraseñas con una lista de contraseñas comunes y un límite de intentos fallidos por cuenta y por IP.
- **Archivos:** `backend/src/main/java/com/callejon9/config/SecurityConfig.java:214-217`, `backend/src/main/java/com/callejon9/auth/password/`, `backend/src/main/java/com/callejon9/auth/throttle/LoginAttemptLimiter.java`.
- **Evidencia de pruebas:** `LoginRateLimitTest` (5 pruebas, pasaron).

**Seguridad · Manejo seguro de errores**
- **Cómo funciona:** todos los errores salen en formato RFC 7807 con mensajes genéricos. No se exponen errores internos de la base de datos ni de Jackson. La pantalla de error global no muestra la traza.
- **Archivos:** `backend/src/main/java/com/callejon9/shared/error/GlobalExceptionHandler.java`, `frontend/src/app/error.tsx`.

**Seguridad · Cabeceras**
- **Cómo funciona:** CSP con nonce, `X-Frame-Options: DENY`, `nosniff` y `Referrer-Policy`, tanto en Spring como en Next.
- **Archivos:** `SecurityConfig.java:225-235`, `frontend/src/middleware.ts`, `frontend/src/lib/security-headers.ts`.
- **Evidencia de pruebas:** `SecurityHeadersTest` (pasó).

## 3. Actividades parcialmente implementadas

**P1.3 / P2.3 · Peticiones HTTP desde la app móvil hacia un servicio remoto**
- **Qué existe:** las peticiones se hacen con `fetch` desde el navegador. La interfaz se adapta a pantallas pequeñas: el menú lateral se cambia por una barra superior por debajo de 1280 px.
- **Qué falta:**
  - Una app móvil como tal (o, al menos, una PWA instalable).
  - Que el servicio sea remoto: hoy es `localhost`.

**P3.2 · Envío de credenciales por una conexión segura**
- **Qué existe:**
  - El envío es `POST /api/v1/auth/login` con cuerpo JSON.
  - `cookie-secure` está activo por defecto (`backend/src/main/resources/application.yml:57`).
  - Next envía la cabecera HSTS en producción (`frontend/next.config.ts:18-20`).
- **Qué falta:**
  - HTTPS de verdad: `application-demo.yml` y `docker-compose.yml` ponen `cookie-secure: false` y todo corre en `http://`.
  - Nada fuerza la redirección de HTTP a HTTPS.

**P3.6 · Errores de conexión y respuestas inesperadas**
- **Qué existe:**
  - Los errores 4xx y 5xx se muestran con un mensaje genérico.
  - Si el cuerpo de un error no es JSON, el cliente lo maneja (probado en `api.test.ts`).
  - El 429 tiene su propio mensaje.
- **Qué falta:** en `login-view.tsx:78` la alerta solo aparece cuando el error es un `ApiError`. Una falla de red (`TypeError`) o un 200 con cuerpo que no es JSON terminan sin ningún mensaje: el botón vuelve a "Ingresar" y solo se borra la contraseña. Esto se dedujo leyendo el código; no se probó en vivo.

**Seguridad · Secretos en el código**
- **Qué existe:**
  - No se encontraron claves privadas ni llaves de API.
  - `.env` está en `.gitignore`.
- **Qué falta:**
  - `application.yml:18,25,45` trae como valores por defecto un `JWT_SECRET` y contraseñas de base de datos de desarrollo, y `docker-compose.yml` repite los mismos valores. Si se despliega sin definir esas variables, la API firmaría los tokens con un secreto público.
  - La contraseña del super-administrador demo está publicada en `README.md:155` y en un comentario de `V6__seed_platform_admin.sql:37`.
  - La rama local **`feat/cal-15-production-profile`** (sin integrar a `main`) agrega un perfil `prod` que rechaza secretos inseguros.

**Ficha técnica**
- **Qué existe:** nombre del proyecto, endpoints, métodos HTTP, mecanismo de autenticación y medidas de seguridad, todo documentado y verificable.
- **Qué falta:**
  - La URL pública de la API.
  - Los integrantes confirmados.
  - Juntar todo en un solo documento.

## 4. Actividades no implementadas

1. **Cliente móvil.** No hay proyecto nativo ni PWA (en `frontend/public/` solo hay SVG de plantilla).
   - **Opción mínima:** convertir la web en PWA con `frontend/src/app/manifest.ts`, íconos y quizá un service worker básico.
   - **Opción más completa:** empaquetarla con Capacitor, o hacer un cliente nativo que consuma la misma API. Ojo: la autenticación por cookies `SameSite=Strict` está pensada para el navegador; un cliente nativo necesitaría ajustes.
2. **Despliegue en la nube.** No hay Terraform, manifiestos ni configuración para alojar la aplicación.
   - **Base para hacerlo:** `docker-compose.yml` y los Dockerfile, más las ramas sin integrar `feat/cal-17-production-images` (imágenes de producción sin root) y `feat/cal-22-health-probes` (sondas de salud).
   - **Destino natural:** Azure Container Apps o App Service, en la suscripción Azure for Students que ya se usa. Ambos dan HTTPS por defecto.
3. **HTTPS en ejecución.** Depende del punto 2. Además, una vez desplegado, hay que definir `AUTH_COOKIE_SECURE=true` y `APP_ALLOWED_ORIGINS` con el dominio real.

## 5. Matriz de cumplimiento

| Requisito | Estado | Evidencia en el proyecto | Trabajo pendiente |
|---|---|---|---|
| P1.1 API identificada | Implementado | API REST propia `/api/v1`, Swagger, `next.config.ts:33` | Documentar la URL pública |
| Servicio alojado en la nube | No implementado | Solo `localhost` o `docker-compose`; Azure solo aloja Jenkins | Desplegar backend, frontend y base de datos |
| P1.2 Endpoint de consulta | Implementado | `GET /api/v1/tables` (`TableController.java:35`) | — |
| P1.3 Petición HTTP desde la app móvil | Parcial | `fetch` en `api.ts:130`; interfaz responsive | Cliente móvil (PWA o nativo) |
| P1.4 Procesar JSON | Implementado | `api.ts:158-181`; `api.test.ts` | — |
| P1.5 Mostrar los datos | Implementado | `waiter-view.tsx:143-169` | — |
| P2.1 POST/PUT | Implementado | `POST /api/v1/orders` (`OrderController.java:36`) | — |
| P2.2 Preparar y enviar los datos | Implementado | `open-table-dialog.tsx:57-68` | — |
| P2.3 Comunicación con un servicio remoto | Parcial | Funciona contra `localhost` | Despliegue en la nube |
| P2.4 Procesar la respuesta | Implementado | `open-table-dialog.tsx:43-49` | — |
| P2.5 Mensajes de éxito o error | Implementado | Avisos y alerta (`open-table-dialog.tsx:46-54, 90-95`) | — |
| P3.1 Captura de usuario y contraseña | Implementado | `login-view.tsx:140-197` | Explicar el slug en la ficha |
| P3.2 Envío por conexión segura | Parcial | Cookie `Secure` y HSTS listos pero apagados | HTTPS real |
| P3.3 Procesar la respuesta de login | Implementado | `AuthController.java:60-82`; `login-view.tsx:78-138` | — |
| P3.4 Autenticación exitosa | Implementado | Cookies `httpOnly` y redirección por rol; prueba pasó | — |
| P3.5 Credenciales incorrectas o usuario inexistente | Implementado | Mismo 401 para todo; 4 pruebas pasaron | Justificar en la ficha |
| P3.6 Errores de conexión e inesperados | Parcial | 4xx, 5xx y 429 sí se muestran | Mensaje para falla de red en el login |
| P3.7 Contraseña no visible después del envío | Implementado | `login-view.tsx:69-74, 182` | Opcional: ocultarla al enviar y agregar una prueba |
| Seg. HTTPS | No implementado | No hay TLS en ningún entorno | Despliegue con TLS |
| Seg. Tokens y sesiones | Implementado | JWT en cookie `httpOnly`, renovación, revocación | — |
| Seg. Protección de contraseñas | Implementado | BCrypt, política, límite de intentos | — |
| Seg. Sin secretos expuestos | Parcial | Valores de desarrollo como respaldo en `application.yml`; contraseña demo pública | Integrar CAL-15 y rotar la cuenta demo en la nube |
| Seg. Manejo seguro de errores | Implementado | `GlobalExceptionHandler.java` | — |
| Evidencia 1: app funcionando | No verificable | La app no estaba corriendo y el puerto 8080 está ocupado | Levantarla y capturar en vista móvil |
| Evidencia 2: código fuente | Implementado | Archivos listados en la sección 2 | — |
| Evidencia 3: ficha técnica | Parcial | La mayoría de los datos ya existe | URL, integrantes y redacción |

## 6. Evidencias y pruebas pendientes

**Se pueden obtener hoy, en local** (levantando el backend en otro puerto o liberando el 8080, y abriendo la web en modo dispositivo de DevTools o desde un teléfono en la misma red):

| Evidencia | Cómo obtenerla |
|---|---|
| Pantalla de autenticación | `/login` |
| Autenticación exitosa | Restaurante creado en `/signup` o cuenta demo; redirige según el rol |
| Consumo de datos | Pantalla `/waiter` con la cuadrícula de mesas |
| Envío de datos | Abrir una mesa (`POST /orders`) |
| Resultado de la operación | Aviso "Orden F abierta" |
| Manejo de un error | Contraseña incorrecta (alerta de 401) o varios fallos seguidos (429) |

**Requieren trabajo antes:**
- Capturas de la API en la nube y por HTTPS (candado del navegador, cookie con `Secure`).
- Captura en una app móvil propiamente dicha, si el profesor no acepta la web responsive.
- Captura de un error de conexión en el login: hoy no muestra mensaje.

**Evidencia 2 (código):** ya está disponible.
- Consumo: `frontend/src/lib/api.ts` y `waiter-view.tsx`.
- Envío: `open-table-dialog.tsx`.
- Autenticación: `login-view.tsx`, `AuthController.java` y `AuthService.java`.
- Errores: `GlobalExceptionHandler.java`, `frontend/src/app/providers.tsx` y `query-state.tsx`.

## 7. Plan de trabajo recomendado

**1. Indispensables**
- **Confirmar con el profesor si la web responsive cuenta como "aplicación móvil".**
  - Si no cuenta, la opción de menor costo es una PWA (`frontend/src/app/manifest.ts` e íconos en `frontend/public/`).
  - La siguiente es Capacitor.
  - Esta decisión va primero: cambia el resto del plan.
- **Desplegar en la nube con HTTPS.**
  - Primero integrar `feat/cal-15-production-profile`, `feat/cal-17-production-images` y `feat/cal-22-health-probes`.
  - Luego desplegar PostgreSQL gestionado, el backend y el frontend, por ejemplo en Azure Container Apps.
  - Variables necesarias: `JWT_SECRET`, `DB_*`, `AUTH_COOKIE_SECURE=true`, `APP_ALLOWED_ORIGINS` y `BACKEND_ORIGIN`.
  - Depende del presupuesto de Azure for Students y de la decisión anterior.

**2. Correcciones funcionales**
- **Login:** mostrar *"No se pudo conectar con el servidor"* cuando el error no sea un `ApiError` (`login-view.tsx:78-138`), y agregar su prueba en `frontend/src/test/`.
- **Opcional:** ocultar la contraseña al enviar (`setShowPassword(false)`) y probar que el campo se vacía.
- **Detalle menor:** el comentario de `frontend/src/lib/endpoints.ts:13` dice que `/auth/me` no existe, pero sí existe (`AuthController.java:120`).

**3. Seguridad pendiente**
- Quitar los valores de desarrollo que sirven de respaldo para los secretos en producción, o depender del perfil `prod` de CAL-15.
- Cambiar la contraseña del super-administrador sembrado (`V6`) en el entorno de la nube, con una migración nueva o un procedimiento operativo. Nunca reescribiendo `V6`.
- Verificar después del despliegue: HSTS presente, cookies con `Secure`, y que HTTP redirige a HTTPS.

**4. Evidencias y ficha**
- Levantar la app (ojo con el conflicto del puerto 8080) y tomar las 6 capturas en vista móvil.
- Redactar la ficha:
  - **Servicio:** API REST propia Callejón 9.
  - **Consulta:** `GET /api/v1/tables`.
  - **Envío:** `POST /api/v1/orders`.
  - **Autenticación:** `POST /api/v1/auth/login` con JWT en cookie `httpOnly`.
  - **Seguridad:** BCrypt, límite de intentos, respuesta 401 neutra, CSP y HSTS.
- Correr `.\mvnw.cmd -B verify` completo y `pnpm lint`, `pnpm exec tsc --noEmit` y `pnpm build` antes de entregar.

## Información pendiente para cerrar el diagnóstico

1. **¿El profesor acepta la web responsive usada desde el teléfono como "app móvil"?** Es la decisión que más cambia el plan.
2. **Integrantes.** Los autores de commits son Ludwin García, Octavio Duarte, Valeria Mercado Serrano, Braulio Hernández Mejía y el usuario `Duvo050611`; falta confirmar nombres y quién entra en la ficha.
3. **¿Qué proveedor de nube se puede usar?** Por ejemplo, si el saldo de Azure for Students alcanza para la base de datos y los contenedores.
4. **¿Se integran a `main` las ramas `feat/cal-15`, `cal-17` y `cal-22`?** Contienen trabajo de producción revisado pero sin integrar.
