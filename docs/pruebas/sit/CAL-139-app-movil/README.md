# Evidencia de prueba · CAL-139 — SIT · App móvil de meseros

> **Borrador v1.** Preparación lista; los casos se ejecutan cuando exista el registro de despliegue del backend (sección 1). Hasta entonces, ningún caso tiene resultado.

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
| Commit probado (API) | *Pendiente del registro de despliegue* |
| Commit de la app móvil | *SHA de `main` desde el que se corre Expo al ejecutar* |
| Registro de despliegue | *Pendiente: comentario «Despliegue» en CAL-118* |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | Teléfono: *modelo, sistema y versión de Expo Go*. Web: Google Chrome 155 con Playwright 1.56.1 en Windows 11 |
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
| Preparación del entorno y datos | 2026-10-20 | 2026-10-20 | 2026-10-20 22:56 | 2026-10-20 23:03 | La API estaba detenida; se encendió y respondió `UP` a las 23:02 |
| Ejecución de los casos | 2026-10-21 | 2026-10-21 | | | |
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
- [ ] Commit desplegado verificado con el registro de despliegue
- [ ] `mobile/.env` con `EXPO_PUBLIC_API_URL=https://app-callejon9-api-prod-3878c6.azurewebsites.net` y `EXPO_PUBLIC_ALLOW_INSECURE_HTTP=false`
- [ ] Expo Go abierto en el teléfono con la app cargada desde `pnpm start` en `mobile/`

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
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

**Precondición.** App recién abierta en Expo Go, sin sesión.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Abrir la app en Expo Go | — | Pantalla de inicio de sesión | | `capturas/C01-P01-login-vacio.png` |
| 2 | Escribir restaurante, correo y contraseña; tocar «Iniciar sesión» | `pruebas`, `mesero@pruebas.com` | Entra a «Mesas» con el aviso «Autenticación exitosa» | | `capturas/C01-P02-mesas-tras-login.png` |
| 3 | Cerrar Expo Go por completo y volver a abrir la app | — | Entra directo a las mesas, sin pedir credenciales | | `capturas/C01-P03-sesion-persistente.png` |

**Respuesta HTTP literal.** La misma llamada de la app (`POST /api/v1/auth/login`), con `Set-Cookie` redactado: `respuestas/C01-login.txt`.

### C02 — El teléfono y la web muestran las mismas mesas y productos

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Media |
| Rol que ejecuta | WAITER |
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

**Precondición.** Sesión de mesero en el teléfono (C01).

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | En el teléfono, abrir la lista de mesas | — | Mesas con número, estado y capacidad | | `capturas/C02-P01-telefono-mesas.png` |
| 2 | En el teléfono, abrir el menú de productos | — | Productos activos con precio | | `capturas/C02-P02-telefono-menu.png` |
| 3–4 | Ejecutar `node capturas-web.mjs paridad` | sesión web de `mesero@pruebas.com` | — | | `respuestas/C02-web-mesas.json.txt`, `respuestas/C02-web-productos.json.txt` |
| 5 | Comparar con la vista web del mesero | — | Mismas mesas, estados y productos que en los pasos 1 y 2 | | `capturas/C02-P05-mesero-web-mesas.png` |

### C03 — Un pedido enviado desde el teléfono llega a la cocina web sin recargar

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (teléfono) y KITCHEN (web) |
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

**Precondición.** Una mesa libre; `node capturas-web.mjs cocina` corriendo con la cocina web abierta.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Abrir la cocina web (script) | `cocina@pruebas.com` | Tablero cargado y WebSocket con handshake `101` | | `capturas/C03-P01-cocina-web-antes.png`, `respuestas/C03-websocket-cocina.txt` |
| 2 | En el teléfono, abrir la mesa libre y agregar dos productos | <mesa y productos> | La orden muestra los productos y el total | | `capturas/C03-P02-telefono-orden.png` |
| 3 | Tocar «Enviar a cocina» | — | Confirmación con folio y total | | `capturas/C03-P03-telefono-confirmacion.png` |
| 4 | Observar la cocina web sin recargar (script) | — | Aparece la tarjeta con el mismo folio | | `capturas/C03-P04-cocina-web-recibe-pedido.png` |

### C04 — Sin conexión, la app avisa y permite reintentar

| Campo | Valor |
|---|---|
| Criterio cubierto | 4 |
| Prioridad | Media |
| Rol que ejecuta | WAITER |
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

**Precondición.** Sesión de mesero en el teléfono, en la lista de mesas.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Activar el modo avión y deslizar para recargar las mesas | — | Aviso «Error al consultar las mesas» con «No se pudo conectar con el servidor. Revisa tu conexión a internet.» y botón «Reintentar» | | `capturas/C04-P01-sin-conexion.png` |
| 2 | Desactivar el modo avión y tocar «Reintentar» | — | La lista de mesas vuelve a cargar | | `capturas/C04-P02-reintento-ok.png` |

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | | | | | |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 4 | 0 | 0 | 0 | 4 |

**Desviaciones y limitaciones.** El teléfono no se automatiza: sus capturas se toman a mano en Expo Go. Playwright solo captura el lado web (C02 paso 5, C03 pasos 1 y 4), con una banda que estampa la URL y la hora.

**Conclusión del ejecutor.** *Pendiente de ejecución.*

## 9. Anexo SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| App móvil ↔ API (HTTPS, cookie nativa) | C01, C02, C04 | Capturas del teléfono y `respuestas/C01-login.txt` |
| Frontend ↔ API (proxy `/api/v1/*`) | C02 | `respuestas/C02-web-*.json.txt` |
| API ↔ tiempo real (`/ws`) | C03 | `respuestas/C03-websocket-cocina.txt` |

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

**Ejecutor:** Octavio Duarte · **Fecha:** *al completar la ejecución*
