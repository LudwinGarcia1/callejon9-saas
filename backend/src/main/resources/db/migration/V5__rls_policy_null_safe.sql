-- ============================================================
-- RLS null-safe: current_setting('app.tenant_id', true) puede
-- devolver cadena vacia, no solo NULL.
--
-- Postgres trata "app.tenant_id" como un GUC personalizado tipo
-- "placeholder". La primera vez que una transaccion en una conexion
-- fisica lo fija con set_config(..., true) (LOCAL a la transaccion),
-- Postgres crea ese placeholder en la sesion. Cuando la transaccion
-- termina (commit o rollback), el valor LOCAL se revierte, pero NO
-- vuelve a "sin fijar" (NULL): vuelve a la cadena vacia ('') con la
-- que se inicializo el placeholder al crearse. Solo una conexion
-- fisica que JAMAS toco la variable devuelve NULL.
--
-- Con un pool de conexiones (HikariCP) esto es inevitable: en cuanto
-- CUALQUIER transaccion fija un tenant real en una conexion, cualquier
-- transaccion posterior en esa misma conexion fisica que no fije la
-- variable hereda '' en vez de NULL. Y ''::uuid lanza una excepcion de
-- Postgres en lugar de simplemente no revelar filas, sin importar que
-- codigo Java abrio esa transaccion.
--
-- nullif(current_setting('app.tenant_id', true), '') convierte esa
-- cadena vacia en NULL antes del cast, de modo que el motor -no la
-- aplicacion- garantiza el fail-closed: cualquier consulta que llegue
-- a estas tablas sin haber fijado un tenant valido para ESTA
-- transaccion ve cero filas, sin excepcion, sin importar que capa de
-- Java (o ausencia de ella) abrio la conexion.
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
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format($f$
            CREATE POLICY tenant_isolation ON %I
                USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
                WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
        $f$, t);
    END LOOP;
END $$;
