-- Goods receipts against a purchase order (feature 006, US3).
-- Each receipt posts to operations-service exactly once (FR-011): post_idempotency_key is unique and
-- posted_to_operations records that it landed, so a retry after a timeout cannot double-count stock.
-- This service never mutates inventory itself; operations-service owns on-hand and average cost.

CREATE TABLE goods_receipt (
    id                    UUID PRIMARY KEY,
    purchase_order_id     UUID NOT NULL REFERENCES purchase_order (id),
    received_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    received_by           TEXT,
    posted_to_operations  BOOLEAN NOT NULL DEFAULT FALSE,
    post_idempotency_key  TEXT NOT NULL UNIQUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_goods_receipt_po ON goods_receipt (purchase_order_id);

CREATE TABLE goods_receipt_line (
    id              UUID PRIMARY KEY,
    goods_receipt_id UUID NOT NULL REFERENCES goods_receipt (id) ON DELETE CASCADE,
    po_line_id      UUID NOT NULL REFERENCES purchase_order_line (id),
    item_ref        TEXT NOT NULL,
    qty_received    NUMERIC(19, 4) NOT NULL,
    unit_cost       NUMERIC(19, 2) NOT NULL,
    CHECK (qty_received > 0)
);

CREATE INDEX idx_goods_receipt_line ON goods_receipt_line (goods_receipt_id);
