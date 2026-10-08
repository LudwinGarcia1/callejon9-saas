-- Se conservan ENABLE/FORCE y las políticas USING/WITH CHECK existentes.
ALTER TABLE payments ADD COLUMN received_amount numeric(10,2);
ALTER TABLE tickets ADD COLUMN payments_snapshot jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE tickets ADD COLUMN change numeric(10,2) NOT NULL DEFAULT 0 CHECK (change >= 0);

-- FORCE RLS también rige al propietario: backfill dentro de cada tenant.
DO $$
DECLARE tenant_record record;
BEGIN
    FOR tenant_record IN SELECT id FROM tenants LOOP
        PERFORM set_config('app.tenant_id', tenant_record.id::text, true);
        UPDATE payments SET received_amount = amount;
        INSERT INTO payments (tenant_id, sale_id, provider, method, amount, received_amount, status)
        SELECT s.tenant_id, s.id, 'LEGACY', s.payment_method, s.total, s.total, 'COMPLETED'
        FROM sales s WHERE s.status = 'COMPLETED'
          AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.sale_id = s.id);
        UPDATE tickets t SET payments_snapshot = (
            SELECT coalesce(jsonb_agg(jsonb_build_object('method', p.method, 'amount', p.amount,
                'receivedAmount', p.received_amount, 'change', 0) ORDER BY p.created_at, p.id), '[]'::jsonb)
            FROM payments p WHERE p.sale_id = t.sale_id AND p.status = 'COMPLETED');
    END LOOP;
    PERFORM set_config('app.tenant_id', '', true);
END $$;
ALTER TABLE payments ALTER COLUMN received_amount SET NOT NULL;
ALTER TABLE payments ADD CONSTRAINT payments_received_check CHECK (
    received_amount >= amount AND (method = 'CASH' OR received_amount = amount));
ALTER TABLE sales ADD CONSTRAINT sales_tenant_id_unique UNIQUE (tenant_id, id);
ALTER TABLE payments ADD CONSTRAINT payments_sale_same_tenant
    FOREIGN KEY (tenant_id, sale_id) REFERENCES sales (tenant_id, id) ON DELETE CASCADE;
