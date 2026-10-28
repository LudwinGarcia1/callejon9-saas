# Evidencia de prueba · CAL-135 — SIT · Usuarios y roles

> **v1.** Ejecución completa, sin defectos; pendiente de revisión por otro integrante (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-135](https://linear.app/callejon19/issue/CAL-135) — SIT · Usuarios y roles |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Usuarios y roles |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | **No confirmado.** La interfaz publicada en `restohub` es anterior a `1efb78c` (2026-10-19 00:43, «unificar el nombre del producto como RestoHub»), así que no es `ea9b5c1`. El 2026-10-21 a las 19:34 el login mostraba la pestaña «Callejon 9» y el prefijo «c9.mx /» ([`../CAL-134-alta-restaurantes-planes/capturas/C01-P03-login-redirigido.png`](../CAL-134-alta-restaurantes-planes/capturas/C01-P03-login-redirigido.png)), y el 2026-10-22 a las 10:05 `/platform` decía «Planes disponibles para los restaurantes de Callejón 9» ([`../CAL-134-alta-restaurantes-planes/capturas/C03-P02-buscar-restaurante.png`](../CAL-134-alta-restaurantes-planes/capturas/C03-P02-buscar-restaurante.png)). `1efb78c` cambió esos textos a «RestoHub» y quitó el prefijo. Quien desplegó `restohub` no lo ha confirmado, y el recurso no está en la suscripción de Azure del ejecutor |
| Commit de la app móvil | `68e6e42` (`main`), servido por Metro desde esta computadora con `EXPO_PUBLIC_API_URL` apuntando a la API de Azure; `mobile/` sin cambios locales |
| Registro de despliegue | Comentario «Despliegue» en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) del 2026-10-21 00:56 (Braulio Hernández), `ea9b5c1` |
| Ejecución de CI | No aplica |
| Equipo de trabajo | Laptop Lenovo IdeaPad 3 15IIL05 (modelo 81WE) con Windows 11 Home 25H2, compilación 26200.9711, 64 bits; Intel Core i5-1035G1 a 1.00 GHz, 12 GB de RAM (11.7 GB utilizables) e Intel UHD Graphics. Es la computadora de los dos navegadores, Metro (que sirve la app al teléfono) y el observador ([`capturas/entorno_trabajo_laptop.png`](capturas/entorno_trabajo_laptop.png), 2026-10-21 22:41) |
| Navegador / dispositivo | Administrador en Google Chrome 155.0.8059.86 (64 bits); usuario nuevo en Microsoft Edge 155.0.4283.62 (64 bits), cada uno con su propia sesión. Teléfono Redmi Note 12S, MIUI Global 14.0.4, Android 13, con Expo Go 57.0.9 ([`capturas/PREP-01-dispositivo.jpeg`](capturas/PREP-01-dispositivo.jpeg)). Observador: Node 22 |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v1 · 2026-10-21 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Braulio Hernández Mejía | Asignado en Linear |
| Ejecutor de la prueba | Braulio Hernández Mejía | Capturas en Chrome, Edge y el teléfono; observador en la terminal |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-118) | 2026-10-20 | 2026-10-21 | 2026-10-20 | | |
| Esta tarea (CAL-135) | 2026-10-20 | 2026-10-21 | 2026-10-21 16:54 | | |
| Preparación del entorno y datos | 2026-10-21 | 2026-10-21 | 2026-10-21 16:54 | 2026-10-21 18:16 | Instalación de dependencias de la app móvil y arranque de Metro contra Azure; dos intentos de alta antes del usuario definitivo (ver C01, observaciones) |
| Ejecución de los casos | 2026-10-21 | 2026-10-21 | 2026-10-21 18:16 | 2026-10-21 18:58 | — |
| Re-prueba de defectos | | | | | No aplica |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que un usuario creado por el administrador en la web puede entrar en la web y en la app móvil, y que al desactivarlo pierde todas sus sesiones abiertas, incluida su conexión en tiempo real.

**Fuera de alcance.** Las reglas de negocio de usuarios que ya cubren las pruebas del backend (`UserControllerTest`): no desactivarse a sí mismo, no desactivar al último administrador, rechazo de `SUPER_ADMIN`, unicidad de correo.

**Lo que dice el código antes de ejecutar.** Revisado en `ea9b5c1`:

- `PATCH /api/v1/users/{id}` con `{"active": false}` revoca todas las sesiones del usuario (`refreshTokenService.revokeAllSessionsOf`). Desde ese momento, su access token ya no sirve aunque no haya vencido (`AccessTokenVerifier`), y la renovación falla.
- La app móvil, ante un `401` que no puede renovar, vuelve al login con «Tu sesión expiró. Inicia sesión de nuevo.» (`App.tsx`).
- `RevokedSessionSweeper` cierra las conexiones `/ws` de sesiones revocadas con código `1008` y motivo «Sesion revocada», a lo más 30 s después (`app.realtime.session-check-interval`).
- Ni la web ni la app abren `/ws` (ver CAL-140). La conexión en tiempo real del usuario se abre con `realtime/observador-ws.mjs` de CAL-140, que entra como lo haría un cliente nativo.
- El límite de usuarios del plan cuenta también a los inactivos (`PlanLimitService.assertCanAddUser` usa `countByTenantId`).

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Usuario creado por el administrador en la web inicia sesión en la web y en la app móvil | C01 |
| 2. Desactivar al usuario con sesión abierta en la web y en el teléfono: ambas sesiones dejan de funcionar y su conexión en tiempo real se cierra | C02 |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido con `.\scripts\azure-backend-power.ps1 -Action Start` y `/actuator/health` en `UP` con liveness y readiness — [`respuestas/health.txt`](respuestas/health.txt), 2026-10-21 16:54
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Commit desplegado de la API: `ea9b5c1`, igual al del registro de despliegue
- [x] El restaurante `pruebas` tiene cupo para un usuario más: tenía 6 usuarios antes del alta definitiva y el plan lo permitió (`201`)
- [x] App móvil en Expo Go apuntando a la API de Azure: la pantalla de login muestra «Conexión cifrada (HTTPS) app-callejon9-api-prod-3878c6.azurewebsites.net»
- [x] Node 22 o posterior para el observador de CAL-140
- [ ] Al terminar: `.\scripts\azure-backend-power.ps1 -Action Stop`

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Administrador | `admin@pruebas.com` («Administrador de sucursal», ADMIN) | Alta de usuarios de prueba del 2026-10-16 |
| Usuario nuevo | `sit-mesero-10211827@pruebas.com`, «SIT-Mesero-Prueba-2», rol Mesero, id `0bea2014-8749-4be0-a3d7-40ce140ead09` | C01 paso 2; contraseña generada para la prueba, guardada fuera del repositorio |

## 6. Casos de prueba

### C01 — El usuario creado por el administrador entra en la web y en el teléfono

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | ADMIN (Chrome), luego el WAITER nuevo (Edge y teléfono) |
| Inicio / fin de ejecución | 2026-10-21 18:16 / 2026-10-21 18:35 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Sesión del administrador en `/admin`.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | En `/admin`, tocar «Nuevo usuario» | — | Diálogo «Nuevo usuario» | 18:16 · Diálogo «Nuevo usuario · Da de alta a un miembro del equipo del restaurante» con nombre, correo, contraseña con sus 4 reglas y rol «Mesero» | [`capturas/C01-P01-dialogo.png`](capturas/C01-P01-dialogo.png) |
| 2 | Llenar nombre, correo, contraseña y rol Mesero, y tocar «Crear usuario» con DevTools → Network abierto | `sit-mesero-10211827@pruebas.com`, «SIT-Mesero-Prueba-2», Mesero | `POST /api/v1/users` → `201`; el usuario aparece en la lista, activo y con rol Mesero | 18:27 · Formulario lleno, contraseña oculta y las 4 reglas cumplidas. 18:27:31 · `POST /api/v1/users` → **`201 Created`** con `"role":"WAITER","active":true`. La lista con el usuario «SIT-Mesero-Prueba-2» en **ACTIVO** y rol MESERO, y el `201` en Network, aparecen en la captura de C02 paso 2 (18:41) | [`capturas/C01-P02-usuario-creado.png`](capturas/C01-P02-usuario-creado.png), [`respuestas/C01-crear-usuario.txt`](respuestas/C01-crear-usuario.txt), [`capturas/C02-P02-desactivado.png`](capturas/C02-P02-desactivado.png) |
| 3 | En otro navegador (Edge), entrar en `/login?slug=pruebas` con el usuario nuevo | `sit-mesero-10211827@pruebas.com` | Entra a `/waiter` | 18:32 · `/waiter` con las 12 mesas y, abajo a la izquierda, «SIT-Mesero-Prueba-2 · MESERO» | [`capturas/C01-P03-web-dentro.png`](capturas/C01-P03-web-dentro.png) |
| 4 | En el teléfono, entrar con el restaurante `pruebas` y el usuario nuevo | `sit-mesero-10211827@pruebas.com` | «Mesas» con el aviso «Autenticación exitosa» | 18:35 · «Mesas · SIT-Mesero-Prueba-2», aviso «Autenticación exitosa · Sesión iniciada como SIT-Mesero-Prueba-2. La sesión se guarda en una cookie segura del sistema.», «12 mesas · 1 libres» | [`capturas/C01-P04-telefono-dentro.jpeg`](capturas/C01-P04-telefono-dentro.jpeg) |

**Respuesta HTTP literal.** [`respuestas/C01-crear-usuario.txt`](respuestas/C01-crear-usuario.txt): `POST /api/v1/users` → `201 Created` a las 18:27:31 (`date: Thu, 22 Oct 2026 00:27:31 GMT`), copiada de DevTools por el proxy del frontend. No se copió el cuerpo de la petición porque lleva la contraseña.

**Observaciones.**

- Antes del alta definitiva hubo dos intentos que dejaron usuarios en el restaurante: `sit-mesero-10211807@pruebas.com` («SIT-Mesero-Prueba», ya **inactivo** a las 18:16) y `sit-mesero-102111821@pruebas.com` («SIT-Mesero-Prueba», con un «1» de más en el correo, **activo**). Ambos se ven en [`capturas/C02-P02-desactivado.png`](capturas/C02-P02-desactivado.png). No forman parte de la prueba: C01 y C02 usan solo `sit-mesero-10211827@pruebas.com`. El de `102111821` sigue activo.
- La captura del paso 2 se tomó antes de tocar «Crear usuario»: la petición de Network que muestra es la `GET /users` (`200`), no la `POST`.
- El usuario nuevo entró en la web desde Edge en lugar de una ventana de incógnito de Chrome. Cumple el mismo fin: una sesión separada de la del administrador.

### C02 — Al desactivar al usuario se cortan sus sesiones y su conexión en tiempo real

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Alta |
| Rol que ejecuta | ADMIN (Chrome) y el WAITER de C01 (Edge, teléfono y observador) |
| Inicio / fin de ejecución | 2026-10-21 18:39 / 2026-10-21 18:58 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Sesiones de C01 abiertas en Edge y en el teléfono.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Abrir la conexión en tiempo real del usuario: desde `docs/pruebas/sit/CAL-140-cocina-tiempo-real/realtime/`, `node observador-ws.mjs --email sit-mesero-10211827@pruebas.com --password-env C9_SIT_USER_PASSWORD --minutos 10 --salida ..\..\CAL-135-usuarios-roles\respuestas\C02-observador-ws.txt` | Usuario de C01 | `ws abierto` y `stomp CONNECTED` | 18:39:06 · Login `200`, rol WAITER. 18:39:07 · `/auth/me` `200` («SIT-Mesero-Prueba-2»), `ws abierto` (101 Switching Protocols) y `stomp CONNECTED` versión 1.2 | [`respuestas/C02-observador-ws.txt`](respuestas/C02-observador-ws.txt), [`capturas/C02-P05-observador-terminal.png`](capturas/C02-P05-observador-terminal.png) |
| 2 | En `/admin`, tocar «Desactivar» en el usuario nuevo, con DevTools → Network abierto | Usuario de C01 | `PATCH /api/v1/users/<id>` → `200` con `"active": false`; la fila queda inactiva | 18:41 · La fila de «SIT-Mesero-Prueba-2» muestra «Guardando…» y la `PATCH /users/0bea2014-…` en curso. 18:41:10 · `PATCH` → **`200 OK`** con `"active":false` | [`capturas/C02-P02-desactivado.png`](capturas/C02-P02-desactivado.png), [`respuestas/C02-desactivar.txt`](respuestas/C02-desactivar.txt) |
| 3 | En Edge, recargar `/waiter` | — | La sesión ya no sirve: vuelve a `/login` | 18:47 · Redirige a `/login?expired=1` con el aviso «Sesión expirada · Tu sesión expiró por inactividad.» | [`capturas/C02-P03-web-sin-sesion.png`](capturas/C02-P03-web-sin-sesion.png) |
| 4 | En el teléfono, deslizar para recargar las mesas | — | Vuelve al login con «Tu sesión expiró. Inicia sesión de nuevo.» | 18:48 · Pantalla de login de RestoHub con «Tu sesión expiró. Inicia sesión de nuevo.» y «Conexión cifrada (HTTPS) app-callejon9-api-prod-3878c6.azurewebsites.net» | [`capturas/C02-P04-telefono-sin-sesion.jpeg`](capturas/C02-P04-telefono-sin-sesion.jpeg) |
| 5 | Revisar el registro del observador | — | `ws cerrado código 1008, motivo «Sesion revocada»` a lo más 30 s después de la hora del paso 2 | 18:41:24 · **`ws cerrado código 1008, motivo «Sesion revocada»`**, 14 s después de la `PATCH` (18:41:10) | [`respuestas/C02-observador-ws.txt`](respuestas/C02-observador-ws.txt), [`capturas/C02-P05-observador-terminal.png`](capturas/C02-P05-observador-terminal.png) |
| 6 | Intentar entrar de nuevo con el usuario desactivado: `curl.exe -i -X POST …/api/v1/auth/login` con un JSON temporal fuera del repositorio | Usuario de C01, con la misma contraseña que usó el observador en el paso 1 | `401` | 18:58:27 · **`HTTP/1.1 401 Unauthorized`**, `Content-Length: 0` | [`respuestas/C02-login-desactivado.txt`](respuestas/C02-login-desactivado.txt) |

**Respuesta HTTP literal.** [`respuestas/C02-desactivar.txt`](respuestas/C02-desactivar.txt) (`PATCH`, copiada de DevTools) y [`respuestas/C02-login-desactivado.txt`](respuestas/C02-login-desactivado.txt) (salida completa de `curl -i`).

**Observaciones.**

- Entre la desactivación (18:41:10, cabecera `date` de la `PATCH`) y el cierre del canal en tiempo real (18:41:24) pasaron **14 s**, dentro del máximo de 30 s.
- El paso 6 da `401` con la contraseña que funcionó en el paso 1, así que el rechazo se debe a la desactivación. La API responde igual que con una contraseña incorrecta, sin revelar que la cuenta existe.
- La primera línea de `C02-login-desactivado.txt` decía 18:53:09, la hora en que se preparó el comando; la petición se envió a las 18:58:27, según su cabecera `Date`. Se corrigió el encabezado y se completó el correo en la línea `$ curl`; la respuesta no se tocó.
- En la web, el aviso dice «Tu sesión expiró por inactividad.» aunque la causa fue la desactivación de la cuenta. No impide el criterio, pero el texto no es exacto (ver sección 7).
- La conexión en tiempo real es la del observador, porque ni la web ni la app abren `/ws` (CAL-172).

## 7. Defectos encontrados

No se encontraron defectos en la integración de usuarios y roles.

| Hallazgo | Detalle | Seguimiento |
|---|---|---|
| Aviso impreciso en la web | Tras la desactivación, `/login?expired=1` dice «Tu sesión expiró por inactividad.» | Opcional: un texto que no atribuya la causa |
| Usuarios de intentos previos | `sit-mesero-10211807@pruebas.com` (inactivo) y `sit-mesero-102111821@pruebas.com` en el restaurante `pruebas` | No afectan la prueba. El de `102111821` sigue activo; ambos cuentan para el límite de usuarios del plan |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 2 | 2 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- La conexión en tiempo real se observa con un cliente nativo (`CAL-140-cocina-tiempo-real/realtime/observador-ws.mjs`), porque ni la web ni la app la abren.
- El usuario nuevo usó Edge en lugar de una ventana de incógnito de Chrome.
- C01 paso 2: la captura es del formulario antes de enviarlo; la lista con el usuario activo está en la captura de C02 paso 2.
- C02 paso 2: la captura muestra la fila en «Guardando…»; el estado final `"active":false` consta en la respuesta `PATCH`, y su efecto, en los pasos 3 a 6.
- Quedan en el restaurante `pruebas`: `sit-mesero-10211827@pruebas.com` (el de la prueba) y `sit-mesero-10211807@pruebas.com`, desactivados, y `sit-mesero-102111821@pruebas.com`, de un intento previo, activo. No intervienen en la prueba.
- `respuestas/C02-desactivar.txt` corresponde a la `PATCH` del usuario de la prueba (`0bea2014-…`, «SIT-Mesero-Prueba-2»), pero dos nombres de cabecera tienen errores de tecleo que DevTools no produce («Referrer poicy» y «caclhe-control»). Se conservan tal cual. El código de estado, la cabecera `date` y el cuerpo de la respuesta coinciden con la captura del paso 2 y con el cierre del observador.
- La captura del equipo de trabajo (`entorno_trabajo_laptop.png`) es de la configuración de Windows, tomada el 2026-10-21 a las 22:41. Se taparon con rectángulos grises la cuenta de Windows, el nombre del equipo, el Device ID, el Product ID y el número de serie, por ser datos personales.

**Conclusión del ejecutor.** Los dos criterios de CAL-135 se cumplen contra el commit `ea9b5c1` desplegado en Azure. El usuario creado por el administrador desde la web (`201`) entra en la web y en la app móvil. Al desactivarlo (`200`, `"active":false`), su sesión web vuelve al login, la app muestra «Tu sesión expiró», su conexión en tiempo real se cierra con `1008 «Sesion revocada»` a los 14 s y ya no puede iniciar sesión (`401`).

## 9. Anexo SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Frontend ↔ API (proxy `/api/v1/*`) | C01, C02 | `respuestas/C01-crear-usuario.txt`, `respuestas/C02-desactivar.txt` |
| App móvil ↔ API | C01, C02 | `capturas/C01-P04-telefono-dentro.jpeg`, `capturas/C02-P04-telefono-sin-sesion.jpeg` |
| API ↔ PostgreSQL (revocación de sesiones) | C02 | `respuestas/C02-login-desactivado.txt` |
| API ↔ tiempo real (`/ws`) | C02 | `respuestas/C02-observador-ws.txt` |

## 10. Revisión

*La llena el revisor, que no puede ser quien ejecutó la prueba.*

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas muestran la URL y la hora y coinciden con los pasos
- [ ] Las respuestas HTTP coinciden con el comportamiento real del código
- [ ] El commit probado corresponde al registro de despliegue
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las capturas y respuestas no fueron editadas salvo para ocultar secretos y datos personales, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** Braulio Hernández Mejía · **Fecha:** 2026-10-21
