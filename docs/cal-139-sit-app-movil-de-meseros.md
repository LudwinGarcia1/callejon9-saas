# CAL-139 — SIT: App Móvil de Meseros (Azure / Producción)

Documento de evidencia técnica y validación del módulo **App Móvil de Meseros** en la fase de System Integration Testing (SIT) para RestoHub / Callejón 9 comunicándose contra la API de Azure App Service por HTTPS.

## 1. Identificación y Objetivos
- **Ticket Linear:** [CAL-139](https://linear.app/callejon19/issue/CAL-139/sit-app-movil-de-meseros)
- **Componentes involucrados:**
  - App Móvil: Expo / React Native (`mobile/src/api/client.ts`, `TablesScreen.tsx`, `OrderScreen.tsx`, `LoginScreen.tsx`).
  - Backend: `AuthController`, `TableController`, `CatalogController`, `OrderController`.
- **Objetivo:** Verificar la operatividad remota de la terminal móvil de meseros conectándose contra el backend productivo en Azure vía canal seguro HTTPS, asegurando la persistencia de sesión por cookies nativas, sincronización de catálogo/mesas y manejo elegante ante desconexión de red.

---

## 2. Casos de Prueba Ejecutados

### Caso 1: Inicio de Sesión y Almacenamiento Seguro de Cookie de Sesión
- **Condición inicial:** App móvil iniciada en dispositivo físico / simulador apuntando a `https://api-callejon9.azurewebsites.net`.
- **Acción:**
  - El mesero ingresa credenciales `mesero@pruebas.com` y contraseña autorizada.
- **Respuesta esperada:**
  - Petición `POST /api/v1/auth/login` con credenciales `include`.
  - El backend responde `200 OK` con cabecera `Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Lax`.
  - El almacén nativo del teléfono (OkHttp / NSHTTPCookieStorage) retiene la cookie y la añade automáticamente a peticiones subsiguientes sin exponer el token a la capa JS.
- **Resultado:** APROBADO (Autenticación fluida y transición automática a `TablesScreen`).

### Caso 2: Consulta Consistente de Salón y Catálogo
- **Acción:**
  - La pantalla principal de la app carga el listado de mesas (`GET /api/v1/tables`) y el catálogo de platillos activos (`GET /api/v1/catalog/menu`).
- **Verificación:**
  - Las mesas libres, reservadas y ocupadas coinciden al 100% con los datos mostrados simultáneamente en el panel web de meseros.
- **Resultado:** APROBADO (Paridad absoluta de datos sin discrepancias de caché).

### Caso 3: Envío de Comanda en Movilidad y Recepción en Cocina Web
- **Acción:**
  - El mesero abre la Mesa 8, añade 2 platillos y presiona "Mandar a Cocina" (`POST /api/v1/orders/{orderId}/send-to-kitchen`).
- **Verificación:**
  - La app móvil recibe confirmación con folio `ORD-2026-M08` y total correspondiente.
  - La pantalla KDS de la cocina web recibe el pedido por WebSocket STOMP al instante sin necesidad de recarga.
- **Resultado:** APROBADO (Interoperabilidad móvil-web verificada en producción).

### Caso 4: Comportamiento ante Pérdida de Conectividad (Modo Offline/Retry)
- **Acción:**
  - Se interrumpe la conexión de red (Modo Avión) y se intenta enviar una acción operativa.
- **Respuesta:**
  - El cliente HTTP móvil detecta el fallo tras timeout o desconexión (`kind: network`), presentando en la interfaz un mensaje claro: *"No fue posible conectar con el servidor. Revisa tu conexión Wi-Fi"* junto a un botón de "Reintentar".
  - Al restablecer la red y presionar "Reintentar", la comanda se despacha con éxito sin duplicaciones ni pérdida de datos.
- **Resultado:** APROBADO (Resiliencia y tolerancia a fallas de conectividad verificada).
