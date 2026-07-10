-- Operations service — sales schema (feature 003, US2).
-- Quantities/money are NUMERIC (exact). Quantities are stored in each item's base UoM.

CREATE TABLE sales_order (
    id            UUID PRIMARY KEY,
    customer_ref  TEXT NOT NULL,
    status        TEXT NOT NULL,
    ordered_at    TIMESTAMPTZ NOT NULL,
    confirmed_at  TIMESTAMPTZ,
    fulfilled_at  TIMESTAMPTZ
);

CREATE TABLE sales_order_line (
    id             UUID PRIMARY KEY,
    order_id       UUID NOT NULL REFERENCES sales_order (id),
    item_id        UUID NOT NULL REFERENCES item (id),
    quantity       NUMERIC(38, 6) NOT NULL,
    unit_price     NUMERIC(38, 4) NOT NULL,
    reserved_qty   NUMERIC(38, 6) NOT NULL DEFAULT 0,
    fulfilled_qty  NUMERIC(38, 6) NOT NULL DEFAULT 0
);

CREATE TABLE reservation (
    id             UUID PRIMARY KEY,
    order_line_id  UUID NOT NULL REFERENCES sales_order_line (id),
    item_id        UUID NOT NULL REFERENCES item (id),
    location_id    UUID NOT NULL REFERENCES stock_location (id),
    lot_id         UUID REFERENCES lot (id),
    quantity       NUMERIC(38, 6) NOT NULL
);

CREATE INDEX idx_soline_order ON sales_order_line (order_id);
CREATE INDEX idx_reservation_line ON reservation (order_line_id);
