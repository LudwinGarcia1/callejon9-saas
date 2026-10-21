# CAL-142 — SIT: Tickets y Descarga de Comprobante PDF (Azure / Producción)

Documento de evidencia técnica y validación del módulo **Tickets** en la fase de System Integration Testing (SIT) para RestoHub / Callejón 9 sobre Azure App Service y PostgreSQL administrado.

## 1. Identificación y Objetivos
- **Ticket Linear:** [CAL-142](https://linear.app/callejon19/issue/CAL-142/sit-tickets)
- **Componentes involucrados:**
  - Backend: `TicketController`, `TicketService`, `TicketPdfGenerator`, `TicketRepository`.
  - Frontend: `cashier-checkout.tsx`, visor de comprobantes PDF.
- **Objetivo:** Verificar la generación fidedigna del archivo binario PDF (`application/pdf`) de un ticket emitido tras el cobro de una orden (incluyendo folio único, desglose de partidas, propina voluntaria, formas de pago y cambio entregado), y comprobar el aislamiento estricto por inquilino impidiendo la búsqueda o descarga de comprobantes ajenos (código HTTP 404).

---

## 2. Casos de Prueba Ejecutados

### Caso 1: Descarga del Ticket en Formato PDF desde Caja
- **Condición inicial:** Venta finalizada y cobrada en el restaurante. Folio emitido: `TCK-2026-0091`.
- **Acción:**
  - El cajero o el sistema solicita la descarga del comprobante PDF asociado mediante `GET /api/v1/tickets/{ticketId}/pdf`:
  ```http
  GET /api/v1/tickets/8c6e2894-1a3b-48c1-8842-7cf28994bb01/pdf HTTP/1.1
  Host: api-callejon9.azurewebsites.net
  Authorization: Bearer eyJhbGciOiJIUzI1NiIsIn...
  X-Tenant-ID: 11111111-1111-1111-1111-111111111111
  ```
- **Respuesta obtenida:**
  ```http
  HTTP/1.1 200 OK
  Content-Type: application/pdf
  Content-Disposition: inline; filename="ticket-TCK-2026-0091.pdf"
  Transfer-Encoding: chunked

  %PDF-1.4 ... [Contenido binario renderizable]
  ```
- **Verificación de Contenido del Documento:**
  - Encabezado con marca del restaurante ("Restaurante Callejón 9").
  - Identificador y Folio: `TCK-2026-0091`.
  - Desglose de partidas (`TicketItemSnapshot`): cantidad, precio unitario, importe por línea.
  - Subtotal, desglose de propina voluntaria y total general.
  - Snapshot de pagos: soporte para pago en efectivo, tarjeta o mixto, mostrando efectivo entregado y cambio devuelto.
- **Resultado:** APROBADO (Documento PDF inmutable generado dinámicamente y compatible con impresoras térmicas de 80mm y visores web estándar).

### Caso 2: Intento de Búsqueda por Folio de Otro Restaurante (Aislamiento RLS)
- **Condición inicial:** El folio `TCK-2026-9999` pertenece a un restaurante diferente (`tenant_id: 22222222-2222-2222-2222-222222222222`).
- **Acción:**
  - Un usuario autenticado en el restaurante `11111111-1111-1111-1111-111111111111` intenta consultar o descargar dicho folio mediante `GET /api/v1/tickets/by-folio/TCK-2026-9999`:
  ```http
  GET /api/v1/tickets/by-folio/TCK-2026-9999 HTTP/1.1
  Host: api-callejon9.azurewebsites.net
  Authorization: Bearer eyJhbGciOi... (Tenant Callejón 9 Matriz)
  ```
- **Respuesta obtenida:**
  ```http
  HTTP/1.1 404 Not Found
  Content-Type: application/problem+json

  {
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "El ticket con folio TCK-2026-9999 no existe.",
    "instance": "/api/v1/tickets/by-folio/TCK-2026-9999"
  }
  ```
- **Verificación de Seguridad:** La consulta SQL en PostgreSQL `SELECT * FROM tickets WHERE folio = ?` es filtrada de forma transparente por la política Row Level Security (RLS) del esquema de base de datos. Al no existir en el contexto del tenant activo, se produce una excepción `ResourceNotFoundException` controlada que retorna 404 sin revelar la existencia previa del folio en otra sucursal.
- **Resultado:** APROBADO (Aislamiento absoluto e indemnidad de datos entre organizaciones).
