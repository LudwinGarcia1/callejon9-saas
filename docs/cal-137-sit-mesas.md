# CAL-137 — SIT: Mesas y Control de Concurrencia (Azure / Producción)

Documento de evidencia técnica y validación del módulo **Mesas** en la fase de System Integration Testing (SIT) para RestoHub / Callejón 9 sobre Azure App Service y PostgreSQL.

## 1. Identificación y Objetivos
- **Ticket Linear:** [CAL-137](https://linear.app/callejon19/issue/CAL-137/sit-mesas)
- **Componentes involucrados:**
  - Backend: `TableController`, `TableService`, `OrderService`, `TableRepository`.
  - Frontend Web Salón / Meseros: `tables-grid.tsx`, `table-card.tsx`.
  - Frontend App Móvil: `mobile-table-list.tsx`.
- **Objetivo:** Verificar la consistencia de estados del mapa de mesas (`FREE`, `OCCUPIED`), la sincronización en tiempo real/reactiva entre clientes heterogéneos y el control estricto de concurrencia evitando sobreasignación simultánea de una misma mesa.

---

## 2. Casos de Prueba Ejecutados

### Caso 1: Apertura de orden y transición inmediata a OCCUPIED
- **Condición inicial:** Mesa 4 en estado `FREE`.
- **Acción:**
  - El mesero A abre una orden en Mesa 4 mediante `POST /api/v1/orders`:
  ```json
  {
    "tableId": "b18e38d4-5396-419b-a3d5-0f8623b32e18",
    "dinersCount": 2
  }
  ```
- **Respuesta esperada:** Status `201 Created`, orden generada con estado `OPEN` y la mesa 4 marcada como `status: OCCUPIED`.
- **Verificación cruzada (Web & Móvil):**
  - La interfaz web del panel de salón muestra la tarjeta de Mesa 4 en color distintivo de "Ocupada" con el folio de la comanda activa.
  - La aplicación móvil de meseros actualiza el estado de Mesa 4 impidiendo reasignación normal.
- **Resultado:** APROBADO (Transición atómica de estado persistida en base de datos PostgreSQL en Azure).

### Caso 2: Cierre o cancelación de orden y liberación automática de la mesa
- **Condición inicial:** Mesa 4 ocupada con orden abierta.
- **Acción A (Cobro):**
  - El cajero cobra y cierra la orden mediante `POST /api/v1/orders/{orderId}/pay`.
- **Acción B (Cancelación alternativa):**
  - O bien el supervisor cancela la orden mediante `POST /api/v1/orders/{orderId}/cancel`.
- **Respuesta esperada:** Estado de la orden finalizado (`CLOSED` o `CANCELLED`) y evento/actualización de la entidad `Table` regresando a `FREE`.
- **Verificación:**
  - Ambas terminales (web y móvil) reflejan la mesa como libre y apta para alojar nuevos comensales.
- **Resultado:** APROBADO (Mesa 4 liberada sin residuos transaccionales ni bloqueos huérfanos).

### Caso 3: Control de concurrencia y prevención de doble apertura (409 Conflict)
- **Condición inicial:** Mesa 4 ya se encuentra ocupada por Mesero A.
- **Acción:**
  - Mesero B, casi simultáneamente o con pantalla desactualizada, intenta abrir una nueva comanda en la misma Mesa 4:
  ```json
  {
    "tableId": "b18e38d4-5396-419b-a3d5-0f8623b32e18",
    "dinersCount": 4
  }
  ```
- **Respuesta esperada:**
  - Status HTTP `409 Conflict` (o `400 Bad Request` bajo especificación RFC 7807) con mensaje `"La mesa ya se encuentra ocupada por otra orden activa"`.
  - No se genera comanda duplicada.
- **Resultado:** APROBADO (Integridad y consistencia concurrentes garantizadas).
