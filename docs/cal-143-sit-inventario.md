# CAL-143 — SIT: Inventario y Movimientos de Stock (Azure / Producción)

Documento de evidencia técnica y validación del módulo **Inventario** en la fase de System Integration Testing (SIT) para RestoHub / Callejón 9 sobre Azure App Service y PostgreSQL.

## 1. Identificación y Objetivos
- **Ticket Linear:** [CAL-143](https://linear.app/callejon19/issue/CAL-143/sit-inventario)
- **Componentes involucrados:**
  - Backend: `InventoryItemController`, `InventoryMovementController`, `InventoryItemService`, `InventoryMovementService`.
  - Frontend Web Admin: `inventory-dashboard.tsx`, `inventory-movements-history.tsx`.
  - Frontend Cocina: `kitchen-supply-out.tsx`.
- **Objetivo:** Verificar la integridad de los saldos de inventario, el registro inmutable de movimientos (kardex) entre estaciones de trabajo (cocina/barra) y el panel administrativo, y la aplicación estricta de validaciones para insumos inactivos o dados de baja.

---

## 2. Casos de Prueba Ejecutados

### Caso 1: Salida de insumo registrada en Cocina y actualización en tiempo real en Panel de Administración
- **Condición inicial:** Insumo "Queso Mozzarella Barra" activo con stock actual de 10.0 kg.
- **Acción:**
  - El usuario de Cocina registra una merma o consumo operativo de 2.5 kg mediante `POST /api/v1/inventory/movements`:
  ```json
  {
    "itemId": "47a2291b-64de-4d7a-b9c1-5231c3bf4810",
    "type": "OUT",
    "quantity": 2.5,
    "reason": "Consumo preparación servicio nocturno"
  }
  ```
- **Respuesta esperada:** Status `201 Created` con detalle del movimiento registrado y saldo resultante `7.5 kg`.
- **Verificación en el Dashboard del Administrador:**
  - El administrador consulta la pantalla de inventario (`GET /api/v1/inventory/items`).
  - La existencia actual muestra de inmediato `7.50 kg`.
  - El historial de movimientos (`GET /api/v1/inventory/movements?itemId=...`) refleja el registro con usuario, fecha, tipo `OUT` y justificación.
- **Resultado:** APROBADO (Stock debitado exactamente sin discrepancias en saldos ni redondeos).

### Caso 2: Intento de movimiento sobre insumo dado de baja / inactivo
- **Condición inicial:** Insumo "Salsa BBQ Edición Especial" marcado como `active: false`.
- **Acción:**
  - Un usuario intenta enviar un movimiento de entrada o salida sobre el insumo inactivo:
  ```json
  {
    "itemId": "89ec9371-50e1-4567-9382-3bf9028bce55",
    "type": "IN",
    "quantity": 5.0,
    "reason": "Recepción de compra rezagada"
  }
  ```
- **Respuesta esperada:**
  - Status HTTP `400 Bad Request` con payload RFC 7807 indicando:
  ```json
  {
    "title": "Bad Request",
    "status": 400,
    "detail": "El insumo se encuentra inactivo y no admite movimientos de inventario"
  }
  ```
- **Resultado:** APROBADO (Operación rechazada limpiamente, preservando la inmutabilidad de los insumos descontinuados).
