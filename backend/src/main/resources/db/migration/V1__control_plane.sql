-- ============================================================
-- CONTROL PLANE: catalogo global del SaaS.
--
-- Estas tablas NO son tenant-scoped y NO llevan RLS: son
-- precisamente el registro de tenants y sus planes. El acceso se
-- restringe por rol SUPER_ADMIN en la capa de aplicacion.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE plans (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            varchar(40)   NOT NULL UNIQUE,
    name            varchar(120)  NOT NULL,
    price_monthly   numeric(10,2) NOT NULL DEFAULT 0,
    max_users       integer       NOT NULL,
    max_tables      integer       NOT NULL,
    features        jsonb         NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now()
);

CREATE TABLE tenants (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(160) NOT NULL,
    slug        varchar(80)  NOT NULL UNIQUE,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    plan_id              uuid NOT NULL REFERENCES plans(id),
    status               varchar(30) NOT NULL,
    started_at           timestamptz NOT NULL DEFAULT now(),
    current_period_end   timestamptz,
    canceled_at          timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT subscriptions_status_check
        CHECK (status IN ('ACTIVE', 'PAST_DUE', 'CANCELED', 'TRIALING'))
);

-- Un tenant no puede tener dos suscripciones vigentes a la vez.
CREATE UNIQUE INDEX subscriptions_one_active_per_tenant
    ON subscriptions (tenant_id) WHERE status IN ('ACTIVE', 'TRIALING');

INSERT INTO plans (code, name, price_monthly, max_users, max_tables, features) VALUES
  ('FREE',    'Gratis',      0.00,   3,  5,   '{"reports": false, "delivery": false}'),
  ('PRO',     'Profesional', 499.00, 15, 30,  '{"reports": true,  "delivery": false}'),
  ('PREMIUM', 'Premium',     999.00, 60, 120, '{"reports": true,  "delivery": true}');
