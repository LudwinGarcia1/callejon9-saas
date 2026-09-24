# Contrato API implementado

- `/api/v1/signup`
- `/api/v1/auth/login`, `/me`, `/logout`
- `/api/v1/platform/plans`
- `/api/v1/tenants/current/branding`
- `/api/v1/users`, `/tables`, `/categories`, `/products`
- `/api/v1/orders` y acciones `items`, `send-to-kitchen`, `cancel`, `checkout`
- `/api/v1/kitchen/orders`, `/kitchen/items/{itemId}/status`
- `/api/v1/tickets/{id}`, `/tickets/{id}/pdf`, búsqueda por `?folio=`
- `/api/v1/sales` y `/api/v1/analytics` con rangos opcionales

## Matriz de autorización

Sin sesión solo responden `POST /auth/login`, `POST /signup`, `/actuator/health` y OpenAPI (`/v3/api-docs/**`, `/swagger-ui/**`); todo lo demás da 401. Con sesión, cada controller declara su regla con `@PreAuthorize` y un rol ausente recibe 403. `AuthorizationMatrixTest` fija las lecturas.

| Ruta | Lectura | Escritura |
|---|---|---|
| `/auth/me`, `/auth/logout` | cualquier sesión | cualquier sesión |
| `/platform/**` | SUPER_ADMIN | SUPER_ADMIN |
| `/users` | ADMIN | ADMIN |
| `/tables` | ADMIN, WAITER, KITCHEN, CASHIER | ADMIN; estado: ADMIN, WAITER |
| `/categories`, `/products` | ADMIN, WAITER, KITCHEN, CASHIER | ADMIN |
| `/orders` | ADMIN, WAITER, CASHIER | ADMIN, WAITER; `checkout`: ADMIN, CASHIER |
| `/kitchen/**` | ADMIN, KITCHEN | ADMIN, KITCHEN |
| `/inventory/items` | ADMIN, KITCHEN | ADMIN |
| `/inventory/movements` | ADMIN, KITCHEN | ADMIN, KITCHEN |
| `/tickets/**`, `/sales` | ADMIN, CASHIER | — |
| `/analytics` | ADMIN | — |

La autorización por rol no sustituye a RLS: un rol permitido solo ve las filas de su tenant.

Las rutas exactas viven en annotations de `backend/src/main/java/**/web/*Controller.java`. El espejo cliente está en `frontend/src/lib/endpoints.ts`. Al cambiar un contrato actualiza ambos lados, DTOs/tipos, autorización, OpenAPI implícito y pruebas.
