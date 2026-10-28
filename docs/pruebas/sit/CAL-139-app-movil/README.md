# Evidencia de prueba · CAL-139 — SIT · App móvil de meseros

> **v1.** Ejecución completa; pendiente de revisión por otro integrante (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-139](https://linear.app/callejon19/issue/CAL-139/sit-app-movil-de-meseros) — SIT · App móvil de meseros |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | App móvil de meseros (`mobile/`, CAL-114) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `7a250be` (`7a250bed4d00a190a82c194f93075d3d637e703c`) |
| Commit de la app móvil | `7a250be` (`origin/main`), servido por Metro desde una copia fija del repositorio en `exp://192.168.68.112:8081` |
| Registro de despliegue | Comentarios «Despliegue» en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat): 2026-10-20 23:32 (Braulio Hernández) y 23:42–23:46 (Octavio Duarte), ambos de `7a250be`. Corre el segundo |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | Teléfono: Nothing Phone (3a), Nothing OS 4.1, Expo Go con SDK 57.0.0, Wi-Fi de la misma red que el servidor de Metro ([`PREP-01`](capturas/PREP-01-expo-go-conectado.png), [`PREP-02`](capturas/PREP-02-dispositivo.png)). Web: Google Chrome 155 con Playwright 1.56.1 en Windows 11 |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v1 · 2026-10-20 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Octavio Duarte | Asignado en Linear |
| Ejecutor de la prueba | Octavio Duarte | Teléfono con Expo Go y script de Playwright |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-118) | 2026-10-20 | 2026-10-21 | 2026-10-20 | | |
| Esta tarea (CAL-139) | 2026-10-20 | 2026-10-21 | 2026-10-20 22:06 | | Reabierta en revisión: la evidencia anterior no correspondía a una ejecución real |
| Preparación del entorno y datos | 2026-10-20 | 2026-10-20 | 2026-10-20 22:56 | 2026-10-20 23:47 | La API estaba detenida; se encendió (`UP` a las 23:02) y se redesplegó `7a250be` para tener un commit registrado (23:46) |
| Ejecución de los casos | 2026-10-21 | 2026-10-21 | 2026-10-20 23:49 | 2026-10-21 00:05 | Empezó la noche anterior a lo planeado |
| Re-prueba de defectos | | | | | |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que la app móvil de meseros, ejecutada en un teléfono, opera contra la API de Azure por HTTPS: inicia sesión con cookie nativa, ve los mismos datos que la web, envía pedidos que llegan a la cocina web en tiempo real y avisa cuando no hay conexión.

**Fuera de alcance.** El alta de productos desde la app (`ProductFormScreen`), el cobro y la cancelación de órdenes; la app compilada para tienda (se prueba en Expo Go).

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Login con `mesero@pruebas.com`: la sesión queda en la cookie nativa del teléfono | C01 |
| 2. Consulta de mesas y menú: los mismos datos que ve el mesero en la web | C02 |
| 3. Envío de un pedido: folio y total; el pedido aparece en la cocina web | C03 |
| 4. Sin conexión: mensaje de error y botón para reintentar | C04 |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido y `/actuator/health` en `UP` — [`respuestas/health.txt`](respuestas/health.txt), 2026-10-20 23:03
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Commit desplegado verificado con el registro de despliegue: `7a250be`, el mismo que sirve Metro a la app; `/actuator/health` en `UP` tras el despliegue — [`respuestas/health-tras-despliegue.txt`](respuestas/health-tras-despliegue.txt), 2026-10-20 23:47
- [x] Metro arrancado con `EXPO_PUBLIC_API_URL=https://app-callejon9-api-prod-3878c6.azurewebsites.net` y `EXPO_PUBLIC_ALLOW_INSECURE_HTTP=false` (los valores de `mobile/.env`), en modo CI para que el código no cambie durante la prueba: `npx expo start --lan --port 8081`
- [x] Expo Go abierto en el teléfono y conectado a `exp://192.168.68.112:8081` — [`capturas/PREP-01-expo-go-conectado.png`](capturas/PREP-01-expo-go-conectado.png), 2026-10-20 23:26

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Cuentas por rol | `mesero@pruebas.com` (WAITER), `cocina@pruebas.com` (KITCHEN) | Alta de usuarios de prueba del 2026-10-16; la contraseña vive fuera del repositorio |
| Mesa para el pedido | *Una mesa libre al ejecutar; anotar número* | Configuración del restaurante `pruebas` |
| Productos del pedido | *Dos productos activos; anotar nombres* | Catálogo del restaurante `pruebas` |

## 6. Casos de prueba

### C01 — El mesero inicia sesión en el teléfono y la sesión persiste

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER |
| Inicio / fin de ejecución | 2026-10-20 23:49 / 2026-10-20 23:56 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** App recién abierta en Expo Go, sin sesión.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Abrir la app en Expo Go | — | Pantalla de inicio de sesión | 23:49 · Pantalla «RestoHub · App de meseros» con restaurante, correo y contraseña vacíos, y el aviso «Conexión cifrada (HTTPS) app-callejon9-api-prod-3878c6.azurewebsites.net» | [`capturas/C01-P01-login-vacio.png`](capturas/C01-P01-login-vacio.png) |
| 2 | Escribir restaurante, correo y contraseña; tocar «Iniciar sesión» | `pruebas`, `mesero@pruebas.com` | Entra a «Mesas» con el aviso «Autenticación exitosa» | 23:56 · «Mesas · Mesero de piso · Callejon 9 prue…» con el aviso «Autenticación exitosa · Sesión iniciada como Mesero de piso. La sesión se guarda en una cookie segura del sistema.» 12 mesas, 5 libres | [`capturas/C01-P02-mesas-tras-login.png`](capturas/C01-P02-mesas-tras-login.png) |
| 3 | Cerrar Expo Go por completo y volver a abrir la app | — | Entra directo a las mesas, sin pedir credenciales | 23:56 · Tras cerrar Expo Go desde las apps recientes y reabrir la app, entra directo a «Mesas» (12 mesas, 5 libres) sin pedir credenciales y sin el aviso de bienvenida, que solo aparece tras un login nuevo: la sesión se reanudó desde la cookie nativa | [`capturas/C01-P03-sesion-persistente.png`](capturas/C01-P03-sesion-persistente.png) |

**Respuesta HTTP literal.** La misma llamada de la app (`POST /api/v1/auth/login`), con `Set-Cookie` redactado: [`respuestas/C01-login.txt`](respuestas/C01-login.txt). 23:53 · `200 OK`, cookies `access_token` y `refresh_token` con `Secure; HttpOnly; SameSite=Strict`, usuario «Mesero de piso» con rol `WAITER`.

**Observaciones.** Un primer intento en el paso 2 (23:52–23:53) falló por un error del ejecutor al teclear: la contraseña capturada tenía 21 caracteres y la real tiene 24. La app respondió «No se pudo iniciar sesión · Restaurante, correo o contraseña incorrectos.» sin revelar cuál dato falló ([`C01-OBS-credenciales-capturadas.png`](capturas/C01-OBS-credenciales-capturadas.png), [`C01-OBS-error-credenciales.png`](capturas/C01-OBS-error-credenciales.png)). No es un defecto del sistema.

En Expo Go, el botón flotante de herramientas de desarrollo tapa el botón «Salir» de la app. Es un elemento de Expo Go que no existe en una app compilada; no se registra como defecto.

### C02 — El teléfono y la web muestran las mismas mesas y productos

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Media |
| Rol que ejecuta | WAITER |
| Inicio / fin de ejecución | 2026-10-20 23:58 / 2026-10-20 23:59 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Sesión de mesero en el teléfono (C01).

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | En el teléfono, abrir la lista de mesas | — | Mesas con número, estado y capacidad | 23:59 · «12 mesas · 5 libres». Libres: 2, 3, 4, 5 y 12. Ocupadas: 1, 6, 7, 8, 9, 10 y 11. Capacidad: 4 personas (1–6), 6 (7–10), 8 (11–12) | [`C02-P01a`](capturas/C02-P01a-telefono-mesas-1-10.png), [`C02-P01b`](capturas/C02-P01b-telefono-mesas-3-12.png) |
| 2 | En el teléfono, abrir el menú de productos | — | Productos activos con precio | 23:59 · «12 productos activos», de Agua de horchata $45.00 a Sopa de tortilla $85.00 | [`C02-P02a`](capturas/C02-P02a-telefono-menu.png), [`C02-P02b`](capturas/C02-P02b-telefono-menu-fin.png) |
| 3–4 | Ejecutar `node capturas-web.mjs paridad` | sesión web de `mesero@pruebas.com` | Respuestas `200` del proxy del frontend | 23:58 · `GET /api/v1/tables` → 200, 12 mesas; `GET /api/v1/products` → 200, 12 productos activos | [`C02-web-mesas`](respuestas/C02-web-mesas.json.txt), [`C02-web-productos`](respuestas/C02-web-productos.json.txt) |
| 5 | Comparar con la vista web del mesero | — | Mismas mesas, estados y productos que en los pasos 1 y 2 | 23:58 · La web muestra «Ocupación 7/12» con las mismas mesas libres y ocupadas y las mismas capacidades. Los 12 productos y precios de la API coinciden uno a uno con el menú del teléfono | [`C02-P05`](capturas/C02-P05-mesero-web-mesas.png) |

**Observaciones.** Cada producto muestra la categoría repetida («Bebidas · Bebidas - Agua de horchata») porque la descripción de los datos de prueba empieza con la categoría. Es un detalle de los datos, no de la integración.

### C03 — Un pedido enviado desde el teléfono llega a la cocina web sin recargar

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (teléfono) y KITCHEN (web) |
| Inicio / fin de ejecución | 2026-10-21 00:01 / 2026-10-21 00:02 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Mesa 2 libre (C02); `node capturas-web.mjs cocina` corriendo con la cocina web abierta.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Abrir la cocina web (script) | `cocina@pruebas.com` | Tablero cargado con las órdenes en cocina *(corregido antes de ejecutar: decía «y WebSocket con handshake 101», pero la cocina web consulta cada 5 s y no abre WebSocket)* | 00:01:18 · Tablero con 5 órdenes: ORD-261017020620, …0621, …0622, …0623 y ORD-261017032537. El registro de DevTools no muestra ningún WebSocket | [`C03-P01`](capturas/C03-P01-cocina-web-antes.png), [`C03-registro-cocina-web.txt`](respuestas/C03-registro-cocina-web.txt) |
| 2 | En el teléfono, abrir la mesa libre y agregar dos productos | Mesa 2, 4 comensales, 3× Agua de horchata, 3× Arrachera 300 g | La orden muestra los productos y el total | 00:02:19 · «6 productos · $1095.00» (3 × $45 + 3 × $320) | [`C03-P02`](capturas/C03-P02-telefono-orden.png) |
| 3 | Tocar «Enviar a cocina» | — | Confirmación con folio y total | 00:02:21 · «Pedido registrado · El servidor respondió con el folio ORD-261021060215», estado «Nueva (sin enviar a cocina)», abierta 21/10/2026 12:02:15 a.m. 00:02:25 · «Pedido enviado a cocina», estado «Enviada a cocina», total $1095.00 | [`C03-P03a`](capturas/C03-P03a-telefono-pedido-registrado.png), [`C03-P03b`](capturas/C03-P03b-telefono-enviado-a-cocina.png) |
| 4 | Observar la cocina web sin recargar (script) | — | Aparece la tarjeta con el mismo folio | 00:02:30 · Tarjeta ORD-261021060215, Mesa 2, «recién enviada», «Enviada a cocina», 3× Agua de horchata y 3× Arrachera 300 g en «Pendiente»; el contador «En preparación» pasa de 5 a 6. Llegó a lo más 9 s después del envío, sin recargar | [`C03-P04`](capturas/C03-P04-cocina-web-recibe-pedido.png) |

**Observaciones.** Al volver a «Mesas», la mesa 2 aparece «Ocupada» y el resumen pasa a «12 mesas · 4 libres» (00:02:29, [`C03-OBS`](capturas/C03-OBS-mesa-2-ocupada.png)). La cocina web no usa el WebSocket `/ws` del backend: consulta las órdenes cada 5 s (`KITCHEN_POLL_INTERVAL_MS` en `kitchen-view.tsx`). Para este criterio basta; para CAL-140, cuyo caso 3 verifica la conexión en tiempo real desde el frontend, es un hallazgo relevante.

### C04 — Sin conexión, la app avisa y permite reintentar

| Campo | Valor |
|---|---|
| Criterio cubierto | 4 |
| Prioridad | Media |
| Rol que ejecuta | WAITER |
| Inicio / fin de ejecución | 2026-10-21 00:05 / 2026-10-21 00:05 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Sesión de mesero en el teléfono, en la lista de mesas.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Activar el modo avión y deslizar para recargar las mesas | — | Aviso «Error al consultar las mesas» con «No se pudo conectar con el servidor. Revisa tu conexión a internet.» y botón «Reintentar» | 00:05:02 · Con el modo avión activo (ícono en la barra de estado), aparece exactamente ese aviso y el botón «Reintentar» | [`C04-P01`](capturas/C04-P01-sin-conexion.png) |
| 2 | Desactivar el modo avión y tocar «Reintentar» | — | La lista de mesas vuelve a cargar | 00:05:11 · Con Wi-Fi de vuelta, «Reintentar» carga «12 mesas · 4 libres», con la mesa 2 ocupada como la dejó C03 | [`C04-P02`](capturas/C04-P02-reintento-ok.png) |

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | — | — | Ninguno | — | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 4 | 4 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- El teléfono no se automatiza: sus capturas se tomaron a mano en Expo Go. Playwright solo capturó el lado web (C02 paso 5, C03 pasos 1 y 4), con una banda que estampa la URL y la hora.
- La app corrió en Expo Go, no compilada para tienda.
- El resultado esperado de C03 paso 1 se corrigió antes de ejecutarlo: la cocina web no abre WebSocket, consulta cada 5 s.
- La orden de prueba ORD-261021060215 (mesa 2) quedó abierta en el restaurante `pruebas`.

**Conclusión del ejecutor.** Los cuatro criterios de CAL-139 se cumplen contra `7a250be` en Azure: la app inicia sesión por HTTPS y conserva la sesión en la cookie nativa tras cerrarse, muestra las mismas mesas y productos que la web, envía pedidos que llegan a la cocina web en segundos sin recargar, y avisa sin conexión con opción de reintentar. Para CAL-140 queda el hallazgo de que la cocina web no usa el WebSocket del backend.

## 9. Anexo SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| App móvil ↔ API (HTTPS, cookie nativa) | C01, C02, C04 | Capturas del teléfono y `respuestas/C01-login.txt` |
| Frontend ↔ API (proxy `/api/v1/*`) | C02 | `respuestas/C02-web-*.json.txt` |
| App móvil → API → cocina web | C03 | Folio ORD-261021060215 en el teléfono y en el tablero, [`respuestas/C03-registro-cocina-web.txt`](respuestas/C03-registro-cocina-web.txt). La cocina web consulta cada 5 s; no usa `/ws` |

**Reproducir las capturas web.** El script está en [`playwright/capturas-web.mjs`](playwright/capturas-web.mjs). Necesita Playwright y Google Chrome, y lee la contraseña de `C9_TEST_PASSWORD`.

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
