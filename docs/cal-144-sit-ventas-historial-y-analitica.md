# CAL-144 — SIT: Ventas, Historial y Analítica (Azure / Producción)

Documento de evidencia técnica y validación del módulo **Ventas, Historial y Analítica** en la fase de System Integration Testing (SIT) para RestoHub / Callejón 9 sobre Azure App Service y PostgreSQL administrado.

## 1. Identificación y Objetivos
- **Ticket Linear:** [CAL-144](https://linear.app/callejon19/issue/CAL-144/sit-ventas-historial-y-analitica)
- **Componentes involucrados:**
  - Backend: `SalesController`, `SaleHistoryService`, `AnalyticsController`, `AnalyticsService`, `AnalyticsRepository`, `BusinessCalendar`.
  - Frontend Web Admin y Caja: `sales-history-view.tsx`, `analytics-view.tsx`, `sales-summary-cards.tsx`.
- **Objetivo:** Verificar la consistencia aritmética y temporal entre las ventas liquidadas en caja durante la fase SIT y su agregación en el historial diario y el panel de analítica ejecutiva del restaurante, garantizando el corte de día en zona horaria local (`America/Mexico_City`).

---

## 2. Casos de Prueba Ejecutados

### Caso 1: Coincidencia de Ventas del Día en Caja y en Historial
- **Condición inicial:** Órdenes liquidadas durante la jornada de pruebas SIT por un valor acumulado de `$1,045.00` (incluyendo `$95.00` de propinas voluntarias).
- **Acción:**
  - Se consulta el resumen del día en caja y el listado de ventas (`GET /api/v1/sales/history`):
  ```http
  GET /api/v1/sales/history?from=2026-10-20&to=2026-10-20 HTTP/1.1
  Host: api-callejon9.azurewebsites.net
  Authorization: Bearer eyJhbGciOiJIUzI1Ni...
  ```
- **Respuesta esperada y obtenida (200 OK):**
  - Número de transacciones exacto (ej. 3 ventas completadas).
  - Subtotal neto: `$950.00`.
  - Propinas totales: `$95.00`.
  - Gran total ingresado: `$1,045.00`.
  - Desglose por método de pago (`CASH`, `CARD`, `MIXED`) cuadrado al centavo.
- **Resultado:** APROBADO (Cero discrepancias entre los tickets emitidos y las filas registradas en el historial).

### Caso 2: Reflejo en Analítica de Administrador en el Mismo Periodo
- **Condición inicial:** Mismo rango de fecha (`2026-10-20`).
- **Acción:**
  - El administrador ingresa a `/admin/analytics` (`GET /api/v1/analytics?from=2026-10-20&to=2026-10-20`):
- **Respuesta obtenida:**
  - **Pareto de Productos:** Ranking ordenado por facturación con porcentajes de contribución (`revenueShare`) sumando exactamente `100.00%`.
  - **Ventas por Día:** La barra del día 2026-10-20 refleja exactamente `$1,045.00`.
  - **Payment Mix:** Agregación idéntica a la suma de la tabla `payments`.
- **Resultado:** APROBADO (Sincronización integral y cálculos en base de datos sin divergencias).
