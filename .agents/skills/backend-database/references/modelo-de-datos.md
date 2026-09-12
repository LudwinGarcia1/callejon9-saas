# Modelo de datos

Plano de control (`V1__control_plane.sql`): `plans`, `tenants`, `subscriptions`.

Plano por restaurante (`V2__data_plane.sql`): `users`, `refresh_tokens`, `restaurant_tables`, `categories`, `products`, `customers`, `orders`, `order_items`, `sales`, `payments`, `tickets`, `inventory_items`, `inventory_movements`, `notifications`.

RLS aplica a 14 tablas tenant-scoped mediante `V3`, `V4` y la corrección null-safe de `V5`. `V6` crea el superadministrador de plataforma y `V7` incorpora branding. Antes de agregar una tabla, decide explícitamente si pertenece al control plane o al data plane. Si es tenant-scoped debe incluir `tenant_id NOT NULL`, FKs/índices apropiados, RLS forzada, políticas de lectura/escritura y pruebas cruzadas.

Las bajas de productos, mesas y usuarios son lógicas porque existe histórico referenciado.
