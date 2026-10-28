# Evidencia de prueba · CAL-133 — SIT · Autenticación y sesiones

> **v1.** Ejecución completa; pendiente de revisión por otro integrante (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-133](https://linear.app/callejon19/issue/CAL-133/sit-autenticacion-y-sesiones) — SIT · Autenticación y sesiones |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Autenticación y sesiones (`auth`, `/login`, cookies de sesión, `LoginAttemptLimiter`, `ClientIp`) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` (la web la consume por el proxy `/api/v1/*` de Next.js) |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`); sin cambios de backend en `main` al ejecutar |
| Registro de despliegue | Comentario «Despliegue» de Braulio Hernández en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat), 2026-10-21 00:56 |
| Commit del frontend | *Sin registro de despliegue* (ver sección 8) |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | Google Chrome 155 automatizado con Playwright 1.56.1 en Windows 11; en C05 fase C, el teléfono de otro integrante desde su propia red |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v1 · 2026-10-21 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Octavio Duarte | Asignado en Linear |
| Ejecutor de la prueba | Octavio Duarte | Scripts de Playwright de esta carpeta |
| Apoyo en C05 fase C | Braulio Hernández | Intento de login desde otra red; captura `C05-P04` |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-118) | 2026-10-20 | 2026-10-21 | 2026-10-20 | | |
| Esta tarea (CAL-133) | 2026-10-20 | 2026-10-21 | 2026-10-21 13:28 | | |
| Preparación del entorno y datos | 2026-10-21 | 2026-10-21 | 2026-10-21 13:28 | 2026-10-21 13:31 | La API estaba apagada; se encendió (`UP` a las 13:31) |
| Ejecución de los casos | 2026-10-21 | 2026-10-21 | 2026-10-21 13:31 | 2026-10-21 14:01 | C05 se extendió para comprobar `ClientIp` desde otra red |
| Re-prueba de defectos | | | | | |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que el inicio y el cierre de sesión, la renovación del token y el freno contra fuerza bruta funcionan en Azure a través del frontend publicado, y comprobar cómo identifica `ClientIp` la IP del cliente detrás de App Service.

**Fuera de alcance.** El segundo factor (CAL-38), el cambio y la recuperación de contraseña (CAL-25, CAL-33) y la revocación de sesiones al desactivar un usuario (CAL-95).

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Login por el frontend publicado: 200 y cookies en el dominio del frontend, httpOnly y Secure | C01 |
| 2. Login con una cuenta inexistente: 401 por el proxy de Next.js | C02 |
| 3. Sesión de más de 15 minutos: el frontend renueva el token sin pedir login | C03 |
| 4. Logout: el token anterior deja de funcionar (401) | C04 |
| 5. Seis contraseñas erróneas de una cuenta por el proxy: 429; confirmar cómo interpreta `ClientIp` la IP real | C05 |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido y `/actuator/health` en `UP` — [`respuestas/health.txt`](respuestas/health.txt), 2026-10-21 13:31
- [x] Commit desplegado verificado con el registro de despliegue: `ea9b5c1`; `git log ea9b5c1..origin/main -- backend` vacío al iniciar
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Política vigente en `application.yml`: token de acceso de 15 minutos; 5 fallos por cuenta o 20 por IP en 15 minutos → 429

**Datos de prueba**

| Dato | Valor | Uso |
|---|---|---|
| `mesero@pruebas.com` (WAITER) | Contraseña de las cuentas de prueba | C01 y C04 |
| `cocina@pruebas.com` (KITCHEN) | Contraseña de las cuentas de prueba | C03 |
| `mesero2@pruebas.com` (WAITER) | Contraseñas erróneas a propósito | C05, para no bloquear la cuenta principal |
| `noexiste@pruebas.com` y `ip-*@noexiste.test` | Cuentas inexistentes | C02 y C05 fases A a C |
| `caja@pruebas.com` (CASHIER) | Contraseña de las cuentas de prueba | C05 fase C, desde otra red |

## 6. Casos de prueba

### C01 — El login por el frontend publicado deja la sesión en cookies seguras

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (`mesero@pruebas.com`) |
| Inicio / fin de ejecución | 2026-10-21 13:31 / 2026-10-21 13:31 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Abrir `/login?slug=pruebas` y llenar el formulario | `mesero@pruebas.com` | Formulario con el restaurante prellenado | 13:31:40 · Restaurante «pruebas», correo y contraseña enmascarada | [`C01-P01`](capturas/C01-P01-formulario.png) |
| 2 | Tocar «Ingresar» | — | `POST /api/v1/auth/login` → 200 con cookies de sesión | 13:31:41 · **200**; `Set-Cookie: access_token=…; Path=/; Secure; HttpOnly; SameSite=Strict` y `refresh_token=…; Path=/api/v1/auth; Secure; HttpOnly; SameSite=Strict` | [`C01-login.txt`](respuestas/C01-login.txt) |
| 3 | Revisar las cookies del navegador | — | Cookies en el dominio del frontend, no legibles por JavaScript | 13:31:41 · `access_token` en `restohub.azurewebsites.net`, `HttpOnly`, `Secure`, `SameSite=Strict`; `document.cookie` no expone ningún token | [`C01-cookies-del-navegador.md`](respuestas/C01-cookies-del-navegador.md) |
| 4 | Ver la pantalla tras el login | — | Entra a la vista de su rol | 13:31:41 · `/waiter` con las mesas | [`C01-P02`](capturas/C01-P02-sesion-iniciada.png) |

**Observaciones.** La cookie `refresh_token` no aparece en la lista del navegador porque está acotada a `/api/v1/auth`; sus atributos se ven en la respuesta literal. Ambas cookies se emiten con `Max-Age` de 7 días, la vida máxima de la sesión; el token de acceso que contienen caduca a los 15 minutos (C03).

### C02 — Una cuenta inexistente recibe 401 por el proxy

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Media |
| Rol que ejecuta | Sin cuenta (`noexiste@pruebas.com`) |
| Inicio / fin de ejecución | 2026-10-21 13:31 / 2026-10-21 13:31 |
| Estado | ✅ Aprobado |
| Defecto vinculado | [CAL-168](https://linear.app/callejon19/issue/CAL-168) (aviso falso de «Sesión expirada»; el 401 se cumple) |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Iniciar sesión con una cuenta que no existe | `noexiste@pruebas.com` | **401** por el proxy | 13:31:48 · **401**, sin cuerpo (`content-length: 0`) | [`C02-cuenta-inexistente.txt`](respuestas/C02-cuenta-inexistente.txt) |
| 2 | Ver el mensaje al usuario | — | «Restaurante, correo o contraseña incorrectos.» | 13:31:49 · Ese mensaje aparece, pero también «Sesión expirada · Tu sesión expiró por inactividad.»; la URL cambia a `/login?expired=1` y el restaurante se vacía | [`C02-P01`](capturas/C02-P01-mensaje-al-usuario.png) |

**Observaciones.** El 401 cumple el criterio. El aviso falso viene del manejador global de `providers.tsx`, que trata cualquier 401 de una mutation —incluido el del propio login— como sesión vencida. Se registró como [CAL-168](https://linear.app/callejon19/issue/CAL-168), severidad media. El mensaje no revela si la cuenta existe.

### C03 — Una sesión de más de 15 minutos se renueva sin pedir login

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 |
| Prioridad | Alta |
| Rol que ejecuta | KITCHEN (`cocina@pruebas.com`) |
| Inicio / fin de ejecución | 2026-10-21 13:31 / 2026-10-21 13:48 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** La cocina web consulta las órdenes cada 5 segundos, así que hace peticiones sin intervención del usuario.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Iniciar sesión y abrir `/kitchen` | `cocina@pruebas.com` | Tablero cargado | 13:31:31 · login **200**; 13:31:32 tablero | [`C03-P01`](capturas/C03-P01-cocina-al-iniciar.png) |
| 2 | Dejar la pantalla abierta 17 minutos | — | Al caducar el token (15 min), una petición recibe 401 y el frontend renueva solo | 13:46:33 · `GET /api/v1/kitchen/orders` → **401**; 13:46:34 · `POST /api/v1/auth/refresh` → **204** y la consulta se repite | [`C03-registro-sesion.txt`](respuestas/C03-registro-sesion.txt) |
| 3 | Revisar la pantalla después de renovar | — | Sigue en `/kitchen` sin pedir login | 13:46:42 · Tablero visible, sin redirección | [`C03-P02`](capturas/C03-P02-cocina-tras-renovar.png) |
| 4 | Revisar al final | — | Sesión viva | 13:48:42 · Sigue en `/kitchen`; 193 consultas correctas y un solo 401 en toda la sesión | [`C03-P03`](capturas/C03-P03-cocina-al-final.png) |

**Observaciones.** La renovación ocurrió mientras el login estaba bloqueado por C05: el freno solo afecta a `POST /auth/login`, no a las sesiones abiertas.

### C04 — El logout invalida el token anterior

| Campo | Valor |
|---|---|
| Criterio cubierto | 4 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (`mesero@pruebas.com`, sesión de C01) |
| Inicio / fin de ejecución | 2026-10-21 13:31 / 2026-10-21 13:31 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Consultar `/api/v1/auth/me` con la sesión de C01 | — | 200 | 13:31:42 · **200** | [`C04-P01`](capturas/C04-P01-antes-del-logout.png) |
| 2 | Tocar «Cerrar sesión» | — | `POST /api/v1/auth/logout` → 204; cookies borradas; vuelve al login | 13:31:43 · **204** con `Set-Cookie … Max-Age=0` para `access_token` y `refresh_token`; pantalla de login | [`C04-logout.txt`](respuestas/C04-logout.txt), [`C04-P02`](capturas/C04-P02-despues-del-logout.png) |
| 3 | En otro navegador, presentar el `access_token` de antes del logout | Token guardado solo en memoria del script | **401** | 13:31:46 · `GET /api/v1/auth/me` → **401** | [`C04-token-anterior-tras-logout.txt`](respuestas/C04-token-anterior-tras-logout.txt) |

**Observaciones.** El token no se escribió en disco: el script lo conserva en memoria para el paso 3. Que el servidor lo rechace aunque no haya caducado muestra que el logout lo revoca (CAL-94), no solo borra la cookie.

### C05 — Seis contraseñas erróneas bloquean la cuenta; comprobación de `ClientIp`

| Campo | Valor |
|---|---|
| Criterio cubierto | 5 |
| Prioridad | Alta |
| Rol que ejecuta | `mesero2@pruebas.com` (bloqueo por cuenta); cuentas inexistentes y `caja@pruebas.com` desde otra red (`ClientIp`) |
| Inicio / fin de ejecución | 2026-10-21 13:32 / 2026-10-21 14:01 |
| Estado | ✅ Aprobado |
| Defecto vinculado | [CAL-167](https://linear.app/callejon19/issue/CAL-167) (crítica: el límite por IP bloquea a todos los usuarios) |

**Parte 1 · Bloqueo por cuenta**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Cinco logins con contraseña errónea desde la interfaz | `mesero2@pruebas.com` | 401 en los cinco | 13:32:27 – 13:32:35 · **401** ×5 | [`C05-P01`](capturas/C05-P01-intento-5.png), [`C05-seis-intentos.txt`](respuestas/C05-seis-intentos.txt) |
| 2 | Sexto intento | — | **429** | 13:32:37 · **429** `problem+json`, «Demasiados intentos fallidos. Intenta de nuevo en 15 minutos.», `Retry-After: 891` | [`C05-P02`](capturas/C05-P02-intento-6-bloqueado.png) |
| 3 | Séptimo intento, para ver el aviso en pantalla | — | Aviso de bloqueo; el intento rechazado no alarga el bloqueo | 13:32:58 · **429**, `Retry-After: 870` (bajó 21 s, no se reinició); la pantalla muestra «Demasiados intentos» y conserva lo escrito | [`C05-P03`](capturas/C05-P03-aviso-de-bloqueo.png), [`C05-intento-7.txt`](respuestas/C05-intento-7.txt) |

**Parte 2 · Cómo identifica `ClientIp` al cliente**

`ClientIp` solo cree a `X-Forwarded-For` si la conexión llega de loopback o de una red privada; si no, usa la dirección de la conexión. Para saber qué llave usa el límite por IP (20 fallos en 15 minutos) se ejecutaron tres fases, con autorización del responsable del issue y aviso al equipo, porque podían bloquear el login de todos hasta 15 minutos.

| Fase | Acción | Resultado esperado si `ClientIp` ve al cliente | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| A | Desde una red, logins fallidos por el proxy con correos inexistentes distintos, hasta el primer 429 | 429 al pasar de 20 fallos de esta red | 13:40:54 – 13:40:58 · 401 en los intentos 1 a 14 y **429** en el 15, que es el fallo 21 contando C02 y C05; `Retry-After: 351` coincide con la salida del fallo de C02 (13:31:48) | [`C05-clientip-fase-a-proxy.txt`](respuestas/C05-clientip-fase-a-proxy.txt) |
| B | Desde la misma red, un login fallido **directo a la API** | 429 (misma red) | 13:40:58 · **429** «Intenta de nuevo en 6 minutos» | [`C05-clientip-fase-b-directo.txt`](respuestas/C05-clientip-fase-b-directo.txt) |
| C | Mantener el bloqueo (un fallo cada 3 s, 13:44 – 14:01) mientras **otro integrante, desde su propia red**, inicia sesión con una cuenta válida | **Entra**, porque su red no tiene fallos | 13:49 · **«Demasiados intentos fallidos. Intenta de nuevo en 7 minutos.»** en el teléfono de Braulio Hernández, en su red | [`C05-P04`](capturas/C05-P04-otra-red-bloqueada.png), [`C05-clientip-fase-c-bloqueo-sostenido.txt`](respuestas/C05-clientip-fase-c-bloqueo-sostenido.txt) |

**Conclusión de la parte 2.** El límite por IP no distingue clientes: los fallos de una red bloquearon el login de otra. La causa probable es que producción usa `forward-headers-strategy: none` y en App Service Linux la conexión llega desde una dirección interna de enlace local (169.254.x.x), que `ClientIp` no considera interna, así que toma esa dirección común a todos. Se registró como [CAL-167](https://linear.app/callejon19/issue/CAL-167), severidad crítica. El mismo `ClientIp` alimenta el límite de altas de restaurantes.

**Observaciones.** El caso cumple lo que pide el criterio: el sexto intento responde 429 y quedó confirmado cómo interpreta `ClientIp` la IP real; esa confirmación es el defecto CAL-167. El bloqueo sostenido se detuvo a las 14:01:33; el login general se liberó a más tardar a las 14:12.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| [CAL-167](https://linear.app/callejon19/issue/CAL-167) | C05 | Crítica | El límite por IP del login es global en Azure: 20 fallos de cualquiera bloquean el login de todos los restaurantes | Abierto | — |
| [CAL-168](https://linear.app/callejon19/issue/CAL-168) | C02 | Media | Un login fallido muestra «Sesión expirada», cambia a `/login?expired=1` y borra el restaurante | Abierto | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 5 | 5 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- El frontend no tiene registro de despliegue. Los criterios dependen de respuestas de la API, cuyo commit sí está registrado (`ea9b5c1`).
- La fase C de C05 usó el teléfono de otro integrante en su red doméstica; su captura la tomó él.
- C05 requirió bloquear el login general durante la prueba (13:40 – a lo más 14:12), con autorización y aviso al equipo.
- La causa de CAL-167 es una hipótesis respaldada por la configuración y el comportamiento observado; falta confirmarla con un registro de `getRemoteAddr()` en Azure.

**Conclusión del ejecutor.** Los cinco criterios de CAL-133 se cumplen contra `ea9b5c1` en Azure: el login por el frontend deja la sesión en cookies `HttpOnly`, `Secure` y `SameSite=Strict` del dominio del frontend; una cuenta inexistente recibe 401; la sesión se renueva sola al caducar el token de 15 minutos; el logout revoca el token; y seis contraseñas erróneas bloquean la cuenta con 429. La prueba encontró un defecto crítico, CAL-167: el límite por IP es común a todos los usuarios, así que cualquiera puede dejar sin login a todos los restaurantes. También un defecto medio de interfaz, CAL-168.

## 9. Anexo SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Navegador ↔ frontend (cookies del dominio publicado) | C01, C04 | `C01-cookies-del-navegador.md`, `C04-logout.txt` |
| Frontend ↔ API (proxy `/api/v1/*`) | C01 a C05 | Respuestas literales registradas desde el navegador |
| API detrás de App Service ↔ identificación del cliente | C05 | Fases A a C de `ClientIp` |

**Reproducir la prueba.** Los scripts están en [`playwright/`](playwright/): `sit-autenticacion.mjs` (etapas `basicos`, `sesion-larga` y `bloqueo`), `c05-aviso.mjs` (aviso de bloqueo en pantalla) y `c05-clientip.mjs` (fase A). Necesitan Playwright y Google Chrome, y leen la contraseña de `C9_TEST_PASSWORD`. La fase B es un `curl` directo a la API y la fase C un ciclo de logins fallidos cada 3 s por el proxy; ambos están transcritos al inicio de sus archivos de respuesta. **Las fases A a C bloquean el login de todos los usuarios hasta 15 minutos mientras CAL-167 siga abierto: avisa al equipo antes de repetirlas.**

## 10. Revisión

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas muestran la URL y la hora (o la banda de la captura automatizada) y coinciden con los pasos
- [ ] Las respuestas HTTP coinciden con el comportamiento real del código
- [ ] El commit probado corresponde al registro de despliegue
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las capturas y respuestas no fueron editadas salvo para ocultar secretos, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** Octavio Duarte · **Fecha:** 2026-10-21
