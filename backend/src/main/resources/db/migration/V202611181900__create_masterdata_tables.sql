CREATE TABLE IF NOT EXISTS supplier (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    tax_code VARCHAR(100),
    address TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS tolerance_policy (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    supplier_id UUID REFERENCES supplier(id),
    item_category VARCHAR(100),
    min_amount NUMERIC(19, 4),
    max_amount NUMERIC(19, 4),
    qty_tolerance_pct NUMERIC(10, 4),
    price_tolerance_pct NUMERIC(10, 4),
    amount_tolerance_abs NUMERIC(19, 4),
    require_grn BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);