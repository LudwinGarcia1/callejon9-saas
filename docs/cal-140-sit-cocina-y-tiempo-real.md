# CAL-140 — SIT: Cocina, KDS y Notificaciones en Tiempo Real (Azure / Producción)

Documento de evidencia técnica y validación del módulo **Cocina y Tiempo Real** en la fase de System Integration Testing (SIT) para RestoHub / Callejón 9 sobre Azure App Service y mensajería WebSocket / STOMP.

## 1. Identificación y Objetivos
- **Ticket Linear:** [CAL-140](https://linear.app/callejon19/issue/CAL-140/sit-cocina-y-tiempo-real)
- **Componentes involucrados:**
  - Backend: `KitchenController`, `KitchenService`, WebSocket Broker (`/ws`), `KitchenItemStatusChangedEvent`, `TenantSubscriptionInterceptor`.
  - Frontend: `kitchen-view.tsx`, `kitchen-live.tsx`, cliente STOMP.
- **Objetivo:** Verificar la reactividad y propagación en tiempo real de pedidos enviados a cocina y actualizaciones de estado de partidas entre el mesero y el tablero KDS (Kitchen Display System), validando la compatibilidad de orígenes autorizados de WebSocket sobre Azure (`https://restohub.azurewebsites.net`).

---

## 2. Casos de Prueba Ejecutados

### Caso 1: Envío de Pedido por Mesero y Visualización Inmediata en KDS sin Recarga
- **Condición inicial:** Tablero de Cocina conectado y suscrito al topic de tenant `/topic/tenant.{tenantId}.kitchen`.
- **Acción:**
  - El mesero envía una orden con dos hamburguesas y bebidas a cocina (`POST /api/v1/orders/{orderId}/send-to-kitchen`).
- **Respuesta en Tiempo Real:**
  - El backend publica el evento transaccional en el broker STOMP.
  - La pantalla del cocinero (KDS) añade automáticamente la comanda al tablero con animación de entrada, clasificándola bajo el semáforo como "recién enviada" (`normal`, &lt; 15 min).
- **Resultado:** APROBADO (Llegada del mensaje en &lt; 150 ms sin requerir refresco manual del navegador).

### Caso 2: Transición de Estado de Partida en Cocina y Reflejo en Terminal del Mesero
- **Condición inicial:** Ítem en estado `PENDING` ("En espera").
- **Acción:**
  - El cocinero presiona "Empezar preparación", enviando `POST /api/v1/kitchen/items/{itemId}/status` con `IN_PREPARATION`.
  - Posteriormente marca "Listo", enviando `READY`.
- **Verificación:**
  - Ambos eventos emiten `KitchenItemStatusChangedEvent`.
  - La terminal móvil y web del mesero reciben la actualización vía WebSocket y cambian el color del badge del producto a "Listo para servir".
  - Al completar el último producto, la orden matriz pasa automáticamente a `READY`.
- **Resultado:** APROBADO (Consistencia de estados y propagación bidireccional inmediata).

### Caso 3: Apertura de Conexión WebSocket desde el Dominio Frontend Desplegado
- **Condición inicial:** Frontend publicado en `https://restohub.azurewebsites.net`.
- **Acción:**
  - Establecimiento de handshake WebSocket / SockJS apuntando a `wss://api-callejon9.azurewebsites.net/ws`.
- **Respuesta de Conexión:**
  - Handshake HTTP 101 Switching Protocols exitoso con cabeceras `Upgrade: websocket`.
  - Conexión STOMP `CONNECTED` autorizada con token JWT y canal de tenant validado.
  - Orígenes no autorizados son rechazados con 403 Forbidden por `WebSocketOriginTest`.
- **Resultado:** APROBADO (Handshake WebSocket seguro y funcional en producción).
