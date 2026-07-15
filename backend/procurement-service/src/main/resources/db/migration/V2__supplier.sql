-- Supplier master, the items each supplier can supply, and append-only change history
-- (feature 006, US1). procurement-service is the supplier Party master that operations-service
-- validates against. item_ref points at an operations-service item; stock lives there, not here.

CREATE TABLE supplier (
    id             UUID PRIMARY KEY,
    supplier_code  TEXT NOT NULL UNIQUE,
    name           TEXT NOT NULL,
    email          TEXT,
    phone          TEXT,
    address        TEXT,
    payment_terms  TEXT,
    delivery_terms TEXT,
    status         TEXT NOT NULL,            -- ACTIVE | INACTIVE
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);

CREATE TABLE supplier_item (
    id             UUID PRIMARY KEY,
    supplier_id    UUID NOT NULL REFERENCES supplier (id),
    item_ref       TEXT NOT NULL,            -- operations-service item id
    supplier_price NUMERIC(19, 2) NOT NULL,
    lead_time_days INT,
    min_order_qty  NUMERIC(19, 4),
    preferred      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    UNIQUE (supplier_id, item_ref)
);

CREATE INDEX idx_supplier_item_ref ON supplier_item (item_ref);
-- At most one preferred source per item, so restock has an unambiguous default supplier.
CREATE UNIQUE INDEX idx_supplier_item_preferred ON supplier_item (item_ref) WHERE preferred;

-- FR-003: no destructive overwrite; price changes in particular are retained.
CREATE TABLE supplier_change_history (
    id          UUID PRIMARY KEY,
    supplier_id UUID NOT NULL REFERENCES supplier (id),
    item_ref    TEXT,                        -- set when the change was to a supplied item
    field       TEXT NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    actor       TEXT,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_supplier_history ON supplier_change_history (supplier_id, changed_at);
