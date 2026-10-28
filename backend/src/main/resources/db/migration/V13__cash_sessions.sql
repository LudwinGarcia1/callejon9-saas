-- Caja por cajero. La sesión y sus movimientos quedan bajo RLS y las llaves
-- compuestas impiden asociar usuarios, ventas o sesiones de otro restaurante.
ALTER TABLE users
    ADD CONSTRAINT users_tenant_id_id_unique UNIQUE (tenant_id, id);

CREATE TABLE cash_sessions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    cashier_id      uuid NOT NULL,
    opening_fund    numeric(10,2) NOT NULL CHECK (opening_fund >= 0),
    opened_at       timestamptz NOT NULL DEFAULT now(),
    closed_at       timestamptz,
    closed_by       uuid,
    expected_cash   numeric(10,2),
    counted_cash    numeric(10,2),
    difference      numeric(10,2),
    closure_reason  varchar(250),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT cash_sessions_tenant_id_id_unique UNIQUE (tenant_id, id),
    CONSTRAINT cash_sessions_tenant_id_id_cashier_unique UNIQUE (tenant_id, id, cashier_id),
    CONSTRAINT cash_sessions_cashier_same_tenant
        FOREIGN KEY (tenant_id, cashier_id) REFERENCES users (tenant_id, id),
    CONSTRAINT cash_sessions_closed_by_same_tenant
        FOREIGN KEY (tenant_id, closed_by) REFERENCES users (tenant_id, id),
    CONSTRAINT cash_sessions_closure_values_check CHECK (
        (closed_at IS NULL AND closed_by IS NULL AND expected_cash IS NULL
            AND counted_cash IS NULL AND difference IS NULL)
        OR
        (closed_at IS NOT NULL AND closed_by IS NOT NULL AND expected_cash IS NOT NULL
            AND counted_cash IS NOT NULL AND difference IS NOT NULL
            AND difference = counted_cash - expected_cash)
    )
);

CREATE UNIQUE INDEX cash_sessions_one_open_per_cashier
    ON cash_sessions (tenant_id, cashier_id) WHERE closed_at IS NULL;
CREATE INDEX cash_sessions_recent_by_cashier
    ON cash_sessions (tenant_id, cashier_id, opened_at DESC);

CREATE TABLE cash_movements (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    cash_session_id uuid NOT NULL,
    movement_type   varchar(20) NOT NULL CHECK (movement_type IN ('DEPOSIT','WITHDRAWAL')),
    amount          numeric(10,2) NOT NULL CHECK (amount > 0),
    reason          varchar(250) NOT NULL CHECK (length(btrim(reason)) > 0),
    recorded_by     uuid NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT cash_movements_session_same_tenant
        FOREIGN KEY (tenant_id, cash_session_id) REFERENCES cash_sessions (tenant_id, id)
            ON DELETE CASCADE,
    CONSTRAINT cash_movements_recorder_same_tenant
        FOREIGN KEY (tenant_id, recorded_by) REFERENCES users (tenant_id, id)
);
CREATE INDEX cash_movements_session_recent
    ON cash_movements (tenant_id, cash_session_id, created_at DESC);

ALTER TABLE cash_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE cash_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON cash_sessions
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE cash_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE cash_movements FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON cash_movements
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
