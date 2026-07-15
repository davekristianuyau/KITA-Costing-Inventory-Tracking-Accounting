-- Effective-dated discount rules and the stacking policy (feature 005, US2).
-- Rules are DATA, not code: a computation uses the version effective for the sale date, so
-- re-versioning a rule never retroactively changes an already-quoted price.

CREATE TABLE discount_rule (
    id             UUID PRIMARY KEY,
    code           TEXT NOT NULL,
    origin         TEXT NOT NULL,            -- STATUTORY | PROMOTIONAL | LOYALTY
    computation    TEXT NOT NULL,            -- PERCENT | FIXED
    value          NUMERIC(12, 6) NOT NULL,  -- PERCENT: fraction (0.25 = 25%); FIXED: amount
    vat_treatment  TEXT NOT NULL DEFAULT 'NONE',
    priority       INT NOT NULL DEFAULT 100, -- ascending order within the cascade
    effective_date DATE NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (code, effective_date)
);

CREATE INDEX idx_discount_rule_effective ON discount_rule (effective_date);

-- Single active row; the newest is authoritative.
CREATE TABLE stacking_policy (
    id         UUID PRIMARY KEY,
    mode       TEXT NOT NULL,                -- MOST_FAVORABLE | STATUTORY_THEN_PROMO | ...
    updated_by TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Default per FR-013: the customer gets the more favorable of statutory vs. promotional.
INSERT INTO stacking_policy (id, mode, updated_by)
VALUES ('00000000-0000-0000-0000-000000000001', 'MOST_FAVORABLE', 'seed');
