-- Operations service — catalog + inventory schema (feature 003, US1).
-- Money/quantity columns are NUMERIC (exact). IDs are UUID.

CREATE TABLE unit_of_measure (
    id      UUID PRIMARY KEY,
    code    TEXT NOT NULL UNIQUE,
    family  TEXT NOT NULL
);

CREATE TABLE uom_conversion (
    id           UUID PRIMARY KEY,
    from_uom_id  UUID NOT NULL REFERENCES unit_of_measure (id),
    to_uom_id    UUID NOT NULL REFERENCES unit_of_measure (id),
    factor       NUMERIC(38, 12) NOT NULL
);

CREATE TABLE item (
    id                UUID PRIMARY KEY,
    sku               TEXT NOT NULL UNIQUE,
    name              TEXT NOT NULL,
    type              TEXT NOT NULL,
    base_uom_id       UUID NOT NULL REFERENCES unit_of_measure (id),
    valuation_method  TEXT NOT NULL,
    perishable        BOOLEAN NOT NULL DEFAULT FALSE,
    standard_cost     NUMERIC(38, 6),
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE stock_location (
    id      UUID PRIMARY KEY,
    code    TEXT NOT NULL UNIQUE,
    name    TEXT NOT NULL,
    active  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE lot (
    id           UUID PRIMARY KEY,
    item_id      UUID NOT NULL REFERENCES item (id),
    lot_code     TEXT NOT NULL,
    expiry_date  DATE,
    unit_cost    NUMERIC(38, 6),
    CONSTRAINT uq_lot_item_code UNIQUE (item_id, lot_code)
);

CREATE TABLE stock_level (
    id           UUID PRIMARY KEY,
    item_id      UUID NOT NULL REFERENCES item (id),
    location_id  UUID NOT NULL REFERENCES stock_location (id),
    lot_id       UUID REFERENCES lot (id),
    on_hand      NUMERIC(38, 6) NOT NULL DEFAULT 0,
    reserved     NUMERIC(38, 6) NOT NULL DEFAULT 0,
    CONSTRAINT uq_stock_level UNIQUE (item_id, location_id, lot_id)
);

CREATE TABLE stock_movement (
    id           UUID PRIMARY KEY,
    item_id      UUID NOT NULL REFERENCES item (id),
    location_id  UUID NOT NULL REFERENCES stock_location (id),
    lot_id       UUID REFERENCES lot (id),
    type         TEXT NOT NULL,
    quantity     NUMERIC(38, 6) NOT NULL,
    unit_cost    NUMERIC(38, 6) NOT NULL DEFAULT 0,
    reason       TEXT,
    source_type  TEXT,
    source_id    TEXT,
    occurred_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_movement_item_time ON stock_movement (item_id, occurred_at);
CREATE INDEX idx_stock_level_item ON stock_level (item_id);
