ALTER TABLE sales ADD COLUMN cash_session_id uuid;
ALTER TABLE sales ADD CONSTRAINT sales_cash_session_owner_same_tenant
    FOREIGN KEY (tenant_id, cash_session_id, cashier_id)
    REFERENCES cash_sessions (tenant_id, id, cashier_id);
CREATE INDEX sales_cash_session_id ON sales (tenant_id, cash_session_id)
    WHERE cash_session_id IS NOT NULL;

-- Una sesión cerrada conserva su arqueo: no se permite modificarla después
-- de su primer cierre. La transición abierta -> cerrada sigue permitida.
CREATE FUNCTION prevent_closed_cash_session_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.closed_at IS NOT NULL THEN
        RAISE EXCEPTION 'closed cash sessions are immutable'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER cash_sessions_closed_immutable
    BEFORE UPDATE ON cash_sessions
    FOR EACH ROW EXECUTE FUNCTION prevent_closed_cash_session_update();
