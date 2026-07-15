-- CRM service baseline. Money is NUMERIC (exact decimal); IDs are UUID (app-generated).
-- Append-only audit trail shared by all modules (who/when/action, PII-scrubbed).

CREATE TABLE audit_event (
    id          UUID PRIMARY KEY,
    actor       TEXT,
    action      TEXT NOT NULL,
    entity_ref  TEXT,
    at          TIMESTAMPTZ NOT NULL,
    detail      TEXT
);

CREATE INDEX idx_audit_event_at ON audit_event (at);
CREATE INDEX idx_audit_event_entity ON audit_event (entity_ref);
