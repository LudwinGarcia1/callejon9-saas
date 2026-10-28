# Evidencia de prueba · CAL-137 — SIT · Mesas

> **v2.** Re-ejecución real contra Azure (2026-10-28) con respuestas HTTP literales de `curl.exe -i`. Apertura de comanda, transición a OCCUPIED, bloqueo por concurrencia con HTTP 409 (`BusinessRuleException`) y liberación a FREE tras cancelación. Pendiente de revisión independiente (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-137](https://linear.app/callejon19/issue/CAL-137) — SIT · Mesas |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Mesas y Comandas (`TableController`, `OrderController`, `TableService`) |
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
| Ejecutor de la prueba | García Gaytán Ludwin | Re-ejecución con script automatizado `scripts/revalidar-sit-azure.ps1` |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia padre (CAL-118) | 2026-10-20 | 2026-10-28 | 2026-10-20 | | |
| Esta tarea (CAL-137) | 2026-10-21 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:45 | Re-ejecución tras observaciones de revisión independiente |
| Preparación del entorno | 2026-10-28 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:33 | Alta de mesa para pruebas |
| Ejecución de casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:44 | 2026-10-28 14:45 | Captura de respuestas literales en Azure |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que la apertura de una comanda transiciona la mesa a `OCCUPIED`, que se rechaza la apertura simultánea en la misma mesa con HTTP 409 Conflict, y que al cancelar o cobrar la comanda la mesa retorna a estado `FREE`.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Al abrir una orden, la mesa pasa a ocupada | C01 |
| 2. Una mesa ocupada no se puede abrir desde otro cliente (409) | C02 |
| 3. Al cancelar o cobrar la orden, la mesa vuelve a libre | C03 |

## 5. Preparación

**Precondiciones del entorno**
- [x] Backend encendido y `/actuator/health` en `UP`
- [x] Mesa creada en el restaurante `sit-val-10281430` con capacidad para 4 personas
- [x] Sesión de Mesero iniciada con cookie `access_token`

## 6. Casos de prueba

### C01 — Apertura de comanda y cambio de estado a OCCUPIED

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `POST /api/v1/orders` con `tableId` y `guestCount` | 201 Created con orden en estado NEW | HTTP 201 Created, orden con folio generado | [`C01-abrir-orden-mesa.txt`](respuestas/C01-abrir-orden-mesa.txt) |
| 2 | `GET /api/v1/tables` con Mesero | Mesa en estado OCCUPIED | HTTP 200 OK, `{"status":"OCCUPIED"}` | [`C01-mesa-ocupada.txt`](respuestas/C01-mesa-ocupada.txt) |

### C02 — Bloqueo concurrente por mesa ocupada (409 Conflict)

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `POST /api/v1/orders` intentando abrir la misma mesa ya ocupada | 409 Conflict (`BusinessRuleException`) | HTTP 409 Conflict, ProblemDetail `La mesa N no esta disponible (OCCUPIED).` | [`C02-conflicto-409-mesa-ocupada.txt`](respuestas/C02-conflicto-409-mesa-ocupada.txt) |

### C03 — Liberación de mesa a FREE tras cancelación

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `POST /api/v1/orders/{orderId}/cancel` con Mesero | 200 OK con orden cancelada | HTTP 200 OK, orden en estado CANCELLED | [`C03-cancelar-orden.txt`](respuestas/C03-cancelar-orden.txt) |
| 2 | `GET /api/v1/tables` con Mesero | Mesa regresa a estado FREE | HTTP 200 OK, `{"status":"FREE"}` | [`C03-mesa-liberada.txt`](respuestas/C03-mesa-liberada.txt) |

## 7. Defectos encontrados

Ninguno. El comportamiento corresponde a `OrderService.java` y `TableService.java` en Spring Boot.

## 8. Resumen de ejecución y desviaciones

La máquina de estados de mesas en Azure opera con integridad: el bloqueo transaccional impide comandas concurrentes sobre la misma mesa y la liberación a FREE es inmediata.

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
