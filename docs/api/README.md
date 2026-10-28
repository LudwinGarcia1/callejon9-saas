# API de Callejón 9 para el frontend

Guía para quien construye o despliega un frontend contra el backend. El contrato completo, con cada cuerpo y respuesta, está en [`openapi.json`](openapi.json) (OpenAPI 3). Se puede abrir en <https://editor.swagger.io> o importar en Postman o Insomnia.

`openapi.json` se exportó de `/v3/api-docs` con el backend en local el 2026-10-25. Si cambia un controller, hay que volver a exportarlo.

Para explorar y probar la API en el navegador, usa Swagger UI:

- **Local:** `http://localhost:8080/swagger-ui.html`
- **Azure:** `https://app-callejon9-api-prod-3878c6.azurewebsites.net/swagger-ui.html`. Solo responde con el backend encendido y la variable de Terraform `swagger_enabled = true` (ver `infra/terraform/azure-backend/README.md`).

Ahí "Try it out" funciona sin pegar tokens: ejecuta `POST /api/v1/auth/login` y las demás rutas usan las cookies que deja.

## Dónde está el backend

| Entorno | Origen | Nota |
|---|---|---|
| Local | `http://localhost:8080` | Arranque en el `README.md` raíz. Swagger UI en `http://localhost:8080/swagger-ui.html` |
| Azure (producción) | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` | Base de datos real. Puede estar apagado para ahorrar crédito: ver `infra/terraform/azure-backend/README.md` |

## Cómo se conecta el frontend

El navegador **nunca** llama al origen del backend. Llama rutas relativas `/api/v1/*` en el mismo dominio del frontend, y el servidor del frontend las reenvía al backend. En este repositorio lo hacen los rewrites de `frontend/next.config.ts` con la variable `BACKEND_ORIGIN`:

```powershell
cd frontend
$env:BACKEND_ORIGIN = "https://app-callejon9-api-prod-3878c6.azurewebsites.net"
pnpm dev
```

Para publicar el frontend hace falta una plataforma que ejecute el servidor de Next.js (Vercel, Netlify o Render), con `BACKEND_ORIGIN` definido al compilar. Un hosting estático no sirve, porque no puede reenviar las peticiones. Después hay que poner el dominio público del frontend en `frontend_origin` de Terraform (ver el README de Azure).

## Sesión

- **El login no devuelve un token.** `POST /api/v1/auth/login` responde con el usuario y deja dos cookies `httpOnly`, `Secure` y `SameSite=Strict`: `access_token` (15 minutos) y `refresh_token`. El navegador las envía solo. No hay header `Authorization: Bearer`, y el token no se guarda en `localStorage` ni en JavaScript.
- **Cuando una petición responde `401`,** se llama una sola vez a `POST /api/v1/auth/refresh`. Si responde `204`, se repite la petición original; si responde `401`, la sesión terminó y hay que volver al login.
- **`GET /api/v1/auth/me`** devuelve el usuario, su rol y su restaurante. Sirve para restaurar la sesión al recargar la página.
- **El restaurante lo decide el backend.** Sale de la cookie firmada; ninguna ruta acepta `tenant_id` del cliente.
- **Un login incorrecto** responde `401` sin cuerpo: no indica si falló el restaurante, el correo o la contraseña.

## Errores

Los errores siguen RFC 7807 (`application/problem+json`): `title`, `status` y `detail`. En las validaciones, la propiedad `errors` trae el detalle por campo. Los más comunes:

| Código | Cuándo |
|---|---|
| `400` | Cuerpo inválido o campos que no pasan la validación |
| `422` | Pago inválido al cobrar |
| `401` | Sin sesión o sesión vencida |
| `403` | El rol no tiene permiso |
| `404` | No existe en este restaurante |
| `409` | Conflicto de estado o duplicado |
| `429` | Demasiados intentos; trae `Retry-After` en segundos |

`openapi.json` solo describe las respuestas exitosas. En dos rutas difiere del comportamiento real: `refresh` y `logout` responden `204`, aunque el archivo dice `200`.

## Endpoints

Todas las rutas cuelgan de `/api/v1`. Los roles son `ADMIN`, `WAITER` (mesero), `CASHIER` (caja), `KITCHEN` (cocina) y `SUPER_ADMIN` (plataforma). "Pública" significa sin sesión. La columna de roles sale de `backend/src/test/resources/security/authorization-contract.json`, el contrato que verifican las pruebas.

### Alta y sesión

| Método | Ruta | Cuerpo | Respuesta | Roles |
|---|---|---|---|---|
| POST | `/signup` | `SignupRequest` | `201 SignupResponse` | Pública. 5 altas por hora por IP |
| POST | `/auth/login` | `LoginRequest` | `200 LoginResponse` + cookies | Pública |
| POST | `/auth/refresh` | — | `204` + cookies nuevas | Pública (usa la cookie `refresh_token`) |
| GET | `/auth/me` | — | `200 MeResponse` | Cualquier sesión |
| POST | `/auth/logout` | — | `204`, borra las cookies | Cualquier sesión |

Ejemplos de cuerpo:

```json
// POST /api/v1/signup. planCode: FREE, PRO o PREMIUM.
// slug: minúsculas, números y guiones (3 a 80). password: mínimo 10 caracteres.
{ "restaurantName": "Tacos Don Pepe", "slug": "tacos-don-pepe", "adminEmail": "admin@ejemplo.com",
  "adminFullName": "Pepe López", "password": "<contraseña>", "planCode": "FREE" }

// POST /api/v1/auth/login
{ "slug": "tacos-don-pepe", "email": "admin@ejemplo.com", "password": "<contraseña>" }
```

### Mesas

| Método | Ruta | Cuerpo / parámetros | Respuesta | Roles |
|---|---|---|---|---|
| GET | `/tables` | `?includeInactive` | `200 TableResponse[]` | ADMIN, WAITER, CASHIER, KITCHEN |
| POST | `/tables` | `CreateTableRequest` | `201 TableResponse` | ADMIN |
| PUT | `/tables/{id}` | `UpdateTableRequest` (número, capacidad) | `200 TableResponse` | ADMIN |
| PATCH | `/tables/{id}` | `UpdateTableActiveRequest` (alta o baja) | `200 TableResponse` | ADMIN |
| POST | `/tables/{id}/status` | `UpdateTableStatusRequest` | `200 TableResponse` | ADMIN, WAITER |

### Catálogo

| Método | Ruta | Cuerpo / parámetros | Respuesta | Roles |
|---|---|---|---|---|
| GET | `/categories` | — | `200 CategoryResponse[]` | ADMIN, WAITER, CASHIER, KITCHEN |
| POST | `/categories` | `CreateCategoryRequest` | `201 CategoryResponse` | ADMIN |
| PUT | `/categories/{id}` | `UpdateCategoryRequest` | `200 CategoryResponse` | ADMIN |
| GET | `/products` | `?categoryId&includeInactive` | `200 ProductResponse[]` | ADMIN, WAITER, CASHIER, KITCHEN |
| POST | `/products` | `CreateProductRequest` | `201 ProductResponse` | ADMIN |
| PUT | `/products/{id}` | `UpdateProductRequest` | `200 ProductResponse` | ADMIN |
| PATCH | `/products/{id}` | `UpdateProductStatusRequest` (alta o baja) | `200 ProductResponse` | ADMIN |

No hay `GET` por id para mesas, categorías ni productos: se lee la colección y se filtra en el cliente.

### Órdenes y cobro

| Método | Ruta | Cuerpo / parámetros | Respuesta | Roles |
|---|---|---|---|---|
| GET | `/orders` | `?status` | `200 OrderSummaryResponse[]` | ADMIN, WAITER, CASHIER |
| POST | `/orders` | `OpenOrderRequest` | `201 OrderResponse` | ADMIN, WAITER |
| GET | `/orders/{id}` | — | `200 OrderResponse` | ADMIN, WAITER, CASHIER |
| POST | `/orders/{id}/items` | `AddOrderItemsRequest` | `200 OrderResponse` | ADMIN, WAITER |
| POST | `/orders/{id}/send-to-kitchen` | — | `200 OrderResponse` | ADMIN, WAITER |
| POST | `/orders/{id}/cancel` | — | `200 OrderResponse` | ADMIN, WAITER |
| POST | `/orders/{id}/checkout` | `CheckoutRequest` | `201 TicketResponse` | ADMIN, CASHIER |

El checkout requiere un turno abierto para el usuario autenticado. Sin turno responde `422`; la venta queda asociada al turno al que pertenece el cajero.

### Turnos de caja

| Método | Ruta | Cuerpo / parámetros | Respuesta | Roles |
|---|---|---|---|---|
| GET | `/cash-sessions` | — | `200 CashSessionListResponse` | ADMIN (restaurante), CASHIER (propias) |
| GET | `/cash-sessions/{id}` | — | `200 CashSessionResponse` | ADMIN, dueño CASHIER |
| POST | `/cash-sessions` | `OpenCashSessionRequest` | `201 CashSessionResponse` | ADMIN, CASHIER |
| POST | `/cash-sessions/{id}/close` | `CloseCashSessionRequest` | `200 CashSessionResponse` | dueño ADMIN, CASHIER |
| POST | `/cash-sessions/{id}/force-close` | `CloseCashSessionRequest` con motivo | `200 CashSessionResponse` | ADMIN |
| POST | `/cash-sessions/{id}/movements` | `CashMovementRequest` con motivo obligatorio | `201 CashSessionResponse` | ADMIN, CASHIER dueño |

El resumen de efectivo suma el fondo inicial, el efectivo recibido menos el cambio, los depósitos y resta los retiros. Los pagos con tarjeta y transferencia aparecen en el resumen por método, pero no aumentan el cajón.

### Cocina

| Método | Ruta | Cuerpo | Respuesta | Roles |
|---|---|---|---|---|
| GET | `/kitchen/orders` | — | `200 KitchenOrderResponse[]` | ADMIN, KITCHEN |
| POST | `/kitchen/items/{itemId}/status` | `UpdateKitchenItemStatusRequest` | `200 KitchenItemResponse` | ADMIN, KITCHEN |

### Tickets, ventas y reportes

| Método | Ruta | Parámetros | Respuesta | Roles |
|---|---|---|---|---|
| GET | `/tickets/{id}` | — | `200 TicketResponse` | ADMIN, CASHIER |
| GET | `/tickets` | `?folio` (obligatorio) | `200 TicketResponse` | ADMIN, CASHIER |
| GET | `/tickets/{id}/pdf` | — | `200 application/pdf` | ADMIN, CASHIER |
| GET | `/sales` | `?from&to` | `200 SalesHistoryResponse` | ADMIN, CASHIER |
| GET | `/analytics` | `?from&to` | `200 AnalyticsResponse` | ADMIN |

### Inventario

| Método | Ruta | Cuerpo / parámetros | Respuesta | Roles |
|---|---|---|---|---|
| GET | `/inventory/items` | `?includeInactive` | `200 InventoryItemResponse[]` | ADMIN, KITCHEN |
| POST | `/inventory/items` | `CreateInventoryItemRequest` | `201 InventoryItemResponse` | ADMIN |
| PUT | `/inventory/items/{id}` | `UpdateInventoryItemRequest` | `200 InventoryItemResponse` | ADMIN |
| PATCH | `/inventory/items/{id}` | `UpdateInventoryItemStatusRequest` (alta o baja) | `200 InventoryItemResponse` | ADMIN |
| GET | `/inventory/movements` | `?from&to&itemId` | `200 InventoryMovementRow[]` | ADMIN, KITCHEN |
| POST | `/inventory/movements` | `RegisterMovementRequest` | `201 RegisteredMovementResponse` | ADMIN, KITCHEN |

### Usuarios y plataforma

| Método | Ruta | Cuerpo | Respuesta | Roles |
|---|---|---|---|---|
| GET | `/users` | — | `200 UserResponse[]` | ADMIN |
| POST | `/users` | `CreateUserRequest` | `201 UserResponse` | ADMIN |
| PATCH | `/users/{id}` | `UpdateUserStatusRequest` (alta o baja) | `200 UserResponse` | ADMIN |
| PATCH | `/users/{id}/role` | `UpdateUserRoleRequest` | `200 UserResponse` | ADMIN |
| PATCH | `/users/{id}/password` | `UpdateUserPasswordRequest` | `204` | ADMIN |
| GET | `/platform/plans` | — | `200 PlanView[]` | SUPER_ADMIN |

Los campos de cada `…Request` y `…Response` están en `components.schemas` de `openapi.json`. En el frontend existente, las rutas están en `frontend/src/lib/endpoints.ts` y los tipos, en `frontend/src/lib`.

Cambiar el rol o la contraseña revoca las sesiones existentes del usuario; debe iniciar sesión de nuevo. El backend valida la contraseña contra el nombre, correo y restaurante guardados. Un restaurante no puede asignar `SUPER_ADMIN` ni quitar el rol al último administrador activo.

## Para probar

- **Con el backend local:** crea un restaurante con `/signup`, o llena la base con un restaurante de ejemplo, su personal, su carta y comandas usando `.\scripts\seed-demo.ps1`.
- **Contra Azure:** es la base de producción real. Cada quien crea su propio restaurante de prueba con `/signup`, y la cuenta del super-administrador no se comparte.
