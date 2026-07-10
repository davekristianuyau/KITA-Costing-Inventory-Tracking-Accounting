-- Operations service — bill-of-materials schema (feature 003, US3).

CREATE TABLE bill_of_materials (
    id               UUID PRIMARY KEY,
    parent_item_id   UUID NOT NULL REFERENCES item (id),
    type             TEXT NOT NULL,
    output_quantity  NUMERIC(38, 6) NOT NULL DEFAULT 1,
    active           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE bom_component (
    id                 UUID PRIMARY KEY,
    bom_id             UUID NOT NULL REFERENCES bill_of_materials (id),
    component_item_id  UUID NOT NULL REFERENCES item (id),
    quantity           NUMERIC(38, 6) NOT NULL,
    uom                TEXT NOT NULL
);

CREATE INDEX idx_bom_parent ON bill_of_materials (parent_item_id);
CREATE INDEX idx_bomcomp_bom ON bom_component (bom_id);
