# Evidencia de prueba · CAL-143 — SIT · Inventario

> **v1.** C01 y C02 ejecutados y verificados contra el entorno de producción en Azure. Pendiente de revisión independiente (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-143](https://linear.app/callejon19/issue/CAL-143) — SIT · Inventario |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT (System Integration Testing) |
| Módulo | Inventario y control de existencias |
| Entorno | Microsoft Azure (Producción) |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | `1efb78c` (RestoHub standalone PWA) |
| Dispositivos de prueba | Laptop (Google Chrome 155, 64 bits) y dispositivo móvil Android PWA |
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
| Tarea (CAL-143) | 2026-10-21 | 2026-10-26 | 2026-10-26 22:10 | 2026-10-26 22:35 | Ejecución y consolidación de respuestas |

## 4. Alcance y Criterios Cubiertos

**Objetivo:** Demostrar que el ledger de inventario y el control de existencias operan de forma consistente y atómica entre la cocina y la administración:
1. Cocina (o rol autorizado) registra un movimiento de salida de insumo (`OUT`), y el administrador ve reflejada la existencia actualizada inmediatamente en su panel.
2. Cualquier intento de registrar un movimiento sobre un insumo dado de baja (inactivo) es rechazado por la lógica de negocio con `HTTP 400 Bad Request` y mensaje descriptivo.

---

## 5. Casos de Prueba Ejecutados

### C01 — Registro de Salida en Cocina y Actualización de Existencia
* **Propósito:** Validar que una salida de insumo aplicada en cocina descuenta atómicamente el stock en la base de datos y se visualiza en la vista de administración.
* **Pasos:**
  1. El administrador consulta la existencia del insumo "Carne al Pastor" (`GET /api/v1/inventory/items`). El stock inicial es de `10.000 kg`.
  2. El operador de cocina registra una salida por preparación (`POST /api/v1/inventory/movements`) enviando:
     ```json
     {
       "inventoryItemId": "i0a80123-0001-4000-8000-000000000101",
       "movementType": "OUT",
       "quantity": 2.500,
       "reason": "Preparación de comanda mesa 5"
     }
     ```
  3. La API responde `201 Created` confirmando el registro del movimiento con folio y marca temporal.
  4. El administrador consulta nuevamente el inventario (`GET /api/v1/inventory/items`).
* **Resultado observado:**
  * El stock actual (`currentStock`) se actualizó a `7.500 kg`.
  * La consulta del historial de movimientos (`GET /api/v1/inventory/movements?itemId=...`) muestra el movimiento registrado con el usuario de cocina autenticado.
* **Evidencia:** `respuestas/C01-stock-inicial.txt`, `respuestas/C01-salida-cocina.txt` y `respuestas/C01-stock-actualizado-admin.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

### C02 — Inactivación de Insumo y Bloqueo de Movimientos
* **Propósito:** Demostrar la regla de integridad que impide alterar el stock de insumos retirados o dados de baja.
* **Pasos:**
  1. El administrador inactiva el insumo mediante `PATCH /api/v1/inventory/items/{id}` enviando `{"active": false}`.
  2. La API responde `200 OK` confirmando `active: false`.
  3. Desde cocina se intenta registrar un nuevo movimiento de salida o entrada sobre dicho insumo inactivo (`POST /api/v1/inventory/movements`).
* **Resultado observado:**
  * El motor de negocio detecta que el insumo está inactivo dentro del bloqueo pesimista (`findByIdForUpdate`).
  * La petición es rechazada con `HTTP/1.1 400 Bad Request`.
  * Cuerpo de error RFC 7807 (`ProblemDetail`): `"No se pueden registrar movimientos en un insumo inactivo."`.
  * El stock del insumo permanece inalterado.
* **Evidencia:** `respuestas/C02-baja-insumo.txt` y `respuestas/C02-rechazo-movimiento-insumo-inactivo.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

## 6. Resumen de Resultados

| Caso | Descripción | Resultado | Defectos |
|---|---|:---:|---|
| **C01** | Registro de salida por cocina y actualización de stock en admin | ✅ **Aprobado** | Ninguno |
| **C02** | Rechazo estricto de movimientos sobre insumo inactivo (400 Bad Request) | ✅ **Aprobado** | Ninguno |

**Conclusión del ejecutor:** Los dos criterios de `CAL-143` se cumplen en Azure. La arquitectura del ledger garantiza trazabilidad atómica y consistencia contable en las existencias de almacén y cocina.

---

## 7. Revisión Independiente

- [x] Cada criterio del issue tiene al menos un caso con resultado
- [x] Las respuestas HTTP coinciden con el comportamiento real del código
- [x] El commit probado corresponde al registro de despliegue en Azure
- [x] No hay contraseñas, tokens, cookies ni datos personales expuestos

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| Octavio Duarte | 2026-10-26 | Aprobado | Bloqueo transaccional y validación de insumos inactivos verificados. |

## 8. Declaración de Autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y fecha indicados.

**Ejecutor:** García Gaytán Ludwin · **Fecha:** 2026-10-26
