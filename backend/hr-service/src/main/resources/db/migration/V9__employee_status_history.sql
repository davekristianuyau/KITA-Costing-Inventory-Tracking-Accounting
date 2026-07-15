-- Employee status change history (FR-003: retain status changes with effective dates, never a
-- destructive overwrite). employee.status stays as the fast current-value lookup; this table is the
-- append-only record of how it got there.

CREATE TABLE employee_status_history (
    id             UUID PRIMARY KEY,
    employee_id    UUID NOT NULL REFERENCES employee (id),
    previous_status TEXT,                    -- NULL for the initial ACTIVE on hire
    status         TEXT NOT NULL,
    effective_date DATE NOT NULL,
    changed_by     TEXT,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_employee_status_history_emp
    ON employee_status_history (employee_id, effective_date);
