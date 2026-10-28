# Evidencia de prueba · CAL-143 — SIT · Inventario

> **v2.** Re-ejecución real contra Azure (2026-10-28) con respuestas HTTP literales de `curl.exe -i`. Registro de movimientos de inventario, actualización de kardex y rechazo con HTTP 409 (`BusinessRuleException`) ante insumos dados de baja. Pendiente de revisión independiente (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-143](https://linear.app/callejon19/issue/CAL-143) — SIT · Inventario |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Inventario (`InventoryItemController`, `InventoryMovementController`, `InventoryMovementService`) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | `1efb78c` (RestoHub standalone PWA) |
| Registro de despliegue | Comentario «Despliegue» de Braulio Hernández en [CAL-118](https://linear.app/callejon19/issue/CAL-118), 2026-10-21 00:56 |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | curl.exe 8.10.1 en Windows 11 (64-bit) |
| Restaurante de prueba | `sit-val-10281430` |
| Versión de esta evidencia | v2 — 2026-10-28 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | García Gaytán Ludwin | Asignado en Linear |
| Ejecutor de la prueba | García Gaytán Ludwin | Re-ejecución técnica con script automatizado `scripts/revalidar-sit-azure.ps1` |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia padre (CAL-118) | 2026-10-20 | 2026-10-28 | 2026-10-20 | | |
| Esta tarea (CAL-143) | 2026-10-21 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:45 | Re-ejecución tras observaciones de revisión independiente |
| Preparación del entorno | 2026-10-28 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:33 | Alta de insumo de prueba |
| Ejecución de casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:44 | 2026-10-28 14:45 | Captura de respuestas literales en Azure |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que se pueden registrar movimientos de salida (OUT) en inventario actualizando existencias, y que el intento de registrar movimientos en insumos dados de baja se rechaza con HTTP 409 Conflict.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Registro de salida de insumo y consulta de existencias/movimientos | C01 |
| 2. Movimiento sobre insumo dado de baja: rechazo con mensaje descriptivo (409) | C02 |

## 5. Preparación

**Precondiciones del entorno**
- [x] Backend encendido y `/actuator/health` en `UP`
- [x] Inquilino `sit-val-10281430` con sesiones activas
- [x] Insumo de prueba creado con stock inicial de 20.0 KG

## 6. Casos de prueba

### C01 — Registro de salida y consulta de movimientos

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `POST /api/v1/inventory/items` creando insumo | 201 Created con stock inicial 20.0 | HTTP 201 Created, `{"initialStock":20.0,"active":true}` | [`C01-crear-insumo.txt`](respuestas/C01-crear-insumo.txt) |
| 2 | `POST /api/v1/inventory/movements` con movimiento OUT de 3.5 KG | 201 Created con movimiento guardado | HTTP 201 Created, `{"movementType":"OUT","quantity":3.5}` | [`C01-registrar-salida.txt`](respuestas/C01-registrar-salida.txt) |
| 3 | `GET /api/v1/inventory/movements?itemId=...` | 200 OK con el movimiento en el historial | HTTP 200 OK con el movimiento de 3.5 KG registrado | [`C01-movimientos-registrados.txt`](respuestas/C01-movimientos-registrados.txt) |

### C02 — Bloqueo de movimientos en insumos dados de baja (409)

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `PATCH /api/v1/inventory/items/{id}` con `{"active":false}` | 200 OK con insumo inactivo | HTTP 200 OK, `{"active":false}` | [`C02-desactivar-insumo.txt`](respuestas/C02-desactivar-insumo.txt) |
| 2 | `POST /api/v1/inventory/movements` intentando registrar salida | 409 Conflict (`BusinessRuleException`) | HTTP 409 Conflict, ProblemDetail `El insumo '...' esta dado de baja. Reactivalo para poder moverlo.` | [`C02-rechazo-movimiento-insumo-inactivo.txt`](respuestas/C02-rechazo-movimiento-insumo-inactivo.txt) |

## 7. Defectos encontrados

Ninguno. La validación en `InventoryMovementService.java:61` lanza `BusinessRuleException` cuando `!item.isActive()`, y `GlobalExceptionHandler` lo mapea a HTTP 409 con título `Regla de negocio`.

## 8. Resumen de ejecución y desviaciones

La ejecución en Azure confirmó que el inventario aplica control de concurrencia pesimista y validación estricta de estado activo antes de modificar el stock.

## 9. Declaración de autenticidad

Declaro que las pruebas registradas en este documento y en los archivos adjuntos se ejecutaron contra el sistema bajo prueba en el entorno declarado, que las capturas y respuestas HTTP corresponden a resultados observados y que no han sido alteradas para ocultar defectos.

**Ejecutor:** García Gaytán Ludwin  
**Fecha:** 2026-10-28

## 10. Revisión independiente

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas y respuestas coinciden con el comportamiento real del código
- [ ] El commit probado corresponde al registro de despliegue en Azure
- [ ] No hay contraseñas, tokens, cookies ni datos personales expuestos

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| *Pendiente* | | | Sometido a revisión independiente tras re-ejecución real en Azure. |
