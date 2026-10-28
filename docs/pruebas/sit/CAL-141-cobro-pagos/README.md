# Evidencia de prueba · CAL-141 — SIT · Cobro y pagos

> **v2.** Re-ejecución real contra Azure (2026-10-28) con respuestas HTTP literales de `curl.exe -i`. Cobro con pago dividido y propina, rechazo con HTTP 422 ante montos insuficientes (`InvalidPaymentException`) y rechazo con HTTP 409 al reintentar cobro de orden pagada. Pendiente de revisión independiente (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-141](https://linear.app/callejon19/issue/CAL-141) — SIT · Cobro y pagos |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Cobro y pagos (`CheckoutController`, `CheckoutService`, `PaymentAllocation`, `CashSessionService`) |
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
| Responsable del issue | Mercado Serrano Valeria | Asignado en Linear |
| Ejecutor de la prueba | García Gaytán Ludwin | Re-ejecución técnica con script automatizado `scripts/revalidar-sit-azure.ps1` |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia padre (CAL-118) | 2026-10-20 | 2026-10-28 | 2026-10-20 | | |
| Esta tarea (CAL-141) | 2026-10-21 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:45 | Re-ejecución tras observaciones de revisión independiente |
| Preparación del entorno | 2026-10-28 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:33 | Apertura de turno de caja ($500.00 fondo) |
| Ejecución de casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:44 | 2026-10-28 14:45 | Captura de respuestas literales en Azure |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que caja cobra órdenes con pago dividido y propina emitiendo ticket y liberando la mesa; que los pagos que no cubren el total se rechazan con HTTP 422 manteniendo la orden abierta; y que una orden ya cobrada rechaza cobros duplicados con HTTP 409.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Caja cobra orden con pago dividido (efectivo y tarjeta) y propina: cierra y libera mesa | C01 |
| 2. Pagos que no cubren el total: 422 y orden sigue abierta | C02 |
| 3. Reintento de cobro en orden ya pagada: rechazo 409 | C03 |

## 5. Preparación

**Precondiciones del entorno**
- [x] Backend encendido y `/actuator/health` en `UP`
- [x] Turno de caja abierto vía `POST /api/v1/cash-sessions` con fondo de $500.00
- [x] Orden con 2 productos (subtotal $90.00) enviada a cocina

## 6. Casos de prueba

### C01 — Checkout exitoso con pago dividido y propina

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `POST /api/v1/orders/{id}/checkout` con $50 efectivo + $49 tarjeta (10% propina) | 201 Created con Ticket | HTTP 201 Created, ticket emitido con total $99.00 y folio asignado | [`C01-checkout-pago-dividido.txt`](respuestas/C01-checkout-pago-dividido.txt) |
| 2 | `GET /api/v1/tables` con Cajero | Mesa liberada a estado FREE | HTTP 200 OK, `{"status":"FREE"}` | [`C01-mesa-liberada.txt`](respuestas/C01-mesa-liberada.txt) |

### C02 — Rechazo transaccional ante pagos insuficientes (422)

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `POST /api/v1/orders/{id}/checkout` enviando $50.00 (cuenta es de $90.00) | 422 Unprocessable Entity (`InvalidPaymentException`) | HTTP 422, ProblemDetail `Los pagos no cubren el total de la cuenta.` | [`C02-rechazo-pago-insuficiente.txt`](respuestas/C02-rechazo-pago-insuficiente.txt) |

### C03 — Rechazo de cobro duplicado sobre orden pagada (409)

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `POST /api/v1/orders/{id}/checkout` sobre orden ya cobrada | 409 Conflict (`BusinessRuleException`) | HTTP 409 Conflict, ProblemDetail `La orden ORD-... ya esta PAID y no admite cobro.` | [`C03-rechazo-cobro-duplicado.txt`](respuestas/C03-rechazo-cobro-duplicado.txt) |

## 7. Defectos encontrados

Ninguno. La lógica de negocio en `PaymentAllocation.java:33` emite `InvalidPaymentException` que `GlobalExceptionHandler.java:31` traduce a `HTTP 422 Unprocessable Entity`.

## 8. Resumen de ejecución y desviaciones

Las respuestas capturadas en Azure demuestran la precisión del módulo de cobro y liquidación: cálculo de propinas, desglose de métodos y protección contra desajustes de saldo.

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
