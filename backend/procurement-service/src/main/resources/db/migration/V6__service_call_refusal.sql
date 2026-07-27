-- 018 FR-008: persisted, queryable record of every internal caller refused for an identity/transport
-- reason. Durable on purpose — an intrusion attempt must be auditable after the fact, not just a log
-- line. Distinct from a business rejection and from an actor-permission refusal.

CREATE TABLE service_call_refusal (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at    TIMESTAMPTZ NOT NULL,
    peer_address   TEXT,                      -- remote address of the refused caller
    attempted_cn   TEXT,                      -- certificate CN presented; NULL when none was
    reason         TEXT        NOT NULL,      -- NO_CERT | UNTRUSTED_CA | EXPIRED | NOT_ALLOWLISTED
    request_method TEXT,
    request_path   TEXT
);

-- Refusals are read newest-first when investigating; and by reason when reporting.
CREATE INDEX idx_service_call_refusal_occurred_at ON service_call_refusal (occurred_at DESC);
CREATE INDEX idx_service_call_refusal_reason ON service_call_refusal (reason);
