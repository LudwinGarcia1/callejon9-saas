# Modelo de datos

Plano de control (`V1__control_plane.sql`): `plans`, `tenants`, `subscriptions`.

Plano por restaurante (`V2__data_plane.sql`): `users`, `refresh_tokens`, `restaurant_tables`, `categories`, `products`, `customers`, `orders`, `order_items`, `sales`, `payments`, `tickets`, `inventory_items`, `inventory_movements`, `notifications`.

RLS aplica a 14 tablas tenant-scoped mediante `V3`, `V4` y la corrección null-safe de `V5`. `V6` crea el superadministrador de plataforma y `V7` agrega la baja lógica de insumos. Antes de agregar una tabla, decide explícitamente si pertenece al control plane o al data plane. Si es tenant-scoped debe incluir `tenant_id NOT NULL`, FKs/índices apropiados, RLS forzada, políticas de lectura/escritura y pruebas cruzadas.

## Privilegios de `callejon9_app`

`V4` concede DML sobre todas las tablas y `V9` lo reduce al mínimo que usa el código en el control plane:

| Tabla | Privilegios | Motivo |
|---|---|---|
| 14 tablas por restaurante | `SELECT`, `INSERT`, `UPDATE`, `DELETE` | El aislamiento lo impone la política RLS |
| `tenants` | `SELECT`, `INSERT`, `UPDATE`, `DELETE` | Login, signup, bloqueo `SELECT … FOR UPDATE` (exige `UPDATE`) y compensación del onboarding |
| `subscriptions` | `SELECT`, `INSERT` | Límites del plan y signup; el borrado en cascada lo ejecuta PostgreSQL como dueño |
| `plans` | `SELECT` | Catálogo de solo lectura para la aplicación |
| `flyway_schema_history` | ninguno | Solo lo escribe `callejon9_owner` |

`DatabaseSecurityContractTest` lee el catálogo de PostgreSQL y exige esta matriz, RLS habilitada y forzada con `USING` y `WITH CHECK` sobre `app.tenant_id`, y que el rol de runtime no sea dueño, superusuario ni `BYPASSRLS`. `ALTER DEFAULT PRIVILEGES` sigue concediendo DML a las tablas nuevas, así que toda tabla nueva hace fallar la prueba hasta que se clasifica ahí; si es de control plane, su migración debe revocar lo que el código no use.

Las bajas de productos, mesas y usuarios son lógicas porque existe histórico referenciado.
