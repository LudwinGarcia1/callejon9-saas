# Mapa de producto y código

| Capacidad | Backend | Frontend | Contrato principal |
|---|---|---|---|
| Alta de restaurante y planes | `platform/tenant`, `platform/plan` | `/signup`, `/platform` | `/api/v1/signup`, `/api/v1/platform/plans` |
| Sesión y autorización | `auth`, `tenancy` | `/login`, layout autenticado | `/api/v1/auth/*` |
| Identidad del restaurante | `platform/tenant` | `/admin` | `/api/v1/tenants/current/branding` |
| Personal | `user` | `/admin` | `/api/v1/users` |
| Mesas | `table` | `/admin`, `/waiter` | `/api/v1/tables` |
| Catálogo | `catalog` | `/admin`, selector de producto | `/api/v1/categories`, `/api/v1/products` |
| Comandas | `order` | `/waiter`, `/waiter/order/[id]` | `/api/v1/orders` |
| Cocina en tiempo real | `kitchen`, `realtime` | `/kitchen` | `/api/v1/kitchen/*`, STOMP |
| Cobro y tickets | `sale`, `ticket` | `/cashier` | checkout, `/api/v1/tickets` |
| Historial y analítica | `sale`, `analytics` | `/history`, `/analytics` | `/api/v1/sales`, `/api/v1/analytics` |

La lista de endpoints del cliente se centraliza en `frontend/src/lib/endpoints.ts`; los controllers del backend son la fuente implementada. Si divergen, corrige contrato, pruebas y documentación en la misma entrega.
