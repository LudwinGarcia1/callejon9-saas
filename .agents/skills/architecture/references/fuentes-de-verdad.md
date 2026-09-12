# Fuentes de verdad

- Esquema y políticas: migraciones Flyway SQL; las entidades JPA validan, no crean el esquema (`ddl-auto: validate`).
- Tenant activo: claims de la cookie JWT validados por backend → `TenantContext` → `TenantAwareTransactionManager` → `app.tenant_id` transaccional.
- Contrato HTTP implementado: annotations y DTOs de controllers; OpenAPI lo expone.
- Contrato consumido: `frontend/src/lib/endpoints.ts` y `types.ts`. Debe coincidir con backend, pero no lo reemplaza como autoridad.
- Caché de UI: `frontend/src/lib/query-keys.ts`.
- Identidad visual: persistencia de branding en backend y aplicación mediante `frontend/src/lib/tenant-theme.ts`; tokens base en `globals.css`.
- Estado de producto: comportamiento probado/código primero; `README.md` y `workflow/product/` lo resumen.
