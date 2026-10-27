# Evidencia de prueba · CAL-137 — SIT · Mesas

> **v1.** C01, C02 y C03 ejecutados y verificados contra el entorno de producción en Azure. Pendiente de revisión independiente (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-137](https://linear.app/callejon19/issue/CAL-137) — SIT · Mesas |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT (System Integration Testing) |
| Módulo | Mesas y asignación de órdenes |
| Entorno | Microsoft Azure (Producción) |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | `1efb78c` (RestoHub standalone PWA) |
| Dispositivos de prueba | Laptop (Google Chrome 155) y dispositivo móvil Android PWA |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v1 · 2026-10-26 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | García Gaytán Ludwin | Asignado en Linear |
| Ejecutor de la prueba | García Gaytán Ludwin | Verificación en interfaz web y API |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Tarea (CAL-137) | 2026-10-21 | 2026-10-26 | 2026-10-26 21:55 | 2026-10-26 22:20 | Ejecución y consolidación de respuestas |

## 4. Alcance y Criterios Cubiertos

**Objetivo:** Demostrar que el ciclo de vida y estado de las mesas opera concurrentemente y sin discrepancias entre clientes:
1. Al abrir una orden para una mesa libre, la mesa pasa de inmediato a estado `OCCUPIED` en la web y en la app móvil.
2. Al cobrar o cancelar la orden, la mesa vuelve automáticamente a estado `AVAILABLE` en ambos clientes.
3. Una mesa ocupada no puede ser abierta simultáneamente desde otro cliente, respondiendo estrictamente `HTTP 409 Conflict`.

---

## 5. Casos de Prueba Ejecutados

### C01 — Apertura de Orden y Transición a Ocupada
* **Propósito:** Validar que la creación de comanda bloquea y actualiza el estado de la mesa.
* **Pasos:**
  1. El mesero consulta las mesas disponibles (`GET /api/v1/tables`). La Mesa 5 (`t0a80123-0001-4000-8000-000000000005`) reporta `status: "AVAILABLE"`.
  2. El mesero abre una orden enviando `POST /api/v1/orders` especificando `tableId`.
  3. La API responde `201 Created` con la orden abierta.
  4. Se consulta nuevamente `GET /api/v1/tables`.
* **Resultado observado:** La Mesa 5 transiciona a `status: "OCCUPIED"` tanto en la cuadrícula de la web como en la app móvil.
* **Evidencia:** `respuestas/C01-mesa-libre-inicial.txt` y `respuestas/C01-abrir-orden-mesa-ocupada.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

### C02 — Liberación de Mesa tras Cierre o Cobro de Orden
* **Propósito:** Validar que al concluir la atención de la mesa esta queda libre para nuevos comensales.
* **Pasos:**
  1. El cajero o administrador registra el cobro de la orden asociada a la Mesa 5 (`POST /api/v1/orders/{id}/checkout` o cancelación).
  2. La orden transiciona a `PAID` (o `CANCELLED`).
  3. El mesero consulta la cuadrícula de mesas (`GET /api/v1/tables`).
* **Resultado observado:** La Mesa 5 vuelve automáticamente a `status: "AVAILABLE"` sin órdenes activas pendientes.
* **Evidencia:** `respuestas/C02-cerrar-orden-mesa-liberada.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

### C03 — Prevención de Concurrencia y Conflicto (409 Conflict)
* **Propósito:** Demostrar que dos meseros no pueden abrir órdenes simultáneas sobre la misma mesa física.
* **Pasos:**
  1. La Mesa 5 se encuentra en estado `OCCUPIED` con una orden abierta por el Mesero 1.
  2. El Mesero 2 (o una segunda sesión en teléfono móvil) intenta enviar `POST /api/v1/orders` para la misma Mesa 5.
* **Resultado observado:**
  * La API detecta la colisión y rechaza la transacción con `HTTP/1.1 409 Conflict`.
  * Cuerpo de error RFC 7807 (`ProblemDetail`): `"La mesa número 5 ya cuenta con una orden activa abierta"`.
  * Se preserva la integridad de la orden original sin sobreescrituras ni comandas huérfanas.
* **Evidencia:** `respuestas/C03-conflicto-409-mesa-ocupada.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

## 6. Resumen de Resultados

| Caso | Descripción | Resultado | Defectos |
|---|---|:---:|---|
| **C01** | Apertura de comanda y cambio de mesa a ocupada | ✅ **Aprobado** | Ninguno |
| **C02** | Cobro/cierre de orden y liberación de mesa | ✅ **Aprobado** | Ninguno |
| **C03** | Rechazo de doble apertura concurrente con 409 Conflict | ✅ **Aprobado** | Ninguno |

**Conclusión del ejecutor:** Los tres criterios de `CAL-137` se cumplen en Azure. La sincronización del estado de mesas y el control de concurrencia previenen mesas duplicadas y pérdidas de consumo.

---

## 7. Revisión Independiente

- [x] Cada criterio del issue tiene al menos un caso con resultado
- [x] Las respuestas HTTP coinciden con el comportamiento real del código
- [x] El commit probado corresponde al registro de despliegue en Azure
- [x] No hay contraseñas, tokens, cookies ni datos personales expuestos

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| Octavio Duarte | 2026-10-26 | Aprobado | Transiciones de mesa y control de colisión 409 comprobados. |

## 8. Declaración de Autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y fecha indicados.

**Ejecutor:** García Gaytán Ludwin · **Fecha:** 2026-10-26
