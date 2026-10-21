# CAL-141 — SIT: Cobro, Pagos Divididos y Liberación de Mesas (Azure / Producción)

Documento de evidencia técnica y validación del módulo **Cobro y Pagos** en la fase de System Integration Testing (SIT) para RestoHub / Callejón 9 sobre Azure App Service y PostgreSQL administrado.

## 1. Identificación y Objetivos
- **Ticket Linear:** [CAL-141](https://linear.app/callejon19/issue/CAL-141/sit-cobro-y-pagos)
- **Componentes involucrados:**
  - Backend: `CheckoutController`, `CheckoutService`, `PaymentAllocation`, `SaleRepository`, `TableService`.
  - Frontend Web Caja: `cashier-checkout.tsx`, `split-payments.tsx`.
- **Objetivo:** Verificar la integridad transaccional del cobro de órdenes en entorno productivo/cloud, cubriendo pagos mixtos (efectivo y tarjeta) con propinas voluntarias, liberación atómica de la mesa asociada (`FREE`), rechazo por cobertura incompleta de saldo y protección de la orden frente a modificaciones concurrentes durante la liquidación.

---

## 2. Casos de Prueba Ejecutados

### Caso 1: Cobro con Pago Dividido (Efectivo + Tarjeta), Propina y Liberación de Mesa
- **Condición inicial:** Orden en estado `READY` asignada a la Mesa 3. Subtotal de consumo: `$350.00`.
- **Acción:**
  - El cajero envía la solicitud de cobro especificando una propina del 10% (`$35.00`) para un total de `$385.00`, cubierto mediante `$200.00` con tarjeta y `$200.00` en efectivo (cambio: `$15.00`):
  ```http
  POST /api/v1/orders/3d4f82a1-55bb-419b-b18e-440055112233/checkout HTTP/1.1
  Host: api-callejon9.azurewebsites.net
  Authorization: Bearer eyJhbGciOiJIUzI1Ni... (Rol: CASHIER)
  Content-Type: application/json

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
- **Respuesta esperada y obtenida (201 Created):**
  - Venta registrada con estado `COMPLETED` y método de pago `MIXED`.
  - Dos registros de pago creados en la tabla `payments`.
  - Ticket emitido con folio inmutable `TCK-2026-0104`, total `$385.00`, propina `$35.00` y cambio `$15.00`.
  - Orden transicionada atómicamente a `PAID`.
  - Mesa 3 actualizada a estado `FREE` en el mapa de mesas.
- **Resultado:** APROBADO (Transacción ACID cerrada completamente sin discrepancias en saldos ni bloqueos huérfanos).

### Caso 2: Pagos que no Cubren el Total Requerido (422 Unprocessable Entity)
- **Condición inicial:** Orden abierta por un total a pagar de `$500.00`.
- **Acción:**
  - El cliente solo entrega un abono de `$400.00` y el cajero intenta enviar la petición:
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
- **Respuesta esperada y obtenida:**
  - Status HTTP `422 Unprocessable Entity` con detalle RFC 7807 indicando monto insuficiente para liquidar el importe.
  - La orden permanece en su estado anterior (`READY`) y la mesa sigue `OCCUPIED`.
- **Resultado:** APROBADO (Garantía contra faltantes de caja y cierres truncados).

### Caso 3: Intento de Adición de Productos por Mesero Durante el Cobro Concurrente
- **Condición inicial:** Orden bloqueada en base de datos mediante consulta `findForCheckout` (`PESSIMISTIC_WRITE` / `FOR UPDATE`).
- **Acción:**
  - Un mesero intenta enviar un `POST /api/v1/orders/{id}/items` justo al momento en que la caja ejecuta el cobro.
- **Respuesta:**
  - El bloqueo transaccional de base de datos retiene o rechaza la adición hasta concluir el checkout; tras la liquidación, la orden ya se encuentra en `PAID`, rechazando cualquier nuevo ítem con `400 Bad Request` ("La orden ya esta PAID y no admite cobro/modificaciones").
- **Resultado:** APROBADO (Inmutabilidad de la cuenta cerrada verificada en PostgreSQL).
