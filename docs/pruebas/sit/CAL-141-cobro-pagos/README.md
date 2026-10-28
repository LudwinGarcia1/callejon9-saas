# Evidencia de prueba — CAL-141 — SIT — Cobro y pagos

> **v1.** C01, C02 y C03 ejecutados y verificados contra el entorno de producción en Azure.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-141](https://linear.app/callejon19/issue/CAL-141) — SIT — Cobro y pagos |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT — Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT (System Integration Testing) |
| Módulo | Cobro, pagos divididos y liberación de mesas |
| Entorno | Microsoft Azure (Producción) |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | `1efb78c` (RestoHub standalone PWA) |
| Dispositivos de prueba | Terminal de cobro / PC de caja (Google Chrome 155, 64 bits) |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v1 — 2026-10-26 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Mercado Serrano Valeria | Asignada en Linear |
| Ejecutora de la prueba | Mercado Serrano Valeria | Verificación en interfaz web de caja y endpoints de checkout |
| Revisor | Octavio Duarte | **Distinto del ejecutor.** Sección 7 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Tarea (CAL-141) | 2026-10-21 | 2026-10-26 | 2026-10-26 22:30 | 2026-10-26 22:50 | Ejecución y consolidación de respuestas |

## 4. Alcance y Criterios Cubiertos

**Objetivo:** Verificar la integridad transaccional del cobro de órdenes en entorno cloud sobre PostgreSQL:
1. Liquidación de una orden con pago mixto (efectivo y tarjeta) con propina voluntaria, emisión de ticket inmutable y liberación atómica de la mesa asociada (`AVAILABLE`).
2. Rechazo de la solicitud de cobro con `HTTP 400 Bad Request` cuando los montos abonados no cubren el total requerido («Los pagos no cubren el total a pagar»).
3. Prevención de concurrencia y mutabilidad: rechazo de modificaciones o cobro doble sobre órdenes ya transicionadas a `PAID` mediante bloqueo transaccional (`findForCheckout`).

---

## 5. Casos de Prueba Ejecutados

### C01 — Cobro con Pago Dividido (Efectivo + Tarjeta), Propina y Liberación de Mesa
* **Propósito:** Validar que una orden con cuenta abierta se liquida registrando múltiples métodos de pago, propina y liberando la mesa.
* **Pasos:**
  1. Se tiene la orden `ord-00000001-0001-4000-8000-000000000010` (Mesa 3) en estado `DELIVERED` con total de consumo de `$350.00`.
  2. El cajero autenticado envía la solicitud a `POST /api/v1/orders/{id}/checkout` con propina voluntaria de `$35.00` (total a liquidar: `$385.00`), abonando `$200.00` en tarjeta y `$185.00` en efectivo (entregando billete de `$200.00`, cambio `$15.00`):
     ```json
     {
       "tipPercent": 10.0,
       "payments": [
         {
           "method": "CARD",
           "amount": 200.00
         },
         {
           "method": "CASH",
           "amount": 185.00,
           "receivedAmount": 200.00
         }
       ]
     }
     ```
  3. La API responde `201 Created` con el `TicketResponse` completo y folio generado.
  4. Se consulta el estado de la Mesa 3 (`GET /api/v1/tables`).
* **Resultado observado:**
  * Orden transicionada a `PAID`.
  * Ticket emitido con desglose de partidas, propina `$35.00`, total `$385.00` y cambio `$15.00`.
  * La Mesa 3 transiciona a `AVAILABLE` de forma atómica dentro de la misma transacción de base de datos.
* **Evidencia:** `respuestas/C01-checkout-pago-dividido.txt` y `respuestas/C01-mesa-liberada.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

### C02 — Rechazo por Cobertura Incompleta de Saldo
* **Propósito:** Comprobar que no se permite cerrar una orden si la suma de pagos es menor al total con propina.
* **Pasos:**
  1. Para una orden con total de `$500.00`, el cajero intenta enviar un pago parcial de `$400.00` en efectivo:
     ```json
     {
       "tipPercent": 0,
       "payments": [
         {
           "method": "CASH",
           "amount": 400.00
         }
       ]
     }
     ```
  2. La API evalúa la suma de pagos contra el total calculado.
* **Resultado observado:**
  * Respuesta `HTTP/1.1 400 Bad Request`.
  * Error RFC 7807 (`BusinessRuleException`): `"Los pagos no cubren el total a pagar."`.
  * La orden permanece intacta y la mesa continúa ocupada.
* **Evidencia:** `respuestas/C02-rechazo-pago-insuficiente.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

### C03 — Inmutabilidad de Orden Cobrada
* **Propósito:** Verificar que una orden ya cobrada (`PAID`) rechaza nuevos cobros o modificaciones.
* **Pasos:**
  1. Se reenvía la petición de cobro sobre la orden ya liquidada en C01 (`POST /api/v1/orders/{id}/checkout`).
* **Resultado observado:**
  * La API verifica el estado previo de la orden dentro de `findForCheckout`.
  * Respuesta `HTTP/1.1 400 Bad Request`.
  * Detalle de error: `"La orden ya esta PAID y no admite cobro"`.
* **Evidencia:** `respuestas/C03-rechazo-cobro-duplicado.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

## 6. Resumen de Resultados

| Caso | Descripción | Resultado | Defectos |
|---|---|:---:|---|
| **C01** | Cobro con pago dividido, propina voluntaria y liberación de mesa | ✅ **Aprobado** | Ninguno |
| **C02** | Rechazo por importe insuficiente (400 Bad Request) | ✅ **Aprobado** | Ninguno |
| **C03** | Inmutabilidad de orden cobrada frente a cobros duplicados (400 Bad Request) | ✅ **Aprobado** | Ninguno |

---

## 7. Revisión Independiente

- [x] Cada criterio del issue tiene al menos un caso con resultado
- [x] Las respuestas HTTP coinciden con los endpoints reales (`/api/v1/orders/{id}/checkout`)
- [x] El commit probado corresponde al registro de despliegue en Azure
- [x] No hay credenciales ni tokens sensibles expuestos

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| Octavio Duarte | 2026-10-26 | Aprobado | Consistencia transaccional de checkout y liberación de mesas verificada. |

## 8. Declaración de Autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y fecha indicados.

**Ejecutora:** Mercado Serrano Valeria — **Fecha:** 2026-10-26
