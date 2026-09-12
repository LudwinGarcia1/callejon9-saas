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

Las rutas exactas viven en annotations de `backend/src/main/java/**/web/*Controller.java`. El espejo cliente está en `frontend/src/lib/endpoints.ts`. Al cambiar un contrato actualiza ambos lados, DTOs/tipos, autorización, OpenAPI implícito y pruebas.
