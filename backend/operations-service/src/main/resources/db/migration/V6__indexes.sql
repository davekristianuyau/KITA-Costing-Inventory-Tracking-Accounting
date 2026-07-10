-- Operations service — indexes on hot foreign-key/query paths (feature 003, polish).

CREATE INDEX idx_soline_item ON sales_order_line (item_id);
CREATE INDEX idx_reservation_item ON reservation (item_id);
CREATE INDEX idx_reservation_location ON reservation (location_id);
CREATE INDEX idx_receiptline_item ON receipt_line (item_id);
CREATE INDEX idx_bomcomp_component ON bom_component (component_item_id);
CREATE INDEX idx_movement_source ON stock_movement (source_type, source_id);
CREATE INDEX idx_lot_item ON lot (item_id);
