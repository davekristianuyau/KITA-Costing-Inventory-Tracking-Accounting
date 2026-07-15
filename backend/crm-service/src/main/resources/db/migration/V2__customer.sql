-- Customer master, append-only attribute history, and government-mandated entitlements
-- (feature 005, US1). crm-service is the customer Party master referenced by operations-service.

CREATE TABLE customer (
    id             UUID PRIMARY KEY,
    customer_code  TEXT NOT NULL UNIQUE,
    type           TEXT NOT NULL,            -- INDIVIDUAL | BUSINESS
    name           TEXT NOT NULL,
    email          TEXT,
    phone          TEXT,
    address        TEXT,
    status         TEXT NOT NULL,            -- ACTIVE | INACTIVE
    loyalty_tier_id UUID,                    -- FK added in V4 once loyalty_tier exists
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);

-- Append-only record of attribute changes (FR-003: no destructive overwrite).
CREATE TABLE customer_attribute_history (
    id          UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customer (id),
    field       TEXT NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    actor       TEXT,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_attr_history ON customer_attribute_history (customer_id, changed_at);

-- A statutory discount applies only with a valid entitlement AND its supporting ID ref (FR-014).
-- supporting_id_ref is stored but never logged or returned in the clear.
CREATE TABLE entitlement (
    id                UUID PRIMARY KEY,
    customer_id       UUID NOT NULL REFERENCES customer (id),
    kind              TEXT NOT NULL,         -- SENIOR | PWD | ...
    supporting_id_ref TEXT,
    valid_from        DATE NOT NULL,
    valid_to          DATE,                  -- NULL = open-ended
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX idx_entitlement_customer ON entitlement (customer_id, kind);
