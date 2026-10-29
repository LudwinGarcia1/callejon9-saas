# Evidencia de prueba · CAL-137 — SIT · Mesas

> **v2 · re-ejecución.** La v1 (2026-10-26) se retiró: sus respuestas HTTP no provenían del backend desplegado (IDs que no son UUID, `Authorization: Bearer`, un estado `AVAILABLE` que no existe y mensajes que el código no produce) y registraba una revisión que no ocurrió. Ver el comentario del 27-oct en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat). Esta versión se ejecutó de nuevo, completa, sobre Azure.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-137](https://linear.app/callejon19/issue/CAL-137/sit-mesas) — SIT · Mesas |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Mesas (web: `/waiter` y `/waiter/order/{id}`; backend `table` y `order`) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` (la web la consume por el proxy `/api/v1/*` de Next.js) |
| Commit probado (API) | `fb36760`, rama `fix/cal-169-hotfix-azure` (base `ea9b5c1` + corrección de `ClientIp`; sin migraciones nuevas) |
| Registro de despliegue | API: comentario «Despliegue» de Braulio Hernández en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat), 2026-10-28 10:56. Frontend: comentario «Despliegue» de Octavio Duarte en CAL-118, 2026-10-22 |
| Commit del frontend | `e6a6782` (`main`) |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | Brave 156.1.98.60 (Chromium 156.0.8078.48) automatizado con Playwright 1.56.1 en Garuda Linux, ventana de 1440 × 900; dos contextos de navegador independientes, uno por mesero |
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
| Esta tarea (CAL-137) | 2026-10-21 | 2026-10-21 | 2026-10-21 | | v1 retirada el 2026-10-27; re-ejecución el 2026-10-28 |
| Preparación del entorno y datos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:07 | 2026-10-28 14:07 | — |
| Ejecución de los casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:07 | 2026-10-28 14:08 | — |
| Re-prueba de defectos | | | | | Sin defectos |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que el estado de las mesas es el mismo para dos clientes que operan a la vez sobre la API de Azure: al abrir una orden la mesa se ocupa para ambos, al cancelarla se libera para ambos, y si dos clientes intentan abrir la misma mesa solo uno gana y el otro recibe un 409.

**Fuera de alcance.** La app móvil: por decisión del responsable, esta re-ejecución cubre solo el cliente web. Donde el criterio dice «en la web y en la app móvil» o «desde el otro cliente», el segundo cliente es **una segunda sesión web** de otro mesero, en un contexto de navegador independiente. Eso prueba la sincronización entre dos clientes, pero no la app móvil (ver sección 8). La liberación de la mesa al **cobrar** se verifica en [CAL-141](../CAL-141-cobro-pagos/README.md); aquí se cubre la de **cancelar**.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Al abrir una orden, la mesa pasa a ocupada en la web y en la app móvil. | C01 (dos sesiones web). App móvil: ⏭️ No ejecutado |
| 2. Al cobrar o cancelar la orden, la mesa vuelve a libre en ambos. | C02 (cancelar, dos sesiones web). Cobrar: CAL-141 |
| 3. Una mesa ocupada no se puede abrir desde el otro cliente (409). | C03 (segunda sesión web) |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido y `/actuator/health` en `UP` — [`respuestas/health.txt`](respuestas/health.txt), 2026-10-28 14:07
- [x] Commit desplegado tomado del registro de despliegue: API `fb36760`, frontend `e6a6782`
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Dos mesas libres: la 1 (C01 y C02) y la 2 (C03)

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Mesero 1 | `mesero@pruebas.com` (WAITER) | Alta de usuarios de prueba del 2026-10-16 |
| Mesero 2 | `mesero2@pruebas.com` (WAITER) | Alta desde la web del administrador en la SIT de CAL-138 |
| Orden de C01 y C02 | `d0945e7b-97b0-45ae-bc62-3c3500d643d6`, folio `ORD-261028200744`, mesa 1 | Abierta por el mesero 1 desde la web en C01 |
| Orden de C03 | `3494cb88-dc57-4c8b-8579-89f2542e1e71`, folio `ORD-261028200752`, mesa 2 | Abierta por el mesero 1 desde la web en C03 |
| Contraseña | La de las cuentas de prueba | Vive fuera del repositorio; el script la lee de `C9_TEST_PASSWORD` |

## 6. Casos de prueba

Los pasos los ejecutó [`playwright/sit-mesas.mjs`](playwright/sit-mesas.mjs) (`casos` y después `limpieza`) con dos sesiones de navegador simultáneas, una por mesero. Las peticiones que hizo la interfaz están en [`C01-C02-C03-peticiones-de-la-interfaz.txt`](respuestas/C01-C02-C03-peticiones-de-la-interfaz.txt); las lecturas de mesas y el intento directo de C03 se enviaron desde la sesión del mesero por el proxy `/api/v1/*`. El orden real fue C01 → C03 → C02. Cada captura lleva una banda roja con la URL y la hora.

### C01 — Al abrir una orden, la mesa queda ocupada para los dos clientes

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 (dos sesiones web) |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (`mesero@pruebas.com` y `mesero2@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:07` / `2026-10-28 14:07` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Ambos meseros con sesión web; la mesa 1 libre.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Mesero 2: abrir «Mesas» | — | Mesa 1 libre | 14:07:42 · Mesa 1 «Libre · 4 lugares · Lista para sentar» | [`C01-P01`](capturas/C01-P01-mesero-2-mesa-libre.png) |
| 2 | Mesero 1: tocar la mesa 1 y abrirla | 2 comensales | La API abre la orden | 14:07:44 · `POST /api/v1/orders` → **201**, folio `ORD-261028200744`, `status: NEW` | [`C01-P02`](capturas/C01-P02-mesero-1-orden-abierta.png) |
| 3 | Mesero 1: volver a «Mesas» | — | Mesa 1 ocupada | 14:07:46 · Mesa 1 «Ocupada · $0.00 · 02:07 p.m. · ORD-261028200744» | [`C01-P03`](capturas/C01-P03-mesero-1-mesa-ocupada.png) |
| 4 | Mesero 2: recargar «Mesas» | — | Mesa 1 ocupada también para él | 14:07:48 · Mesa 1 «Ocupada · ORD-261028200744» | [`C01-P04`](capturas/C01-P04-mesero-2-mesa-ocupada.png) |
| 5 | Mesero 2: pedir las mesas a la API | `GET /api/v1/tables` | Mesa 1 en `OCCUPIED` | 14:07:49 · **200**, mesa 1 `status: OCCUPIED` | [`C01-mesas-vistas-por-el-mesero-2.txt`](respuestas/C01-mesas-vistas-por-el-mesero-2.txt) |

**Observaciones.** La rejilla de mesas se refresca sola cada 10 s (`TABLES_POLL_INTERVAL_MS`); el paso 4 recarga la página para no esperar el sondeo.

### C02 — Al cancelar la orden, la mesa vuelve a libre para los dos clientes

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 (cancelar; dos sesiones web) |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (`mesero@pruebas.com` y `mesero2@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:07` / `2026-10-28 14:08` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** La mesa 1 ocupada por la orden `ORD-261028200744` de C01, en «Nueva».

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Mesero 1: abrir la orden y tocar «Cancelar orden» | — | Diálogo de confirmación | 14:07:57 · Diálogo «Cancelar orden ORD-261028200744» | [`C02-P01`](capturas/C02-P01-confirmar-cancelacion.png) |
| 2 | Tocar «Sí, cancelar orden» | — | La API cancela la orden | 14:07:58 · `POST /api/v1/orders/d0945e7b-…/cancel` → **200**, `status: CANCELED` | [`C02-P02`](capturas/C02-P02-orden-cancelada.png), [`C01-C02-C03-peticiones-de-la-interfaz.txt`](respuestas/C01-C02-C03-peticiones-de-la-interfaz.txt) |
| 3 | Mesero 1: «Mesas» | — | Mesa 1 libre | 14:08:00 · Mesa 1 «Libre · Lista para sentar» | [`C02-P03`](capturas/C02-P03-mesero-1-mesa-libre.png) |
| 4 | Mesero 2: recargar «Mesas» | — | Mesa 1 libre también para él | 14:08:01 · Mesa 1 «Libre · Lista para sentar» | [`C02-P04`](capturas/C02-P04-mesero-2-mesa-libre.png) |
| 5 | Mesero 2: pedir las mesas a la API | `GET /api/v1/tables` | Mesa 1 en `FREE` | 14:08:02 · **200**, mesa 1 `status: FREE` | [`C02-mesas-vistas-por-el-mesero-2.txt`](respuestas/C02-mesas-vistas-por-el-mesero-2.txt) |

**Observaciones.** Ninguna.

### C03 — Si otro cliente ya ocupó la mesa, abrirla responde 409

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 (segunda sesión web) |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (`mesero@pruebas.com` y `mesero2@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:07` / `2026-10-28 14:07` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** La mesa 2 libre para los dos meseros.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Mesero 2: tocar la mesa 2 (libre) y escribir comensales, sin confirmar | 3 comensales | Diálogo «Abrir mesa 2» | 14:07:51 · Diálogo abierto con 3 comensales | [`C03-P01`](capturas/C03-P01-mesero-2-dialogo-abierto.png) |
| 2 | Mesero 1: abrir la mesa 2 desde su sesión | 2 comensales | La API le asigna la mesa | 14:07:52 · `POST /api/v1/orders` → **201**, folio `ORD-261028200752` | [`C03-P02`](capturas/C03-P02-mesero-1-abre-primero.png) |
| 3 | Mesero 2: tocar «Abrir orden» en el diálogo que tenía abierto | — | 409 y mensaje al usuario | 14:07:53 · `POST /api/v1/orders` → **409** «La mesa 2 no esta disponible (OCCUPIED).». El diálogo muestra «No se pudo abrir la mesa» con ese detalle | [`C03-P03`](capturas/C03-P03-mesero-2-rechazo-409.png) |
| 4 | Mesero 2: pedir lo mismo directo a la API | `{"tableId":"0568fb35-…","guestCount":3}` | 409 | 14:07:55 · **409**, mismo detalle | [`C03-abrir-mesa-ocupada-api.txt`](respuestas/C03-abrir-mesa-ocupada-api.txt) |
| 5 | Mesero 1: consultar su orden | `GET /api/v1/orders/3494cb88-…` | Intacta | 14:07:55 · **200**, `status: NEW` | [`C03-orden-del-mesero-1-intacta.txt`](respuestas/C03-orden-del-mesero-1-intacta.txt) |

**Respuesta HTTP literal**

```text
# El mesero 2 pide abrir directo en la API la mesa que ya ocupa el mesero 1
# 2026-10-28 14:07:55 America/Mexico_City · por el proxy https://restohub.azurewebsites.net
POST /api/v1/orders
Content-Type: application/json

{
  "tableId": "0568fb35-160c-4be4-a0cb-cc8c42962644",
  "guestCount": 3
}

HTTP 409
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Regla de negocio",
  "status": 409,
  "detail": "La mesa 2 no esta disponible (OCCUPIED).",
  "instance": "/api/v1/orders"
}
```

**Observaciones.** El mensaje que ve el mesero incluye el valor interno del estado en inglés, «(OCCUPIED)». No es un defecto funcional; se deja como posible mejora de redacción.

**Limpieza.** 14:08:17 · el mesero 1 cancela la orden de C03: `POST …/cancel` → **200**, `status: CANCELED` — [`LIMPIEZA-cancelar-orden-c03.txt`](respuestas/LIMPIEZA-cancelar-orden-c03.txt), [`LIMPIEZA-mesas-al-final`](capturas/LIMPIEZA-mesas-al-final.png).

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | — | — | Sin defectos | — | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 3 | 3 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- **App móvil sustituida por una segunda sesión web.** Por urgencia, el responsable decidió cubrir solo el cliente web. La sincronización entre dos clientes y el 409 quedan probados con dos sesiones web independientes; la app móvil en un teléfono sigue pendiente.
- **Cobro.** La liberación de la mesa al cobrar se cubre en CAL-141, no aquí.
- **Commit de la API fuera de `main`.** `fb36760` es un hotfix desplegado sin fusionar (PR #105). No toca mesas ni órdenes, pero el código probado no es exactamente el de `main`.

**Conclusión del ejecutor.** Con dos clientes web, los tres criterios se cumplen sobre Azure: la mesa se ocupa y se libera igual para ambos, y la API resuelve la carrera por la misma mesa con un 409 que la interfaz explica al usuario. Falta la verificación con la app móvil.

## 9. Anexos por tipo de prueba

### SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Frontend ↔ API (proxy `/api/v1/*`) | C01, C02, C03 | `POST /orders` 201 y 409, `POST /orders/{id}/cancel` 200, registrados desde el navegador |
| App móvil ↔ API | — | No ejecutado (ver sección 8) |
| API ↔ PostgreSQL (RLS en la base real) | C01, C02, C03 | El estado de la mesa que escribe una sesión es el que lee la otra; el bloqueo de fila de `occupy` decide la carrera de C03 |
| API ↔ tiempo real (`/ws`) | — | No aplica: la rejilla de mesas usa sondeo cada 10 s |

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
