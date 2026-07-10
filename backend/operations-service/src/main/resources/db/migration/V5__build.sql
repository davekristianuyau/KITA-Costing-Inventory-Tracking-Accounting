-- Operations service — production build (feature 003, US7).

CREATE TABLE build (
    id                UUID PRIMARY KEY,
    finished_item_id  UUID NOT NULL REFERENCES item (id),
    location_id       UUID NOT NULL REFERENCES stock_location (id),
    quantity          NUMERIC(38, 6) NOT NULL,
    status            TEXT NOT NULL,
    produced_lot_id   UUID REFERENCES lot (id)
);
