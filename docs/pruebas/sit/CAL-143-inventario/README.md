# Evidencia de prueba · CAL-143 — SIT · Inventario

> **v2 · re-ejecución.** La v1 (2026-10-26) se retiró: sus respuestas HTTP no provenían del backend desplegado (IDs que no son UUID, `Authorization: Bearer` y errores con un formato que el código no produce) y registraba una revisión que no ocurrió. Ver el comentario del 27-oct en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat). Esta versión se ejecutó de nuevo, completa, sobre Azure.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-143](https://linear.app/callejon19/issue/CAL-143/sit-inventario) — SIT · Inventario |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Inventario (web: `/inventory`; backend `inventory`) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` (la web la consume por el proxy `/api/v1/*` de Next.js) |
| Commit probado (API) | `fb36760`, rama `fix/cal-169-hotfix-azure` (base `ea9b5c1` + corrección de `ClientIp`; sin migraciones nuevas) |
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
| Esta tarea (CAL-143) | 2026-10-21 | 2026-10-21 | 2026-10-21 | | v1 retirada el 2026-10-27; re-ejecución el 2026-10-28 |
| Preparación del entorno y datos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:17 | 2026-10-28 14:18 | — |
| Ejecución de los casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:18 | 2026-10-28 14:18 | — |
| Re-prueba de defectos | | | | | Sin defectos |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que un movimiento que registra cocina en su pantalla llega por la API de Azure a la existencia que ve el administrador, y que la baja de un insumo, hecha por el administrador en otra sesión, impide moverlo aunque cocina ya tuviera abierto el diálogo.

**Fuera de alcance.** Entradas, mermas y ajustes por conteo y la valuación del inventario.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Cocina registra una salida de insumo y el administrador ve la existencia actualizada. | C01 |
| 2. Movimiento sobre un insumo dado de baja: rechazo con mensaje. | C02 |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido y `/actuator/health` en `UP` — [`respuestas/health.txt`](respuestas/health.txt), 2026-10-28 14:17
- [x] Commit desplegado tomado del registro de despliegue: API `fb36760`, frontend `e6a6782`
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Insumo de prueba dado de alta por el administrador desde «Nuevo insumo» con existencia inicial de 10 kg: `POST /api/v1/inventory/items` → **201**, `stock: 10`, 14:18:02 — [`PREP-01`](capturas/PREP-01-nuevo-insumo.png), [`PREP-02`](capturas/PREP-02-insumo-con-10-kg.png)

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Administrador | `admin@pruebas.com` (ADMIN) | Alta de usuarios de prueba del 2026-10-16 |
| Cocina | `cocina@pruebas.com` (KITCHEN) | Alta de usuarios de prueba del 2026-10-16 |
| Insumo | «SIT CAL-143 Tortilla 1417», kg, mínimo 2 kg, costo $25.00, `64093307-dba5-47c8-85bb-32724f8042fc` | Preparación |
| Contraseña | La de las cuentas de prueba | Vive fuera del repositorio; el script la lee de `C9_TEST_PASSWORD` |

## 6. Casos de prueba

Los pasos los ejecutó [`playwright/sit-inventario.mjs`](playwright/sit-inventario.mjs) con dos sesiones de navegador simultáneas, administrador y cocina. Las peticiones de la interfaz y la enviada directo por el proxy están en [`peticiones-de-la-interfaz.txt`](respuestas/peticiones-de-la-interfaz.txt). Cada captura lleva una banda roja con la URL y la hora.

### C01 — La salida que registra cocina se refleja en la existencia del administrador

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | KITCHEN (`cocina@pruebas.com`) y ADMIN (`admin@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:18` / `2026-10-28 14:18` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** El insumo existe, activo, con 10 kg.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Cocina: abrir «Inventario» | — | El insumo con 10 kg | 14:18:05 · Fila «SIT CAL-143 Tortilla 1417», 10 kg | [`C01-P01`](capturas/C01-P01-cocina-inventario.png) |
| 2 | Tocar «Movimiento», tipo «Salida», cantidad y motivo | 3 kg, «Salida para producción, SIT CAL-143» | Diálogo lleno | 14:18:05 · Diálogo «Registrar movimiento» con Salida, 3 | [`C01-P02`](capturas/C01-P02-cocina-registra-salida.png) |
| 3 | Tocar «Registrar» | — | La API registra la salida | 14:18:06 · `POST /api/v1/inventory/movements` `{"movementType":"OUT","quantity":3,…}` → **201** | [`C01-P03`](capturas/C01-P03-cocina-tras-la-salida.png), [`peticiones-de-la-interfaz.txt`](respuestas/peticiones-de-la-interfaz.txt) |
| 4 | Administrador: abrir «Inventario» en su sesión | — | Existencia en 7 kg | 14:18:09 · Fila «SIT CAL-143 Tortilla 1417 · 7 kg · Suficiente · mínimo 2 kg · $25.00 · Activo» | [`C01-P04`](capturas/C01-P04-administrador-ve-7-kg.png) |
| 5 | Pedir los insumos con la sesión del administrador | `GET /api/v1/inventory/items?includeInactive=true` | `stock: 7` | 14:18:09 · **200**, el insumo con `stock: 7` | [`C01-existencia-vista-por-el-administrador.txt`](respuestas/C01-existencia-vista-por-el-administrador.txt) |

**Observaciones.** Ninguna.

### C02 — Un insumo dado de baja no admite movimientos y el rechazo trae mensaje

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Alta |
| Rol que ejecuta | KITCHEN (`cocina@pruebas.com`) y ADMIN (`admin@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:18` / `2026-10-28 14:18` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** El insumo está activo con 7 kg; cocina tiene su inventario abierto.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Cocina: abrir «Movimiento» del insumo y llenarlo, sin registrar | Salida, 1 kg | Diálogo lleno | 14:18:11 · Diálogo con «stock actual: 7 kg» | [`C02-P01`](capturas/C02-P01-cocina-dialogo-abierto.png) |
| 2 | Administrador: tocar «Dar de baja» en la fila del insumo | — | Insumo inactivo | 14:18:13 · `PATCH /api/v1/inventory/items/64093307-…` `{"active":false}` → **200**, `active: false`, `stock: 7`. Insignia «Inactivo» | [`C02-P02`](capturas/C02-P02-insumo-dado-de-baja.png) |
| 3 | Cocina: tocar «Registrar» en el diálogo que tenía abierto | — | Rechazo con mensaje | 14:18:14 · `POST /api/v1/inventory/movements` → **409**. El diálogo muestra «No se pudo registrar el movimiento · El insumo 'SIT CAL-143 Tortilla 1417' esta dado de baja. Reactivalo para poder moverlo.» | [`C02-P03`](capturas/C02-P03-cocina-rechazo-con-mensaje.png) |
| 4 | Cocina: enviar la salida directo a la API | `{"inventoryItemId":"64093307-…","movementType":"OUT","quantity":1,…}` | Rechazo | 14:18:15 · **409**, mismo mensaje | [`C02-movimiento-sobre-insumo-de-baja.txt`](respuestas/C02-movimiento-sobre-insumo-de-baja.txt) |
| 5 | Cocina: recargar «Inventario» | — | El insumo ya no se puede mover | 14:18:17 · «Sin datos · Todavia no hay insumos.»: cocina solo ve insumos activos y este era el único | [`C02-P04`](capturas/C02-P04-cocina-inventario-final.png) |
| 6 | Pedir los insumos con la sesión del administrador | `GET /api/v1/inventory/items?includeInactive=true` | Existencia sin cambios | 14:18:17 · **200**, `stock: 7`, `active: false` | [`C02-existencia-final.txt`](respuestas/C02-existencia-final.txt) |

**Respuesta HTTP literal**

```text
# Cocina envía directo a la API una salida sobre el insumo dado de baja
# 2026-10-28 14:18:15 America/Mexico_City · por el proxy https://restohub.azurewebsites.net
POST /api/v1/inventory/movements
Content-Type: application/json

{
  "inventoryItemId": "64093307-dba5-47c8-85bb-32724f8042fc",
  "movementType": "OUT",
  "quantity": 1,
  "reason": "Intento directo, SIT CAL-143"
}

HTTP 409
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Regla de negocio",
  "status": 409,
  "detail": "El insumo 'SIT CAL-143 Tortilla 1417' esta dado de baja. Reactivalo para poder moverlo.",
  "instance": "/api/v1/inventory/movements"
}
```

**Observaciones.** Ninguna. El insumo queda dado de baja con 7 kg en el restaurante `pruebas`: no hay borrado de insumos.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | — | — | Sin defectos | — | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 2 | 2 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- **Commit de la API fuera de `main`.** `fb36760` es un hotfix desplegado sin fusionar (PR #105). No toca inventario, pero el código probado no es exactamente el de `main`.
- **Solo web.** La app móvil no tiene inventario; no aplica.

**Conclusión del ejecutor.** Los dos criterios se cumplen sobre Azure: la salida de 3 kg que registró cocina dejó la existencia en 7 kg para el administrador, y el insumo dado de baja rechazó el movimiento con 409 y un mensaje claro, tanto desde el diálogo abierto como directo en la API.

## 9. Anexos por tipo de prueba

### SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Frontend ↔ API (proxy `/api/v1/*`) | C01, C02 | `POST /inventory/movements` 201 y 409, `PATCH /inventory/items/{id}` 200, registrados desde el navegador |
| App móvil ↔ API | — | No aplica |
| API ↔ PostgreSQL (RLS en la base real) | C01, C02 | El movimiento de cocina y la baja del administrador, en sesiones distintas, se leen de inmediato en la otra |
| API ↔ tiempo real (`/ws`) | — | No aplica |

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
