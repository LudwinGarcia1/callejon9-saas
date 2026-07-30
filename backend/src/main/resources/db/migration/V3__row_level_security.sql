-- ============================================================
-- ROW LEVEL SECURITY
--
-- Cada tabla del plano de datos queda gobernada por la variable de
-- sesion app.tenant_id, que la aplicacion fija por transaccion con
-- set_config('app.tenant_id', <uuid>, true).
--
-- USING      -> filtra SELECT, UPDATE y DELETE
-- WITH CHECK -> impide INSERT o UPDATE hacia otro tenant
-- Ambas son necesarias: sin WITH CHECK un tenant puede escribir
-- dentro de otro aunque no pueda leerlo.
--
-- FORCE ROW LEVEL SECURITY es indispensable: sin el, el DUENO de la
-- tabla ignora las politicas. Como Flyway crea las tablas siendo
-- callejon9_owner, ese rol las evadiria por completo.
--
-- current_setting('app.tenant_id', true) usa missing_ok = true, asi
-- que devuelve NULL en lugar de lanzar error cuando la variable no
-- esta fijada. Con NULL la comparacion es NULL, que RLS trata como
-- falso: SIN TENANT ACTIVO NO SE VE NINGUNA FILA. Ese fail-closed es
-- deliberado y replica el contrato de NoTenantContextError del
-- sistema Flask original.
-- ============================================================

DO $$
DECLARE
    t text;
    tenant_tables text[] := ARRAY[
        'users', 'refresh_tokens', 'restaurant_tables', 'categories',
        'products', 'customers', 'orders', 'order_items', 'sales',
        'payments', 'tickets', 'inventory_items', 'inventory_movements',
        'notifications'
    ];
BEGIN
    FOREACH t IN ARRAY tenant_tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE  ROW LEVEL SECURITY', t);
        EXECUTE format($f$
            CREATE POLICY tenant_isolation ON %I
                USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
                WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $f$, t);
    END LOOP;
END $$;
