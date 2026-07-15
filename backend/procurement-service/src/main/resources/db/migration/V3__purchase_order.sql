-- Purchase orders and their lines (feature 006, US2).
-- Lines lock at SENT (FR-007): the agreed price is fixed even if the supplier catalog moves on, so
-- what was ordered stays reconstructable. Money is exact decimal; order_total = sum(line_total).

CREATE TABLE purchase_order (
    id          UUID PRIMARY KEY,
    po_no       TEXT NOT NULL UNIQUE,
    supplier_id UUID NOT NULL REFERENCES supplier (id),
    status      TEXT NOT NULL,             -- DRAFT | APPROVED | SENT | PARTIALLY_RECEIVED |
                                           -- FULLY_RECEIVED | CLOSED | CANCELLED
    origin      TEXT NOT NULL,             -- MANUAL | RESTOCK
    order_total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    created_by  TEXT,
    approved_by TEXT,
    approved_at TIMESTAMPTZ,
    sent_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ
);

CREATE INDEX idx_po_supplier ON purchase_order (supplier_id, status);

CREATE TABLE purchase_order_line (
    id                UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_order (id) ON DELETE CASCADE,
    item_ref          TEXT NOT NULL,
    qty_ordered       NUMERIC(19, 4) NOT NULL,
    agreed_price      NUMERIC(19, 2) NOT NULL,
    qty_received      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    line_total        NUMERIC(19, 2) NOT NULL,
    CHECK (qty_ordered > 0),
    CHECK (qty_received >= 0),
    UNIQUE (purchase_order_id, item_ref)
);

CREATE INDEX idx_po_line_order ON purchase_order_line (purchase_order_id);
