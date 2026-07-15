-- Loyalty / repeat-customer tiers (feature 005, US3). A tier carries its qualifying criteria and
-- the discount rule it contributes to the cascade. Purchase history lives in operations-service;
-- crm-service evaluates against supplied activity and caches the customer's current tier.

CREATE TABLE loyalty_tier (
    id                 UUID PRIMARY KEY,
    code               TEXT NOT NULL UNIQUE,
    name               TEXT NOT NULL,
    min_purchase_count INT,                       -- NULL = criterion not used
    min_purchase_value NUMERIC(19, 2),            -- NULL = criterion not used
    period_days        INT,                       -- window the activity is measured over
    discount_rule_id   UUID NOT NULL REFERENCES discount_rule (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The FK deferred from V2, now that loyalty_tier exists.
ALTER TABLE customer
    ADD CONSTRAINT fk_customer_loyalty_tier
    FOREIGN KEY (loyalty_tier_id) REFERENCES loyalty_tier (id);
