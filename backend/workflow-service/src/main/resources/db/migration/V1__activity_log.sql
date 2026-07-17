-- Append-only back-office activity log (FR-003, SC-003). One row per transition/attempt; the durable
-- "who did what, when, outcome". No UPDATE/DELETE. Referenced domain records appear only as target_ref.

CREATE TABLE back_office_activity (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_employee_id TEXT        NOT NULL,           -- acting employee (X-Kita-User, HR-validated)
    action            TEXT        NOT NULL,           -- a BackOfficeAction value
    outcome           TEXT        NOT NULL,           -- SUCCESS | REJECTED_NOT_PERMITTED | REJECTED_INVALID | FAILED_UNAVAILABLE
    reason            TEXT,                           -- required when outcome != SUCCESS (scrubbed)
    target_ref        TEXT,                           -- e.g. sales-order:<id>, po:<id>, receipt:<id>
    maker_employee_id TEXT,                           -- for a checker's confirmation (self-review audit)
    idempotency_key   TEXT,                           -- key forwarded to downstream posts (retry-safety)
    retry_count       INT         NOT NULL DEFAULT 0, -- downstream retries performed (SC-010)
    at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_activity_actor_at  ON back_office_activity (actor_employee_id, at);
CREATE INDEX idx_activity_action_at ON back_office_activity (action, at);
CREATE INDEX idx_activity_target    ON back_office_activity (target_ref);
