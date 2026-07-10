-- Operations service — goods receipts (feature 003, US4).

CREATE TABLE goods_receipt (
    id           UUID PRIMARY KEY,
    supplier_ref TEXT NOT NULL,
    location_id  UUID NOT NULL REFERENCES stock_location (id),
    received_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE receipt_line (
    id          UUID PRIMARY KEY,
    receipt_id  UUID NOT NULL REFERENCES goods_receipt (id),
    item_id     UUID NOT NULL REFERENCES item (id),
    lot_id      UUID REFERENCES lot (id),
    quantity    NUMERIC(38, 6) NOT NULL,
    unit_cost   NUMERIC(38, 6) NOT NULL
);

CREATE INDEX idx_receiptline_receipt ON receipt_line (receipt_id);
