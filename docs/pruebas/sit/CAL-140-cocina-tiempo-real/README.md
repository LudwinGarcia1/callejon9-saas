# Evidencia de prueba · CAL-140 — SIT · Cocina y tiempo real

> **v1.** Ejecución completa; defectos registrados en Linear (CAL-169 a CAL-172); pendiente de revisión por otro integrante (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-140](https://linear.app/callejon19/issue/CAL-140) — SIT · Cocina y tiempo real |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Cocina y tiempo real |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | **No confirmado.** La interfaz publicada en `restohub` es anterior a `1efb78c` (2026-10-19 00:43, «unificar el nombre del producto como RestoHub»), así que no es `ea9b5c1`. El 2026-10-21 a las 19:34 el login mostraba la pestaña «Callejon 9» y el prefijo «c9.mx /» ([`../CAL-134-alta-restaurantes-planes/capturas/C01-P03-login-redirigido.png`](../CAL-134-alta-restaurantes-planes/capturas/C01-P03-login-redirigido.png)), y el 2026-10-22 a las 10:05 `/platform` decía «Planes disponibles para los restaurantes de Callejón 9» ([`../CAL-134-alta-restaurantes-planes/capturas/C03-P02-buscar-restaurante.png`](../CAL-134-alta-restaurantes-planes/capturas/C03-P02-buscar-restaurante.png)). `1efb78c` cambió esos textos a «RestoHub» y quitó el prefijo. Quien desplegó `restohub` no lo ha confirmado, y el recurso no está en la suscripción de Azure del ejecutor |
| Registro de despliegue | Comentario «Despliegue» en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) del 2026-10-21 00:56 (Braulio Hernández), `ea9b5c1` |
| Ejecución de CI | No aplica |
| Equipo de trabajo | Laptop Lenovo IdeaPad 3 15IIL05 (modelo 81WE) con Windows 11 Home 25H2, compilación 26200.9711, 64 bits; Intel Core i5-1035G1 a 1.00 GHz, 12 GB de RAM (11.7 GB utilizables) e Intel UHD Graphics. Es la computadora de los dos navegadores y el observador ([`capturas/entorno_trabajo_laptop.png`](capturas/entorno_trabajo_laptop.png), 2026-10-21 22:41) |
| Navegador / dispositivo | Mesero en Google Chrome 155.0.8059.86 (64 bits) y cocina en Microsoft Edge 155.0.4283.62 (64 bits), cada uno con su propia sesión. Observador: Node 22 en la misma computadora |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v1 · 2026-10-21 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Braulio Hernández Mejía | Asignado en Linear |
| Ejecutor de la prueba | Braulio Hernández Mejía | Capturas en el navegador y observador en la terminal |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-118) | 2026-10-20 | 2026-10-21 | 2026-10-20 | | |
| Esta tarea (CAL-140) | 2026-10-20 | 2026-10-21 | 2026-10-20 22:05 | | Reabierta en revisión: la evidencia anterior no correspondía a una ejecución real |
| Preparación del entorno y datos | 2026-10-21 | 2026-10-21 | 2026-10-21 13:05 | 2026-10-21 13:14 | La API estaba apagada; se encendió con `azure-backend-power.ps1 -Action Start` |
| Ejecución de los casos | 2026-10-21 | 2026-10-21 | 2026-10-21 13:14 | 2026-10-21 16:17 | Pausa de 13:51 a 13:56 por el bloqueo del login (ver C03, observaciones); C02 pasos 3 a 6 se completaron a las 16:10 |
| Re-prueba de defectos | | | | | |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que un pedido enviado por el mesero llega a la cocina desplegada sin recargar, que los cambios de cocina llegan de vuelta al mesero, y verificar si el canal en tiempo real (`/ws`) se usa desde el frontend publicado.

**Fuera de alcance.** El cobro de la orden, el rechazo de suscripciones a otro restaurante (cubierto por `TenantSubscriptionInterceptorTest`, no se repite en Azure) y la carga de muchas conexiones simultáneas.

**Lo que dice el código antes de ejecutar.** Revisado en `ea9b5c1`:

- La cocina web consulta `GET /api/v1/kitchen/orders` cada 5 s (`KITCHEN_POLL_INTERVAL_MS` en `kitchen-view.tsx`). No abre WebSocket.
- Ni el frontend ni la app móvil abren `/ws`. Solo el backend publica en `/topic/tenant.{id}.kitchen` (`KitchenRealtimeEventPublisher`).
- La vista de orden del mesero web (`order-view.tsx`) no se actualiza por intervalo, y ni ella ni la app muestran el estado por producto (`kitchenStatus`). Muestran el estado de la orden.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. El mesero envía un pedido y aparece en el tablero de cocina sin recargar | C01 |
| 2. Cocina cambia el estado de un producto y el mesero lo ve actualizado | C02 |
| 3. La conexión en tiempo real abre desde el dominio del frontend publicado | C03 |
| Criterio de entrada: el origen del frontend publicado está configurado en el backend (`frontend_origin` en `azure-backend`) | C03 pasos 4 y 5 |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido con `.\scripts\azure-backend-power.ps1 -Action Start` (plan B1, Always On, PostgreSQL `Ready`) y `/actuator/health` en `UP` con liveness y readiness — [`respuestas/health.txt`](respuestas/health.txt), 2026-10-21 13:14
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Commit desplegado de la API: `ea9b5c1`, igual al del registro de despliegue
- [ ] Commit desplegado del frontend anotado en la sección 1: **no confirmado**; la interfaz publicada es anterior a `1efb78c`
- [x] Node 22 para `realtime/observador-ws.mjs`
- [ ] **No cumplido:** el origen del frontend publicado no está configurado en el backend. `APP_ALLOWED_ORIGINS = https://callejon9.example.com` (ver C03)
- [ ] Al terminar: `.\scripts\azure-backend-power.ps1 -Action Stop`

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Cuentas | `mesero@pruebas.com` (WAITER, «Mesero de piso»), `cocina@pruebas.com` (KITCHEN, «Jefe de cocina») | Alta de usuarios de prueba del 2026-10-16; la contraseña vive fuera del repositorio |
| Mesa | Mesa 12, 5 comensales | Mesa libre del restaurante `pruebas` |
| Orden | ORD-261021203800 (`85a12106-1afb-44c7-89ec-a689f667e7f4`), 7 productos de 1 unidad: Chiles en nogada, Pescado a la talla, Cerveza artesanal, Agua de horchata, Sopa de tortilla, Queso fundido y Guacamole con totopos. Total $975.00 | C01 paso 3 |

## 6. Casos de prueba

### C01 — Un pedido enviado por el mesero aparece en el tablero de cocina sin recargar

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (Chrome) y KITCHEN (Edge) |
| Inicio / fin de ejecución | 2026-10-21 14:34 / 2026-10-21 14:38 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Cocina con sesión en `/kitchen`; mesero con sesión en `/waiter`; Mesa 12 libre.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | En la ventana de cocina, abrir `/kitchen` | `cocina@pruebas.com` | Tablero con las órdenes en cocina y el contador «En preparación» | 14:37 · Tablero «Cocina» con «En preparación 7»; primeras tarjetas ORD-261017020620 (Mesa 6), …0621 (Mesa 7) y …0622 (Mesa 8) | [`capturas/C01-P01-cocina-antes.png`](capturas/C01-P01-cocina-antes.png) |
| 2 | Arrancar el observador: `node observador-ws.mjs --email cocina@pruebas.com --suscribir-cocina --minutos 20 --salida ..\respuestas\C01-C02-observador-ws.txt` | `cocina@pruebas.com` | `ws abierto`, `stomp CONNECTED` y `stomp SUBSCRIBE /topic/tenant.<id>.kitchen` | 14:34:58 · `ws abierto (101 Switching Protocols)`, `stomp CONNECTED versión 1.2`, `stomp SUBSCRIBE /topic/tenant.b1041f82-e0df-4519-8c9f-e9a2b46de2d2.kitchen` | [`respuestas/C01-C02-observador-ws.txt`](respuestas/C01-C02-observador-ws.txt) |
| 3 | En la ventana del mesero, «Abrir mesa» en la Mesa 12, agregar los productos y tocar «Enviar a cocina» | Mesa 12, 5 comensales, 7 productos | La orden muestra folio y total, en estado enviada a cocina | 14:38 · `/waiter/order/85a12106-…`: «Mesa 12 · 5 comensales», «Enviada a cocina», 7 productos, total $975.00, «Se envió a cocina a las 02:38 p.m.» | [`capturas/C01-P03-mesero-enviado.png`](capturas/C01-P03-mesero-enviado.png) |
| 4 | Sin tocar la ventana de cocina, esperar hasta 10 s | — | Aparece la tarjeta con el mismo folio | 14:38 · Sin recargar, aparece la tarjeta ORD-261021203800, Mesa 12, «recién enviada», «Enviada a cocina», con los productos en «Pendiente» | [`capturas/C01-P04-cocina-recibe.png`](capturas/C01-P04-cocina-recibe.png) |
| 5 | En DevTools de cocina, copiar la `GET /api/v1/kitchen/orders` que contiene el folio | — | `200` con la orden y sus productos en «Pendiente» | No se conservó: DevTools no guardó la petición de las 14:38, y la copiada después es de las 15:42, con el cambio de C02 ya aplicado. Se usa en C02 (paso 2) | — |
| 6 | Revisar el registro del observador | — | Una línea `stomp MESSAGE /topic/tenant.<id>.kitchen` con el folio del paso 3 | 14:38:24 · `stomp MESSAGE` con ORD-261021203800, Mesa 12 (`tableId b1e548c9-…`), 5 comensales, total 975.00, `status SENT`, `sentToKitchenAt 20:38:23.708Z`: llegó 1 s después del envío | [`respuestas/C01-C02-observador-ws.txt`](respuestas/C01-C02-observador-ws.txt) |

**Respuesta HTTP literal.** No hay respuesta HTTP del momento del paso 4 (ver paso 5). Lo que la API entregó en ese momento queda en el evento del paso 6, con hora, folio y total.

**Observaciones.** La tarjeta apareció en la cocina sin recargar entre el envío (14:38:23) y la captura (14:38). Llegó por la consulta cada 5 s del tablero, no por WebSocket (ver C03 paso 1). En el mismo segundo, la API publicó el evento en `/ws` (paso 6), aunque ningún cliente web lo escucha.

### C02 — Cocina cambia el estado de un producto y el mesero lo ve actualizado

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Media |
| Rol que ejecuta | KITCHEN (Edge) y WAITER (Chrome) |
| Inicio / fin de ejecución | 2026-10-21 14:46 / 2026-10-21 16:14 |
| Estado | ❌ Fallido |
| Defecto vinculado | [CAL-170](https://linear.app/callejon19/issue/CAL-170) |

**Precondición.** La orden de C01 en cocina con sus productos en «Pendiente»; el observador de C01 corriendo (hasta las 14:54).

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | En la ventana del mesero, abrir la orden de C01 | ORD-261021203800 | La orden con sus productos y su estado | 14:46 · «ORD-261021203800 · Abierta 02:38 p.m.», «Mesa 12 · 5 comensales», «Enviada a cocina», 7 productos y $975.00. Solo muestra cantidad, producto, precio y subtotal; **no hay columna de estado por producto** | [`capturas/C02-P01-mesero-orden-antes.png`](capturas/C02-P01-mesero-orden-antes.png) |
| 2 | En cocina, tocar «Marcar como En preparación» en un producto | Chiles en nogada | El producto pasa a «En preparación»; `POST /api/v1/kitchen/items/<id>/status` → `200` | 14:47 · Chiles en nogada en «En preparación» con el botón «Marcar como Listo»; el observador recibe el cambio a las 14:47:12. La respuesta `POST …/status` copiada es la de un segundo producto, de las 16:07 (Pescado a la talla → `IN_PREPARATION`, `200 OK`). La de las 15:42 de `GET /kitchen/orders` confirma que el cambio de las 14:47 quedó guardado | [`capturas/C02-P02-cocina-en-preparacion.png`](capturas/C02-P02-cocina-en-preparacion.png), [`respuestas/C02-item-status.txt`](respuestas/C02-item-status.txt), [`respuestas/C02-kitchen-orders-en-preparacion.txt`](respuestas/C02-kitchen-orders-en-preparacion.txt) |
| 3 | Sin recargar ni cambiar de pestaña, mirar la orden del mesero durante 30 s | — | El mesero ve el producto «En preparación» | 16:10 · La orden sigue «Enviada a cocina» con los 7 productos y $975.00, sin ningún estado por producto. El mesero no puede saber que un producto está en preparación | [`capturas/C02-P03-mesero-sin-recargar.png`](capturas/C02-P03-mesero-sin-recargar.png) |
| 4 | En cocina, marcar todos los productos hasta «Listo» | 7 productos | La orden pasa a «Lista» | 16:11 · Aviso «Todos los productos de la orden ORD-261021203800 están listos. Pasará a "Lista" en el tablero.» | [`capturas/C02-P04-cocina-listo.png`](capturas/C02-P04-cocina-listo.png) |
| 5 | Sin recargar, mirar la orden del mesero durante 30 s | — | El mesero ve la orden «Lista» | 16:13 · Unos 2 min después del paso 4, la orden **sigue en «Enviada a cocina»** | [`capturas/C02-P05-mesero-orden-lista.png`](capturas/C02-P05-mesero-orden-lista.png) |
| 6 | Recargar la página del mesero (F5) | — | La orden «Lista» | 16:14 · Tras F5, la etiqueta cambia a **«Lista»**. La API sí tenía el estado nuevo: lo que falta es que la vista se actualice sola | [`capturas/C02-P06-mesero-tras-recargar.png`](capturas/C02-P06-mesero-tras-recargar.png) |
| 7 | Revisar el registro del observador | — | Un `stomp MESSAGE` por cada cambio de estado | 14:47:12 · `stomp MESSAGE` del ítem `9dfa1ce9-…` (Chiles en nogada, ORD-261021203800) en `IN_PREPARATION`. Los cambios de las 16:07 a las 16:11 no se registraron porque el observador terminó a las 14:54 (20 min) | [`respuestas/C01-C02-observador-ws.txt`](respuestas/C01-C02-observador-ws.txt) |

**Respuesta HTTP literal.** [`respuestas/C02-item-status.txt`](respuestas/C02-item-status.txt) (16:07, `POST /api/v1/kitchen/items/72f33841-…/status` → `200 OK`) y [`respuestas/C02-kitchen-orders-en-preparacion.txt`](respuestas/C02-kitchen-orders-en-preparacion.txt) (15:42, `GET /api/v1/kitchen/orders` → `200 OK`). Ambas fueron copiadas de DevTools por el proxy del frontend y no llevan cookies.

**Observaciones.**

- Entre el paso 1 (14:46) y el paso 3 (16:10), la vista del mesero cambió el orden de los productos: Chiles en nogada y Pescado a la talla, los que cambiaron de estado, pasaron al final. La vista volvió a pedir la orden en algún momento, probablemente al recuperar el foco, pero no muestra el estado por producto.
- Capturas de apoyo de DevTools en cocina: [`C02-OBS-devtools-orders-1458.png`](capturas/C02-OBS-devtools-orders-1458.png) (14:58, vista previa de `GET /api/v1/kitchen/orders` con 8 órdenes, incluida ORD-261021203800, y su tarjeta en el tablero) y [`C02-OBS-devtools-orders-headers-1526.png`](capturas/C02-OBS-devtools-orders-headers-1526.png) (15:26, cabeceras de `GET /api/v1/kitchen/orders`, `200 OK`).

### C03 — La conexión en tiempo real abre desde el dominio del frontend publicado

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 y criterio de entrada |
| Prioridad | Alta |
| Rol que ejecuta | KITCHEN (Edge y observador) |
| Inicio / fin de ejecución | 2026-10-21 13:14 / 2026-10-21 16:17 |
| Estado | ❌ Fallido |
| Defecto vinculado | [CAL-172](https://linear.app/callejon19/issue/CAL-172); criterio de entrada: [CAL-171](https://linear.app/callejon19/issue/CAL-171) |

**Precondición.** Sesión de cocina en `/kitchen`.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | En cocina, DevTools → Network → filtro «Socket», recargar `/kitchen` y esperar 30 s | — | Una conexión `wss://…/ws` con estado `101` | 16:17 · Con el filtro «Socket» activo: **«0 / 33 requests»**, ninguna conexión WebSocket. El tablero carga y muestra «En preparación 7» | [`capturas/C03-P01-devtools-ws.png`](capturas/C03-P01-devtools-ws.png) |
| 2 | Handshake sin sesión con el origen del frontend: `curl.exe -i --http1.1 --max-time 10 -H "Connection: Upgrade" -H "Upgrade: websocket" -H "Sec-WebSocket-Version: 13" -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" -H "Origin: https://restohub.azurewebsites.net" https://app-callejon9-api-prod-3878c6.azurewebsites.net/ws` | Origen restohub, sin cookie | `401`: sin sesión no hay canal | 13:14 · `HTTP/1.1 401 Unauthorized`, `Content-Length: 0` | [`respuestas/C03-handshake-sin-sesion-origen-frontend.txt`](respuestas/C03-handshake-sin-sesion-origen-frontend.txt) |
| 3 | El mismo handshake sin sesión con otro origen | `-H "Origin: https://example.com"`, sin cookie | `401` *(corregido tras ejecutar: decía `403`. Spring corre `JwtHandshakeInterceptor` antes que la validación de origen, así que sin sesión cualquier origen recibe `401`)* | 13:14 · `HTTP/1.1 401 Unauthorized`, `Content-Length: 0` | [`respuestas/C03-handshake-sin-sesion-otro-origen.txt`](respuestas/C03-handshake-sin-sesion-otro-origen.txt) |
| 4 | Handshake **con sesión** de cocina y el origen del frontend: `node observador-ws.mjs --email cocina@pruebas.com --handshake-origen https://restohub.azurewebsites.net --salida ..\respuestas\C03-handshake-con-sesion-origen-frontend.txt` | `cocina@pruebas.com`, origen restohub | `101 Switching Protocols`: el origen del frontend está permitido | 14:32:59 · Login `200` (KITCHEN) y `/auth/me` `200`; handshake **`HTTP/1.1 403 Forbidden`**, `Content-Length: 0`. La sesión es válida, así que el rechazo es por origen: restohub **no** está permitido. *El archivo es de la repetición de las 14:32; la primera ejecución, a las 13:35:35, dio el mismo `403` y su salida se sobrescribió* | [`respuestas/C03-handshake-con-sesion-origen-frontend.txt`](respuestas/C03-handshake-con-sesion-origen-frontend.txt) |
| 5 | El mismo handshake con sesión y otro origen: `--handshake-origen https://example.com --salida ..\respuestas\C03-handshake-con-sesion-otro-origen.txt` | `cocina@pruebas.com`, origen `https://example.com` | `403 Forbidden`: el origen se rechaza aunque la sesión sea válida | 14:33:12 · Login `200` y handshake `HTTP/1.1 403 Forbidden`: igual que el paso 4 (primera ejecución a las 13:35:50, con el mismo resultado) | [`respuestas/C03-handshake-con-sesion-otro-origen.txt`](respuestas/C03-handshake-con-sesion-otro-origen.txt) |
| 6 | Revisar el inicio del registro del observador de C01 | — | `ws abierto … (101 Switching Protocols)` y `stomp CONNECTED` | 14:34:58 · `ws abierto (101 Switching Protocols)` y `stomp CONNECTED versión 1.2`. Un cliente nativo, que no envía `Origin`, sí abre el canal | [`respuestas/C01-C02-observador-ws.txt`](respuestas/C01-C02-observador-ws.txt) |

**Observaciones.**

- **El frontend publicado no abre el canal (paso 1).** Por eso el caso queda ❌ Fallido. El backend sí ofrece el canal (paso 6): el hueco está en el cliente web. La decisión de replantear el caso es del responsable de CAL-118, no del ejecutor.
- **Criterio de entrada no cumplido (paso 4).** El 2026-10-21 hacia las 13:52, `az webapp config appsettings list` del App Service `app-callejon9-api-prod-3878c6` muestra `APP_ALLOWED_ORIGINS = https://callejon9.example.com`, el valor de ejemplo, no `https://restohub.azurewebsites.net`. Aunque el frontend abriera `/ws`, hoy el backend lo rechazaría con `403`. Lo registra [CAL-171](https://linear.app/callejon19/issue/CAL-171). [CAL-163](https://linear.app/callejon19/issue/CAL-163) traía este paso, pero está cancelado. No se cambió durante la prueba.
- El paso 3 corrigió una suposición del ejecutor, no un defecto: el handshake rechaza en ambos órdenes de validación. Por eso el origen solo se puede comprobar con sesión (pasos 4 y 5).
- **Hallazgo fuera del caso: el freno del login es compartido por todos los clientes.** A las 13:51, el primer y único intento de login del ejecutor en el navegador, con datos correctos, recibió «Demasiados intentos fallidos. Intenta de nuevo en 7 minutos.» Una cuenta inventada recibió `429` con el mismo `Retry-After` tanto por el proxy de restohub como directo a la API ([`respuestas/OBS-login-bloqueado-por-ip.txt`](respuestas/OBS-login-bloqueado-por-ip.txt)). Los terminales de este equipo solo hicieron logins exitosos (13:35:35 y 13:35:50), que no cuentan como fallo. Las sondas llegaron con el bloqueo activo, y `checkAllowed` rechaza antes de `recordFailure`, así que tampoco contaron. Los 20 fallos que llenaron la llave de IP de `LoginAttemptLimiter` vinieron de otros clientes: 20 logins fallidos de cualquiera en 15 min bloquean el login de todos los restaurantes, incluso con la contraseña correcta. Relacionado con CAL-106 y con CAL-134 C02, que usa la misma `ClientIp`. Se registra como [CAL-169](https://linear.app/callejon19/issue/CAL-169), que bloquea la UAT (CAL-117).

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| [CAL-170](https://linear.app/callejon19/issue/CAL-170) | C02 | Media | La vista de orden del mesero web no se actualiza sola ni muestra el estado de cada producto. A las 16:13, 2 min después de que todos los productos quedaron listos, seguía «Enviada a cocina»; con F5 pasó a «Lista» | Abierto | — |
| [CAL-172](https://linear.app/callejon19/issue/CAL-172) | C03 | Media | El frontend publicado no usa el canal en tiempo real (`/ws`): 0 conexiones WebSocket en `/kitchen`. La cocina consulta cada 5 s | Abierto | — |
| [CAL-171](https://linear.app/callejon19/issue/CAL-171) | C03 | Media | `APP_ALLOWED_ORIGINS` del backend en Azure es `https://callejon9.example.com`; el handshake con sesión desde restohub recibe `403`. CAL-163 traía este paso pero está cancelado | Abierto | — |
| [CAL-169](https://linear.app/callejon19/issue/CAL-169) | Hallazgo en C03 | Crítica *(propuesta)* | El freno de login por IP se comparte entre clientes en Azure: 20 fallos de cualquiera bloquean el login de todos durante hasta 15 min, aun con la contraseña correcta. Según la guía, impide operar el turno | Abierto | — |

> Cada defecto se registra como issue en Linear con severidad, pasos, resultado esperado, obtenido y su captura. CAL-169 es crítico según la escala de la guía y bloquea el cierre de la fase SIT hasta su re-prueba.

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 3 | 1 | 2 | 0 | 0 |

**Desviaciones y limitaciones.**

- El canal `/ws` se observó con un cliente nativo (`realtime/observador-ws.mjs`), porque ni la web ni la app lo abren.
- C01 paso 5: no se conservó la respuesta HTTP del momento; la sustituye el evento del observador con hora, folio y total.
- C02 paso 2: la respuesta `POST …/status` copiada es la de las 16:07 (otro producto de la misma orden), no la de las 14:47; el cambio de las 14:47 consta en la captura, el observador y la respuesta de las 15:42.
- C02 paso 7: el observador terminó a las 14:54 y no registró los cambios de las 16:07 a las 16:11.
- C03 paso 3: el resultado esperado se corrigió después de ejecutar (`403` → `401`).
- C03 pasos 4 y 5: se ejecutaron a las 13:35 y se repitieron a las 14:32–14:33 con el mismo `403`; los archivos guardados son los de la repetición.
- La ejecución se pausó de 13:51 a 13:56 por el bloqueo de login (CAL-169).
- La orden ORD-261021203800 (Mesa 12) quedó «Lista» y abierta en el restaurante `pruebas`.
- La captura del equipo de trabajo (`entorno_trabajo_laptop.png`) es de la configuración de Windows, tomada el 2026-10-21 a las 22:41. Se taparon con rectángulos grises la cuenta de Windows, el nombre del equipo, el Device ID, el Product ID y el número de serie, por ser datos personales.

**Conclusión del ejecutor.** El criterio 1 se cumple: el pedido aparece en la cocina sin recargar, por consulta periódica. Los criterios 2 y 3 no se cumplen. El mesero no ve los cambios de cocina sin recargar, ni el estado por producto (CAL-170). El frontend no usa el canal en tiempo real (CAL-172). Además, el backend en Azure no tiene el origen de restohub permitido (CAL-171). El backend sí publica los eventos en tiempo real al instante, como demuestra el observador. Fuera del alcance apareció un defecto crítico en el freno del login (CAL-169), que bloquea el cierre de la SIT hasta corregirlo.

## 9. Anexo SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Frontend ↔ API (proxy `/api/v1/*`) | C01, C02 | Capturas de C01 y C02, `respuestas/C02-item-status.txt`, `respuestas/C02-kitchen-orders-en-preparacion.txt` |
| API ↔ tiempo real (`/ws`) | C01, C02, C03 | `respuestas/C01-C02-observador-ws.txt` |
| Frontend publicado ↔ tiempo real | C03 | `capturas/C03-P01-devtools-ws.png` (0 sockets) |
| Origen del frontend permitido en el backend (`frontend_origin`) | C03 | `respuestas/C03-handshake-con-sesion-origen-frontend.txt` (`403`) |

**Reproducir el observador.** [`realtime/observador-ws.mjs`](realtime/observador-ws.mjs) necesita Node 22 o posterior, sin dependencias, y lee la contraseña de `C9_TEST_PASSWORD`.

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
