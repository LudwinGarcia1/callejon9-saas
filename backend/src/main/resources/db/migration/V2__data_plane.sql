-- ============================================================
-- PLANO DE DATOS: todas las tablas llevan tenant_id.
-- Las politicas RLS se agregan en V3.
--
-- Correcciones sobre el modelo Mongo original:
--   * restaurant_tables.number pasa a integer. En Mongo convivian
--     int y str, y Mesa.find_by_numero hacia $in: [int(n), str(n)]
--     para sobrevivirlo.
--   * orders referencia la mesa por FK. Antes apuntaba a
--     mesa_numero, sin integridad referencial.
--   * products unifica las colecciones productos y platillos.
-- ============================================================

CREATE TABLE users (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email          varchar(180) NOT NULL,
    password_hash  varchar(100) NOT NULL,
    full_name      varchar(160) NOT NULL,
    role           varchar(20)  NOT NULL,
    active         boolean      NOT NULL DEFAULT true,
    totp_secret    varchar(64),
    totp_enabled   boolean      NOT NULL DEFAULT false,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT users_email_unique_per_tenant UNIQUE (tenant_id, email),
    CONSTRAINT users_role_check
        CHECK (role IN ('SUPER_ADMIN','ADMIN','WAITER','KITCHEN','CASHIER'))
);

CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  varchar(100) NOT NULL UNIQUE,
    expires_at  timestamptz  NOT NULL,
    revoked_at  timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE restaurant_tables (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    number      integer     NOT NULL,
    capacity    integer     NOT NULL DEFAULT 4,
    status      varchar(20) NOT NULL DEFAULT 'FREE',
    waiter_id   uuid REFERENCES users(id) ON DELETE SET NULL,
    active      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT restaurant_tables_number_unique_per_tenant UNIQUE (tenant_id, number),
    CONSTRAINT restaurant_tables_status_check
        CHECK (status IN ('FREE','OCCUPIED','RESERVED','CLEANING'))
);

CREATE TABLE categories (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        varchar(120) NOT NULL,
    sort_order  integer      NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT categories_name_unique_per_tenant UNIQUE (tenant_id, name)
);

CREATE TABLE products (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    category_id  uuid REFERENCES categories(id) ON DELETE SET NULL,
    name         varchar(160)  NOT NULL,
    description  text,
    price        numeric(10,2) NOT NULL CHECK (price >= 0),
    active       boolean       NOT NULL DEFAULT true,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now()
);

CREATE TABLE customers (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    full_name   varchar(160) NOT NULL,
    phone       varchar(40),
    email       varchar(180),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    folio                varchar(40) NOT NULL,
    table_id             uuid REFERENCES restaurant_tables(id) ON DELETE SET NULL,
    waiter_id            uuid REFERENCES users(id) ON DELETE SET NULL,
    guest_count          integer       NOT NULL DEFAULT 1,
    status               varchar(20)   NOT NULL DEFAULT 'NEW',
    total                numeric(10,2) NOT NULL DEFAULT 0,
    opened_at            timestamptz   NOT NULL DEFAULT now(),
    sent_to_kitchen_at   timestamptz,
    closed_at            timestamptz,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT orders_folio_unique_per_tenant UNIQUE (tenant_id, folio),
    CONSTRAINT orders_status_check
        CHECK (status IN ('NEW','SENT','READY','PAID','CANCELED'))
);

CREATE TABLE order_items (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    order_id        uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      uuid REFERENCES products(id) ON DELETE SET NULL,
    product_name    varchar(160)  NOT NULL,
    unit_price      numeric(10,2) NOT NULL CHECK (unit_price >= 0),
    quantity        integer       NOT NULL CHECK (quantity > 0),
    kitchen_status  varchar(20)   NOT NULL DEFAULT 'PENDING',
    notes           text,
    ordered_at      timestamptz   NOT NULL DEFAULT now(),
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT order_items_kitchen_status_check
        CHECK (kitchen_status IN ('PENDING','IN_PREPARATION','READY','DELIVERED'))
);

CREATE TABLE sales (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    order_id        uuid NOT NULL REFERENCES orders(id),
    table_id        uuid REFERENCES restaurant_tables(id) ON DELETE SET NULL,
    cashier_id      uuid REFERENCES users(id) ON DELETE SET NULL,
    status          varchar(20)   NOT NULL DEFAULT 'PENDING',
    payment_method  varchar(20)   NOT NULL,
    subtotal        numeric(10,2) NOT NULL DEFAULT 0,
    tip             numeric(10,2) NOT NULL DEFAULT 0,
    total           numeric(10,2) NOT NULL DEFAULT 0,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT sales_status_check
        CHECK (status IN ('PENDING','COMPLETED','CANCELED')),
    CONSTRAINT sales_payment_method_check
        CHECK (payment_method IN ('CASH','CARD','TRANSFER','MIXED','MERCADOPAGO'))
);

CREATE TABLE payments (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sale_id               uuid NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    provider              varchar(40)   NOT NULL,
    provider_payment_id   varchar(120),
    method                varchar(20)   NOT NULL,
    amount                numeric(10,2) NOT NULL CHECK (amount >= 0),
    status                varchar(20)   NOT NULL,
    raw_response          jsonb,
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now()
);

-- items_snapshot es jsonb a proposito: el ticket es un documento inmutable.
-- Si el producto cambia de precio despues, el ticket emitido no debe cambiar.
CREATE TABLE tickets (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sale_id         uuid NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    order_id        uuid NOT NULL REFERENCES orders(id),
    folio           varchar(40)   NOT NULL,
    items_snapshot  jsonb         NOT NULL,
    subtotal        numeric(10,2) NOT NULL,
    tip             numeric(10,2) NOT NULL DEFAULT 0,
    tip_percent     numeric(5,2)  NOT NULL DEFAULT 0,
    total           numeric(10,2) NOT NULL,
    payment_method  varchar(20)   NOT NULL,
    closed_at       timestamptz   NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT tickets_folio_unique_per_tenant UNIQUE (tenant_id, folio)
);

CREATE TABLE inventory_items (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        varchar(160)  NOT NULL,
    unit        varchar(20)   NOT NULL,
    stock       numeric(12,3) NOT NULL DEFAULT 0,
    min_stock   numeric(12,3) NOT NULL DEFAULT 0,
    unit_cost   numeric(10,2) NOT NULL DEFAULT 0,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT inventory_items_name_unique_per_tenant UNIQUE (tenant_id, name)
);

CREATE TABLE inventory_movements (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    inventory_item_id   uuid NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    movement_type       varchar(20)   NOT NULL,
    quantity            numeric(12,3) NOT NULL,
    reason              varchar(200),
    user_id             uuid REFERENCES users(id) ON DELETE SET NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT inventory_movements_type_check
        CHECK (movement_type IN ('IN','OUT','ADJUSTMENT','WASTE'))
);

CREATE TABLE notifications (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     uuid REFERENCES users(id) ON DELETE CASCADE,
    title       varchar(160) NOT NULL,
    body        text,
    read_at     timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

-- Indices por tenant: toda consulta filtra por tenant_id, asi que va primero.
CREATE INDEX idx_users_tenant               ON users (tenant_id);
CREATE INDEX idx_refresh_tokens_tenant_user ON refresh_tokens (tenant_id, user_id);
CREATE INDEX idx_tables_tenant              ON restaurant_tables (tenant_id);
CREATE INDEX idx_categories_tenant          ON categories (tenant_id);
CREATE INDEX idx_products_tenant            ON products (tenant_id);
CREATE INDEX idx_customers_tenant           ON customers (tenant_id);
CREATE INDEX idx_orders_tenant_status       ON orders (tenant_id, status);
CREATE INDEX idx_order_items_tenant_order   ON order_items (tenant_id, order_id);
CREATE INDEX idx_sales_tenant_created       ON sales (tenant_id, created_at DESC);
CREATE INDEX idx_payments_tenant_sale       ON payments (tenant_id, sale_id);
CREATE INDEX idx_tickets_tenant_created     ON tickets (tenant_id, created_at DESC);
CREATE INDEX idx_inventory_items_tenant     ON inventory_items (tenant_id);
CREATE INDEX idx_inventory_mov_tenant_item  ON inventory_movements (tenant_id, inventory_item_id);
CREATE INDEX idx_notifications_tenant_user  ON notifications (tenant_id, user_id);
