-- Restock / reorder suggestions (feature 006, US4).
-- Low-stock signals come from operations-service; suggestions are sized to the target level, rounded
-- up to the supplier's min order qty, and consolidated per preferred supplier. Converting one raises
-- a DRAFT purchase order -- auto-submit is opt-in per item and off by default (FR-014).

CREATE TABLE restock_suggestion (
    id               UUID PRIMARY KEY,
    supplier_id      UUID NOT NULL REFERENCES supplier (id),
    status           TEXT NOT NULL,          -- OPEN | CONVERTED | DISMISSED
    generated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    converted_po_id  UUID REFERENCES purchase_order (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_restock_supplier ON restock_suggestion (supplier_id, status);

CREATE TABLE restock_suggestion_line (
    id             UUID PRIMARY KEY,
    suggestion_id  UUID NOT NULL REFERENCES restock_suggestion (id) ON DELETE CASCADE,
    item_ref       TEXT NOT NULL,
    suggested_qty  NUMERIC(19, 4) NOT NULL,
    on_hand        NUMERIC(19, 4) NOT NULL,
    target_level   NUMERIC(19, 4) NOT NULL,
    reason         TEXT,
    CHECK (suggested_qty > 0)
);

CREATE INDEX idx_restock_line ON restock_suggestion_line (suggestion_id);

-- Per-item opt-in for turning a suggestion straight into a submitted PO. Default off (FR-014).
ALTER TABLE supplier_item ADD COLUMN auto_submit BOOLEAN NOT NULL DEFAULT FALSE;
