-- Auditable history of significant business events (FR-021).
--
-- The movement ledger already records every stock change (FR-003), but FR-021 is broader: orders
-- confirmed/fulfilled/cancelled, receipts, adjustments, BOM changes and builds. Log lines are not a
-- durable, queryable history, so those events are persisted here — matching hr/crm/procurement.

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
