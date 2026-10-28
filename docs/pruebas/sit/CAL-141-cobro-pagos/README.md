# Evidencia de prueba · CAL-141 — SIT · Cobro y pagos

> **v2 · re-ejecución.** La v1 (2026-10-26) se retiró: sus respuestas HTTP no provenían del backend desplegado (IDs que no son UUID y cuerpos de cobro que el `CheckoutRequest` desplegado no acepta). Ver el comentario del 27-oct en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat). Esta versión se ejecutó de nuevo, completa, sobre Azure.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-141](https://linear.app/callejon19/issue/CAL-141/sit-cobro-y-pagos) — SIT · Cobro y pagos |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Cobro y pagos (web: `/cashier`, `/kitchen`, `/waiter`; backend `sale`, `kitchen` y `order`) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` (la web la consume por el proxy `/api/v1/*` de Next.js) |
| Commit probado (API) | `fb36760`, rama `fix/cal-169-hotfix-azure` (base `ea9b5c1` + corrección de `ClientIp`; sin migraciones nuevas; **sin** sesiones de caja de CAL-98) |
| Registro de despliegue | API: comentario «Despliegue» de Braulio Hernández en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat), 2026-10-28 10:56. Frontend: comentario «Despliegue» de Octavio Duarte en CAL-118, 2026-10-22 |
| Commit del frontend | `e6a6782` (`main`) |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | Brave 156.1.98.60 (Chromium 156.0.8078.48) automatizado con Playwright 1.56.1 en Garuda Linux, ventana de 1440 × 900; un contexto de navegador por rol |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v2 · 2026-10-28 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Octavio Duarte | Asignado en Linear (reasignado el 2026-10-28) |
| Ejecutor de la prueba | Octavio Duarte | Script de Playwright de esta carpeta |
| Revisor | Braulio Hernández Mejía | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-118) | 2026-10-20 | 2026-10-21 | 2026-10-20 | | Sigue abierta: 7 SIT se re-ejecutan |
| Esta tarea (CAL-141) | 2026-10-21 | 2026-10-21 | 2026-10-21 | | v1 retirada el 2026-10-27; re-ejecución el 2026-10-28 |
| Preparación del entorno y datos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:11 | 2026-10-28 14:12 | — |
| Ejecución de los casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:12 | 2026-10-28 14:12 | — |
| Re-prueba de defectos | | | | | Sin defectos |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que el cobro integra caja, cocina, mesero y API sobre Azure: una orden lista se cobra con pagos divididos y propina, la venta cierra y la mesa se libera; un cobro que no cubre el total se rechaza sin tocar la orden; y una orden cobrada ya no admite productos aunque el mesero la tenga abierta en su pantalla.

**Fuera de alcance.** La app móvil (no cobra). Las sesiones de caja de CAL-98 no están desplegadas en `fb36760`. La descarga del ticket se prueba en [CAL-142](../CAL-142-tickets/README.md).

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Caja cobra una orden lista con pago dividido (efectivo y tarjeta) y propina: la venta cierra y la mesa se libera. | C01 |
| 2. Pagos que no cubren el total: 422 y la orden sigue abierta. | C02 |
| 3. El mesero intenta agregar productos durante el cobro: la orden no cambia. | C03 |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido y `/actuator/health` en `UP` — [`respuestas/health.txt`](respuestas/health.txt), 2026-10-28 14:11
- [x] Commit desplegado tomado del registro de despliegue: API `fb36760`, frontend `e6a6782`
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Orden A lista para cobrar: el mesero abrió la mesa 1, agregó 2 × Guacamole con totopos ($95) y 1 × Margarita ($135), la envió a cocina (14:11:46), y cocina marcó cada partida «En preparación» y luego «Listo» desde su tablero (14:11:59). Estado `READY`, total $325.00 — [`PREP-01`](capturas/PREP-01-orden-a-enviada-a-cocina.png), [`PREP-02`](capturas/PREP-02-cocina-orden-a-lista.png)
- [x] Orden B por cobrar: mesa 2, 1 × Queso fundido ($110), enviada a cocina (`SENT`)

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Mesero | `mesero@pruebas.com` (WAITER) | Alta de usuarios de prueba del 2026-10-16 |
| Segundo mesero | `mesero2@pruebas.com` (WAITER), solo para ver la rejilla de mesas | Alta en la SIT de CAL-138 |
| Cocina | `cocina@pruebas.com` (KITCHEN) | Alta de usuarios de prueba del 2026-10-16 |
| Caja | `caja@pruebas.com` (CASHIER) | Alta de usuarios de prueba del 2026-10-16 |
| Administrador | `admin@pruebas.com` (ADMIN), solo para la limpieza | Alta de usuarios de prueba del 2026-10-16 |
| Orden A | `53cf75fc-6681-49c7-8b05-3605cd565fb8`, folio `ORD-261028201139`, mesa 1 | Preparación |
| Orden B | `3ef3b8fe-2f3c-4722-a868-60ed3f7be29b`, folio `ORD-261028201148`, mesa 2 | Preparación |
| Contraseña | La de las cuentas de prueba | Vive fuera del repositorio; el script la lee de `C9_TEST_PASSWORD` |

## 6. Casos de prueba

Los pasos los ejecutó [`playwright/sit-cobro.mjs`](playwright/sit-cobro.mjs) (`casos` y después `limpieza`) con una sesión de navegador por rol, simultáneas. Las peticiones de la interfaz y las que se mandaron desde la sesión por el proxy `/api/v1/*` están en [`peticiones-de-la-interfaz.txt`](respuestas/peticiones-de-la-interfaz.txt). El orden real fue preparación → C02 → C01 → C03. Cada captura lleva una banda roja con la URL y la hora.

### C01 — Caja cobra una orden lista con pago dividido y propina; la mesa se libera

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | CASHIER (`caja@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:12` / `2026-10-28 14:12` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Orden A en `READY` con total $325.00; mesa 1 ocupada.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Caja: elegir la orden A, propina 10 %, «Agregar pago por monto libre» dos veces | Pago 1: Efectivo $200.00; pago 2: Tarjeta $157.50 | Total $357.50 cubierto | 14:12:10 · Subtotal $325.00, propina $32.50, total $357.50; «Falta cubrir» $0.00; «Confirmar cobro» habilitado | [`C01-P01`](capturas/C01-P01-caja-pago-dividido.png) |
| 2 | Tocar «Confirmar cobro» | `{"payments":[{"method":"CASH","amount":200},{"method":"CARD","amount":157.5}],"tipPercent":10}` | La API cierra la venta y emite ticket | 14:12:11 · `POST /api/v1/orders/53cf75fc-…/checkout` → **201**, ticket `TCK-261028201211`, `subtotal: 325`, `tip: 32.5`, `total: 357.5`, `paymentMethod: MIXED`, dos pagos, cambio 0. Aviso «Cobro registrado. Ticket TCK-261028201211.» | [`C01-P02`](capturas/C01-P02-ticket-del-cobro.png), [`peticiones-de-la-interfaz.txt`](respuestas/peticiones-de-la-interfaz.txt) |
| 3 | Consultar la orden | `GET /api/v1/orders/53cf75fc-…` | Orden pagada | 14:12:13 · **200**, `status: PAID` | [`C01-orden-pagada.txt`](respuestas/C01-orden-pagada.txt) |
| 4 | Consultar las mesas y ver la rejilla de un mesero | `GET /api/v1/tables`; `/waiter` con `mesero2@pruebas.com` | Mesa 1 libre | 14:12:14 · mesa 1 `status: FREE`. 14:12:21 · Mesa 1 «Libre · Lista para sentar» | [`C01-mesas-tras-el-cobro.txt`](respuestas/C01-mesas-tras-el-cobro.txt), [`C01-P03`](capturas/C01-P03-mesa-liberada.png) |

**Respuesta HTTP literal** (extracto; completa en [`peticiones-de-la-interfaz.txt`](respuestas/peticiones-de-la-interfaz.txt))

```text
POST /api/v1/orders/53cf75fc-6681-49c7-8b05-3605cd565fb8/checkout
Content-Type: application/json

{"payments":[{"method":"CASH","amount":200},{"method":"CARD","amount":157.5}],"tipPercent":10}

HTTP 201
Content-Type: application/json

  "folio": "TCK-261028201211",
  "subtotal": 325,
  "tip": 32.5,
  "tipPercent": 10,
  "total": 357.5,
  "paymentMethod": "MIXED",
  "payments": [
    { "method": "CASH", "amount": 200, "receivedAmount": 200, "change": 0 },
    { "method": "CARD", "amount": 157.5, "receivedAmount": 157.5, "change": 0 }
  ],
  "change": 0
```

**Observaciones.** Ninguna.

### C02 — Pagos que no cubren el total: 422 y la orden sigue abierta

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Alta |
| Rol que ejecuta | CASHIER (`caja@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:12` / `2026-10-28 14:12` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Orden B en `SENT`, total $110.00.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Caja: elegir la orden B y registrar un pago menor al total | Efectivo $50.00, propina 0 % | La interfaz no deja confirmar | 14:12:04 · Total $110.00, «Falta cubrir» $60.00; «Confirmar cobro» deshabilitado | [`C02-P01`](capturas/C02-P01-caja-pago-insuficiente.png) |
| 2 | Enviar ese cobro a la API desde la sesión de caja (el botón no lo permite) | `{"payments":[{"method":"CASH","amount":50.0}],"tipPercent":0}` | 422 | 14:12:04 · **422** «Los pagos no cubren el total de la cuenta.» | [`C02-pago-insuficiente.txt`](respuestas/C02-pago-insuficiente.txt) |
| 3 | Consultar la orden | `GET /api/v1/orders/3ef3b8fe-…` | Sigue abierta | 14:12:05 · **200**, `status: SENT` | [`C02-orden-sigue-abierta.txt`](respuestas/C02-orden-sigue-abierta.txt) |
| 4 | Volver a la caja | — | La orden sigue por cobrar | 14:12:07 · Mesa 2, `ORD-261028201148`, $110.00, «Enviada a cocina», en la lista «Por cobrar» | [`C02-P02`](capturas/C02-P02-orden-sigue-por-cobrar.png) |

**Respuesta HTTP literal**

```text
# Caja envía pagos que no cubren el total
# 2026-10-28 14:12:04 America/Mexico_City · por el proxy https://restohub.azurewebsites.net
POST /api/v1/orders/3ef3b8fe-2f3c-4722-a868-60ed3f7be29b/checkout
Content-Type: application/json

{
  "payments": [
    {
      "method": "CASH",
      "amount": 50
    }
  ],
  "tipPercent": 0
}

HTTP 422
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Los pagos no cubren el total de la cuenta.",
  "instance": "/api/v1/orders/3ef3b8fe-2f3c-4722-a868-60ed3f7be29b/checkout"
}
```

**Observaciones.** La regla está en dos capas: la interfaz deshabilita «Confirmar cobro» mientras falte cubrir, y la API la hace cumplir aunque alguien salte la interfaz.

### C03 — Una orden cobrada no admite productos aunque el mesero la tenga abierta

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (`mesero@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:12` / `2026-10-28 14:12` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** El mesero abrió la orden A en su pantalla **antes** del cobro de C01 y no la recargó.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Mesero: abrir la orden A antes del cobro | — | Orden «Lista» con su menú | 14:12:01 · Orden `ORD-261028201139` «Lista», $325.00 | [`C03-P01`](capturas/C03-P01-mesero-con-la-orden-abierta.png) |
| 2 | Tras el cobro de C01, en la misma pantalla sin recargar: agregar Queso fundido y tocar «Agregar a la orden» | 1 × Queso fundido | La API rechaza; la orden no cambia | 14:12:21 · `POST /api/v1/orders/53cf75fc-…/items` → **409** «La orden ORD-261028201139 ya esta PAID y no admite mas productos.», mostrado en pantalla | [`C03-P02`](capturas/C03-P02-mesero-intenta-agregar.png), [`peticiones-de-la-interfaz.txt`](respuestas/peticiones-de-la-interfaz.txt) |
| 3 | Enviar lo mismo directo a la API | `{"items":[{"productId":"fca14107-…","quantity":1}]}` | 409 | 14:12:22 · **409**, mismo detalle | [`C03-agregar-a-orden-cobrada.txt`](respuestas/C03-agregar-a-orden-cobrada.txt) |
| 4 | Consultar la orden | `GET /api/v1/orders/53cf75fc-…` | Sin cambios | 14:12:23 · **200**, `status: PAID`, 2 partidas, `total: 325` | [`C03-orden-sin-cambios.txt`](respuestas/C03-orden-sin-cambios.txt) |
| 5 | Recargar la pantalla del mesero | — | La orden se muestra cerrada | 14:12:24 · Insignia «Pagada», 2 partidas, $325.00; «Esta orden ya está cerrada. No se pueden agregar más productos…» y «Orden cerrada · Esta orden ya fue pagada.» | [`C03-P03`](capturas/C03-P03-orden-cerrada.png) |

**Observaciones.** El cobro bloquea la fila de la orden (`findForCheckout` con `PESSIMISTIC_WRITE`) y deja la orden en `PAID` en la misma transacción. Esta prueba demuestra el resultado observable —ningún producto entra después del cobro—; no fuerza la intercalación exacta de dos transacciones simultáneas. Antes del cobro, agregar productos a una orden lista sí está permitido (vuelve a `SENT`) y no se considera «durante el cobro».

**Limpieza.** 14:12:39 · el administrador cancela la orden B, que ya estaba en cocina: `POST …/cancel` → **200**, `status: CANCELED` — [`LIMPIEZA-cancelar-orden-b.txt`](respuestas/LIMPIEZA-cancelar-orden-b.txt), [`LIMPIEZA-mesas-al-final`](capturas/LIMPIEZA-mesas-al-final.png). La venta `TCK-261028201211` ($357.50) queda registrada en el restaurante `pruebas` y la usa [CAL-142](../CAL-142-tickets/README.md) y [CAL-144](../CAL-144-ventas-analitica/README.md).

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | — | — | Sin defectos | — | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 3 | 3 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- **Commit de la API fuera de `main`.** `fb36760` es un hotfix desplegado sin fusionar (PR #105). No incluye las sesiones de caja de CAL-98, que `main` sí exige para cobrar; cuando se despliegue `main`, este cobro debe volver a probarse con una sesión de caja abierta.
- **C02 paso 2 por la API.** La interfaz no permite enviar un cobro insuficiente; el 422 se obtuvo con la misma sesión de caja por el proxy.
- **Solo web.** La app móvil no cobra; no aplica.

**Conclusión del ejecutor.** Los tres criterios se cumplen sobre Azure con el código desplegado: la orden lista se cobró con efectivo y tarjeta más 10 % de propina (ticket `TCK-261028201211`, $357.50) y liberó la mesa; un cobro insuficiente devolvió 422 sin tocar la orden; y la orden cobrada rechazó productos con 409.

## 9. Anexos por tipo de prueba

### SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Frontend ↔ API (proxy `/api/v1/*`) | C01, C02, C03 | `POST /orders/{id}/checkout` 201 y 422, `POST /orders/{id}/items` 409, `POST /kitchen/items/{id}/status` desde el tablero |
| App móvil ↔ API | — | No aplica |
| API ↔ PostgreSQL (RLS en la base real) | C01, C03 | Venta, pagos, ticket, orden y mesa cambian en una sola transacción; la orden cobrada rechaza escrituras |
| API ↔ tiempo real (`/ws`) | — | No aplica: la cocina web consulta por sondeo, sin WebSocket (ver CAL-140) |

## 10. Revisión

*La llena el revisor, que no puede ser quien ejecutó la prueba.*

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas muestran la URL y la hora (banda de la captura automatizada) y coinciden con los pasos
- [ ] Las respuestas HTTP coinciden con el comportamiento real del código (rutas, códigos y mensajes)
- [ ] El commit probado corresponde al registro de despliegue
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las capturas y respuestas no fueron editadas salvo para ocultar secretos, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** Octavio Duarte · **Fecha:** `2026-10-28`
