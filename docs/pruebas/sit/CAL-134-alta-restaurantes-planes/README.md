# Evidencia de prueba · CAL-134 — SIT · Alta de restaurantes y planes

> **v2.** C01, C02 y C03 ejecutados. Defectos registrados en Linear: CAL-176 (C02) y CAL-177 (C03). Pendiente de revisión por otro integrante (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-134](https://linear.app/callejon19/issue/CAL-134) — SIT · Alta de restaurantes y planes |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Alta de restaurantes y planes |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | **No confirmado.** La interfaz publicada en `restohub` es anterior a `1efb78c` (2026-10-19 00:43, «unificar el nombre del producto como RestoHub»), así que no es `ea9b5c1`. El 2026-10-21 a las 19:34 el login mostraba la pestaña «Callejon 9» y el prefijo «c9.mx /» ([`capturas/C01-P03-login-redirigido.png`](capturas/C01-P03-login-redirigido.png)), y el 2026-10-22 a las 10:05 `/platform` decía «Planes disponibles para los restaurantes de Callejón 9» ([`capturas/C03-P02-buscar-restaurante.png`](capturas/C03-P02-buscar-restaurante.png)). `1efb78c` cambió esos textos a «RestoHub» y quitó el prefijo. Quien desplegó `restohub` no lo ha confirmado, y el recurso no está en la suscripción de Azure del ejecutor |
| Registro de despliegue | Comentario «Despliegue» en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) del 2026-10-21 00:56 (Braulio Hernández), `ea9b5c1` |
| Ejecución de CI | No aplica |
| Equipo de trabajo | Laptop Lenovo IdeaPad 3 15IIL05 (modelo 81WE) con Windows 11 Home 25H2, compilación 26200.9711, 64 bits; Intel Core i5-1035G1 a 1.00 GHz, 12 GB de RAM (11.7 GB utilizables) e Intel UHD Graphics. Es la computadora del navegador y de la terminal del script de altas ([`capturas/entorno_trabajo_laptop.png`](capturas/entorno_trabajo_laptop.png), 2026-10-21 22:41) |
| Navegador / dispositivo | Google Chrome 155.0.8059.86 (64 bits), con Wi-Fi. C02 paso 4: teléfono de un integrante del equipo, POCO con Xiaomi HyperOS 3.0.304.0 y Android 16, en Chrome con datos móviles 4G y sin Wi-Fi ([`capturas/PREP-02-telefono-otra-red.jpeg`](capturas/PREP-02-telefono-otra-red.jpeg); el nombre del dispositivo se tapó por ser un dato personal) |
| Restaurante de prueba | `sit-alta-10211930` (C01) y el intento `request-tel-ip` (C02 paso 4, no creado) |
| Versión de esta evidencia | v2 · 2026-10-22 (v1 del 2026-10-21, con C03 bloqueado) |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Braulio Hernández Mejía | Asignado en Linear |
| Ejecutor de la prueba | Braulio Hernández Mejía | Chrome, script de altas, el teléfono de otra red y la cuenta de plataforma |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-118) | 2026-10-20 | 2026-10-21 | 2026-10-20 | | |
| Esta tarea (CAL-134) | 2026-10-20 | 2026-10-21 | 2026-10-21 19:20 | | |
| Preparación del entorno y datos | 2026-10-21 | 2026-10-21 | 2026-10-21 19:20 | 2026-10-21 19:26 | — |
| Ejecución de los casos | 2026-10-21 | 2026-10-21 | 2026-10-21 19:26 | 2026-10-22 10:07 | C02 se repitió a las 22:01 con la ventana del límite vacía. C03 se ejecutó por la API el 2026-10-21 a las 22:55 y se confirmó en el navegador el 2026-10-22 de 10:04 a 10:07 |
| Re-prueba de defectos | | | | | Pendiente de CAL-176 |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que el alta pública de restaurantes funciona de punta a punta a través del frontend publicado, que el límite de altas por IP sigue funcionando detrás del proxy de Next.js, y que la plataforma ve el restaurante nuevo con su plan.

**Fuera de alcance.** Los límites de usuarios y mesas de cada plan una vez creado el restaurante, el cambio de plan y el cobro de suscripciones.

**Lo que dice el código antes de ejecutar.** Revisado en `ea9b5c1`:

- `POST /api/v1/signup` responde `201` con `tenantId`, `slug` y `adminEmail`. El frontend redirige a `/login?slug=<slug>`, así que el administrador inicia sesión en un paso aparte.
- El límite es de 5 altas por IP en una ventana deslizante de 1 h (`SignupRateLimiter`, `app.signup.rate-limit`). El cupo se consume antes de crear nada, así que cuentan las altas creadas (`201`) y las de slug ocupado (`409`). Las que fallan validación (`400`) y las rechazadas por el límite (`429`) no cuentan. `Retry-After` es el tiempo que falta para que salga de la ventana el intento más antiguo.
- La IP sale de `ClientIp`: solo cree en `X-Forwarded-For` si la conexión llega desde una red privada. Por eso C02 incluye un intento desde otra red.
- El panel `/platform` solo consulta `GET /api/v1/platform/plans` y su subtítulo dice «Planes disponibles para los restaurantes de RestoHub». No hay endpoint que liste restaurantes.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Alta desde el frontend publicado: crea el restaurante y su administrador, que inicia sesión enseguida | C01 |
| 2. Sexta alta en una hora desde la misma red: 429 (límite por IP detrás del proxy) | C02 |
| 3. Panel de plataforma del super-administrador: el restaurante nuevo aparece con su plan | C03 |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido con `.\scripts\azure-backend-power.ps1 -Action Start` y `/actuator/health` en `UP` con liveness y readiness — [`respuestas/health.txt`](respuestas/health.txt), 2026-10-21 19:20
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Commit desplegado de la API: `ea9b5c1`, igual al del registro de despliegue
- [ ] Commit desplegado del frontend anotado en la sección 1: **no confirmado**; la interfaz publicada es anterior a `1efb78c`
- [x] Cuenta `SUPER_ADMIN` de Azure disponible para C03: `super@callejon9.com`, con la contraseña del secreto `platform-admin-password` de Key Vault, desde el 2026-10-21 22:55 (antes no se tenía)
- [ ] Al terminar: `.\scripts\azure-backend-power.ps1 -Action Stop`

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Restaurante de C01 | Slug `sit-alta-10211930`, `tenantId 14b46dde-cc8b-4c8b-9e45-d64464d389b3`. En el campo «Nombre del restaurante» quedó escrito `admin@sit-alta-10211930.test` | C01 paso 2 |
| Plan de C01 | *No consta en la evidencia.* El formulario propone PRO por defecto | C01 paso 2 |
| Administrador de C01 | `admin@sit-alta-10211930.test`, «SIT Alta Braulio», `userId 54dc3a67-…` | C01 paso 2; contraseña generada para la prueba, guardada fuera del repositorio |
| Cuerpo de C02 | Los mismos datos de C01 (con `planCode` `Pro`); [`altas-repetidas.ps1`](altas-repetidas.ps1) lo escribe en un archivo temporal fuera del repositorio y lo borra al terminar | C02 paso 2 |
| Intento de C02 paso 4 | «Test-Diferent-Ip-Request», slug `request-tel-ip`, administrador «SIT-Request-Tel-IP», `sit10211016@test` | C02 paso 4; no se creó |

## 6. Casos de prueba

### C01 — El alta desde el frontend crea el restaurante y su administrador entra enseguida

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | Visitante sin sesión, luego ADMIN del restaurante nuevo |
| Inicio / fin de ejecución | 2026-10-21 19:26 / 2026-10-21 19:37 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Chrome sin sesión.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Abrir `https://restohub.azurewebsites.net/signup` | — | Formulario «Registra tu restaurante» | 19:26 · «Registro · Paso 1 de 2 · Registra tu restaurante», con los campos vacíos y las 4 reglas de contraseña | [`capturas/C01-P01-formulario.png`](capturas/C01-P01-formulario.png) |
| 2 | Llenar el formulario y enviarlo, con DevTools → Network abierto | `sit-alta-10211930`, `admin@sit-alta-10211930.test` | `POST /api/v1/signup` → `201` con `tenantId`, `slug` y `adminEmail` | 19:32 · Formulario lleno con la contraseña oculta. 19:32:15 · **`201 Created`** con `"slug":"sit-alta-10211930","adminEmail":"admin@sit-alta-10211930.test"` | [`capturas/C01-P02-alta-enviada.png`](capturas/C01-P02-alta-enviada.png), [`respuestas/C01-signup.txt`](respuestas/C01-signup.txt) |
| 3 | Observar la redirección | — | `/login?slug=sit-alta-…` con el restaurante prellenado | 19:34 · `/login?slug=sit-alta-10211930` con «c9.mx / sit-alta-10211930» prellenado; en Network, la `POST signup` `201 Created` | [`capturas/C01-P03-login-redirigido.png`](capturas/C01-P03-login-redirigido.png) |
| 4 | Iniciar sesión con el administrador nuevo | `admin@sit-alta-10211930.test` | `POST /api/v1/auth/login` → `200` con rol `ADMIN`; entra a `/admin` | 19:37:15 · **`200 OK`** con `"role":"ADMIN"`. `/admin` con «Mesas 0, Categorías 0, Productos 0, Usuarios 1» | [`capturas/C01-P04-admin-dentro.png`](capturas/C01-P04-admin-dentro.png), [`respuestas/C01-login.txt`](respuestas/C01-login.txt) |

**Respuesta HTTP literal.** [`respuestas/C01-signup.txt`](respuestas/C01-signup.txt) y [`respuestas/C01-login.txt`](respuestas/C01-login.txt), copiadas de DevTools por el proxy del frontend, sin el cuerpo de la petición. En el login, los valores de `set-cookie` se sustituyeron por `redactado`.

**Observaciones.**

- A las 19:30 hubo intentos con un identificador inválido (`admin@sit-alta-1021.test`); el formulario mostró «Solo minusculas, numeros y guiones, entre 3 y 80 caracteres.» ([`C01-OBS-slug-invalido-1.png`](capturas/C01-OBS-slug-invalido-1.png), [`C01-OBS-slug-invalido-2.png`](capturas/C01-OBS-slug-invalido-2.png)). En Network se ven tres `POST signup` fallidas antes del `201`. Su código no se conservó; si fueron rechazos de validación (`400`), no cuentan para el límite.
- En «Nombre del restaurante» quedó escrito el correo `admin@sit-alta-10211930.test`, y así aparece en el encabezado de `/admin`. No afecta el criterio.
- El plan elegido no consta en las capturas ni en las respuestas.

### C02 — La sexta alta en una hora desde la misma red recibe 429

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Alta |
| Rol que ejecuta | Visitante sin sesión |
| Inicio / fin de ejecución | 2026-10-21 20:33 / 2026-10-21 22:16 |
| Estado | ❌ Fallido |
| Defecto vinculado | [CAL-176](https://linear.app/callejon19/issue/CAL-176) |

**Precondición.** Restaurante `sit-alta-10211930` creado en C01.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Definir `$env:C9_SIT_ALTA_PASSWORD` en la terminal propia con la contraseña del administrador de C01 | — | — | — | — |
| 2 | Enviar altas por el proxy del frontend, una tras otra, con [`altas-repetidas.ps1`](altas-repetidas.ps1): cada una es `curl.exe -i -X POST https://restohub.azurewebsites.net/api/v1/signup` con el mismo cuerpo de C01 | slug ocupado | Las 5 primeras de la hora responden `409` y cuentan; la sexta responde `429` | **Ventana de las 20:33:** `409` del formulario a las 20:33:16, 4 × `409` del script a las 20:51:45–20:51:48 y 4 × `429` a las 20:52:01–20:52:03. **Ventana de las 22:01** (vacía, el último intento contado era de las 20:51:48): 4 × `409` a las 22:01:20–22:01:22, `409` a las 22:01:35 (quinta) y **`429 Too Many Requests` con `Retry-After: 3586`** a las 22:01:35–22:01:36 (sexta a octava) | [`respuestas/C02-altas-2-a-5.txt`](respuestas/C02-altas-2-a-5.txt), [`capturas/C02-P02-script-terminal.png`](capturas/C02-P02-script-terminal.png) |
| 3 | Otra alta desde el formulario de `/signup`, con DevTools abierto | slug `sit-many-request` | `429` «Demasiadas altas» con cabecera `Retry-After`; el formulario muestra el mensaje | 20:59:56 · **`429 Too Many Requests`**, `retry-after: 2001`; el formulario muestra «No se pudo completar el registro · Se alcanzo el limite de registros de restaurantes desde esta conexion. Intenta de nuevo en 34 minutos.» | [`capturas/C02-P03-sexta-alta-429.png`](capturas/C02-P03-sexta-alta-429.png), [`respuestas/C02-sexta-alta-429.txt`](respuestas/C02-sexta-alta-429.txt) |
| 4 | Desde otro teléfono **con datos móviles 4G y sin Wi-Fi**, abrir `/signup` y dar de alta otro restaurante | slug `request-tel-ip` | `201` y redirección a login: el límite es por IP del cliente, no global | 22:16 · **«No se pudo completar el registro · Se alcanzo el limite de registros de restaurantes desde esta conexion. Intenta de nuevo en 45 minutos.»** Los 45 minutos coinciden con la ventana que abrió la computadora a las 22:01:20, que vence a las 23:01:20: el teléfono, desde otra red, cayó en la misma llave | [`capturas/C02-P04-otra-red.jpeg`](capturas/C02-P04-otra-red.jpeg), [`capturas/PREP-02-telefono-otra-red.jpeg`](capturas/PREP-02-telefono-otra-red.jpeg) |

**Respuesta HTTP literal.** [`respuestas/C02-altas-2-a-5.txt`](respuestas/C02-altas-2-a-5.txt) (salida completa de `curl -i` de la corrida de las 22:01:35, con la hora y la línea `$ curl` de cada alta; la contraseña aparece como `<redactado>`), [`respuestas/C02-sexta-alta-429.txt`](respuestas/C02-sexta-alta-429.txt) y [`respuestas/C02-OBS-formulario-409-2033.txt`](respuestas/C02-OBS-formulario-409-2033.txt) (copiadas de DevTools).

**Observaciones.**

- **El límite funciona para un cliente:** la sexta alta de la hora recibe `429` con `Retry-After`, por `curl` (paso 2) y en el formulario (paso 3). Los valores de `Retry-After` concuerdan con la ventana deslizante: `2001` s a las 20:59:56 apunta al `409` de las 20:33:16 ([`C02-OBS-formulario-409-2033.png`](capturas/C02-OBS-formulario-409-2033.png)), y `3586` s a las 22:01:36 apunta a las altas de las 22:01:20–22:01:22.
- **Pero lo comparten todos los clientes (paso 4).** Un teléfono en otra red recibió el bloqueo de la computadora, con el mismo tiempo restante. Cinco altas de cualquiera bloquean el registro de restaurantes para todos durante 1 h. Se registró como [CAL-176](https://linear.app/callejon19/issue/CAL-176). La causa probable es la misma que [CAL-169](https://linear.app/callejon19/issue/CAL-169) (`ClientIp` detrás del proxy del frontend y de Azure).
- El script se corrió varias veces sobre el mismo archivo de salida, y cada corrida lo sobrescribió. Se conserva la de las 22:01:35; las de las 20:51, 20:52 y 22:01:20 constan en la captura de la terminal. Hubo además al menos una corrida anterior a las 20:51 cuya hora no se ve en la captura; por el `Retry-After` de las 20:59:56, quedó fuera de esa ventana.
- El paso 3 se hizo con otros datos (slug `sit-many-request`), no con los de C01. Da igual: el límite se revisa antes de validar el slug.
- La captura del paso 4 muestra la URL recortada («…zurewebsites.net») y la barra de estado con la hora, la señal 4G y sin el ícono de Wi-Fi. La captura del teléfono de las 22:13 (`PREP-02`) se tomó con Wi-Fi, antes de cambiar a datos móviles.

### C03 — El panel de plataforma muestra el restaurante nuevo con su plan

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 |
| Prioridad | Media |
| Rol que ejecuta | SUPER_ADMIN (`super@callejon9.com`, restaurante `platform`) |
| Inicio / fin de ejecución | 2026-10-21 22:55 / 2026-10-22 10:07 |
| Estado | ❌ Fallido |
| Defecto vinculado | [CAL-177](https://linear.app/callejon19/issue/CAL-177) |

**Precondición.** Restaurante de C01 (`sit-alta-10211930`) creado. Contraseña de la cuenta de plataforma leída de Key Vault sin mostrarla ni guardarla.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Iniciar sesión en `https://restohub.azurewebsites.net/login?slug=platform` con la cuenta de plataforma | `super@callejon9.com` | Entra a `/platform` | **API**, 2026-10-21 22:55:32 · `POST /api/v1/auth/login` → `200 OK`, `"role":"SUPER_ADMIN"`. **Navegador**, 2026-10-22 10:04 · formulario de login con el restaurante `platform`; 10:05 · dentro de `/platform` como «Super Administrador» | [`respuestas/C03-login-plataforma.txt`](respuestas/C03-login-plataforma.txt), [`capturas/C03-P01-plataforma.png`](capturas/C03-P01-plataforma.png), [`capturas/C03-P02-buscar-restaurante.png`](capturas/C03-P02-buscar-restaurante.png) |
| 2 | Buscar en el panel el restaurante de C01 | `sit-alta-10211930` | Aparece con su plan | **No aparece.** 2026-10-22 10:05 · el «Panel de plataforma» solo dice «Planes disponibles para los restaurantes de Callejón 9» y muestra las tarjetas FREE, PRO y PREMIUM; no hay lista ni buscador de restaurantes. Por la API, 2026-10-21 22:55:39–40, `GET /api/v1/platform/tenants` y `GET /api/v1/tenants` → `404 Not Found` | [`capturas/C03-P02-buscar-restaurante.png`](capturas/C03-P02-buscar-restaurante.png), [`respuestas/C03-OBS-listar-restaurantes.txt`](respuestas/C03-OBS-listar-restaurantes.txt) |
| 3 | En DevTools, revisar la respuesta de `GET /api/v1/platform/plans` | — | `200` con los planes | **API**, 2026-10-21 22:55:38 · `200 OK` con FREE «Gratis» $0.00 (3 usuarios, 5 mesas), PRO «Profesional» $499.00 (15, 30) y PREMIUM $999.00 (60, 120). **Navegador**, 2026-10-22 10:07 · Network: `GET …/api/v1/platform/plans` → `200 OK`, `Date: Thu, 22 Oct 2026 16:05:38 GMT` | [`respuestas/C03-platform-plans.txt`](respuestas/C03-platform-plans.txt), [`capturas/C03-P03-devtools-plans.png`](capturas/C03-P03-devtools-plans.png) |

**Respuesta HTTP literal.** [`respuestas/C03-login-plataforma.txt`](respuestas/C03-login-plataforma.txt), [`respuestas/C03-platform-plans.txt`](respuestas/C03-platform-plans.txt) y [`respuestas/C03-OBS-listar-restaurantes.txt`](respuestas/C03-OBS-listar-restaurantes.txt): salida de `curl -i` por el proxy del frontend, con la hora de cada petición. La contraseña y los valores de las cookies aparecen como `<redactado>`.

**Observaciones.**

- El super-administrador entra y el panel carga, pero el panel solo muestra los planes. No hay forma de ver el restaurante de C01 ni qué plan tiene, ni en la pantalla ni en la API: no existe endpoint que liste restaurantes. Coincide con lo que indicaba el código de `ea9b5c1` (sección 4).
- La captura del paso 1 muestra el formulario antes de escribir el correo y la contraseña; que la sesión se abrió consta en la captura del paso 2 («Super Administrador» y `/platform`) y en la respuesta `200` del login.
- El paso 2 no lleva captura de Ctrl+F: el panel cabe completo en una pantalla y solo tiene las tres tarjetas de planes.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| [CAL-176](https://linear.app/callejon19/issue/CAL-176) | C02 | Alta | El límite de altas por IP lo comparten todos los clientes en Azure: un teléfono en otra red recibió a las 22:16 el bloqueo que había abierto la computadora a las 22:01, con el mismo tiempo restante | Abierto | — |
| [CAL-177](https://linear.app/callejon19/issue/CAL-177) | C03 | Media | El panel de plataforma no permite ver los restaurantes ni su plan: `/platform` solo muestra los planes y no hay endpoint que liste restaurantes (`404`) | Abierto | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 3 | 1 | 2 | 0 | 0 |

**Desviaciones y limitaciones.**

- C02 no se hizo dentro de la hora de C01: empezó a las 20:33. La sexta alta se demostró dentro de ventanas propias de C02.
- Las altas de C02 paso 2 se hicieron con `curl` por el proxy del frontend (`altas-repetidas.ps1`), no con el formulario. Siguen la misma ruta que el navegador y dejan la respuesta literal.
- El archivo de C02 paso 2 es el de la última corrida; las anteriores constan solo en la captura de la terminal.
- El paso 4 se hizo con el teléfono de otro integrante, no con el del ejecutor. En su captura del dispositivo se tapó el nombre del teléfono por ser un dato personal.
- C03 no se pudo ejecutar el 2026-10-21 hasta las 22:55, cuando se obtuvo la cuenta de plataforma. Esa noche se ejecutó por la API; las capturas del navegador se tomaron el 2026-10-22 de 10:04 a 10:07 y llevan esa fecha.
- Quedan en Azure el restaurante `sit-alta-10211930` y su administrador.
- La captura del equipo de trabajo (`entorno_trabajo_laptop.png`) es de la configuración de Windows, tomada el 2026-10-21 a las 22:41. Se taparon con rectángulos grises la cuenta de Windows, el nombre del equipo, el Device ID, el Product ID y el número de serie, por ser datos personales.

**Conclusión del ejecutor.** El criterio 1 se cumple: el alta desde el frontend publicado crea el restaurante (`201`) y su administrador entra enseguida (`200`, ADMIN). El criterio 2 se cumple a medias: la sexta alta de la hora recibe `429` con `Retry-After`, pero el límite lo comparten todos los clientes, así que un usuario en otra red quedó bloqueado por altas ajenas (CAL-176). El criterio 3 no se cumple: el super-administrador entra al panel de plataforma, pero el panel solo muestra los planes y no hay forma de ver el restaurante nuevo ni su plan.

## 9. Anexo SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Frontend ↔ API (proxy `/api/v1/*`) | C01, C02 | `respuestas/C01-signup.txt`, `respuestas/C02-*.txt` |
| API ↔ PostgreSQL (alta en la base real) | C01 | Login del administrador nuevo en `respuestas/C01-login.txt` |
| Proxy ↔ límite por IP | C02 | `respuestas/C02-altas-2-a-5.txt`, `respuestas/C02-sexta-alta-429.txt`, `capturas/C02-P04-otra-red.jpeg` |
| Frontend ↔ API de plataforma (sesión `SUPER_ADMIN`) | C03 | `respuestas/C03-*.txt`, `capturas/C03-P02-buscar-restaurante.png`, `capturas/C03-P03-devtools-plans.png` |

## 10. Revisión

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

**Ejecutor:** Braulio Hernández Mejía · **Fecha:** 2026-10-22
