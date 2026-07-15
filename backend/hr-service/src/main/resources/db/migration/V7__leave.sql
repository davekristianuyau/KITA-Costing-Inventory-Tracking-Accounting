-- Leave: types + accrual policy, per-employee balances, and requests with a decision lifecycle
-- (feature 004, US4). Approved UNPAID leave reduces the covering period's pay (FR-020).

CREATE TABLE leave_type (
    id             UUID PRIMARY KEY,
    code           TEXT NOT NULL UNIQUE,
    name           TEXT NOT NULL,
    pay_treatment  TEXT NOT NULL,             -- PAID | UNPAID | CONVERTIBLE
    accrual_rate   NUMERIC(6, 2) NOT NULL DEFAULT 0,
    accrual_period TEXT NOT NULL,             -- MONTHLY | ANNUAL
    accrual_cap    NUMERIC(6, 2),             -- nullable = uncapped
    allow_negative BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE leave_balance (
    id            UUID PRIMARY KEY,
    employee_id   UUID NOT NULL REFERENCES employee (id),
    leave_type_id UUID NOT NULL REFERENCES leave_type (id),
    balance       NUMERIC(7, 2) NOT NULL DEFAULT 0,
    UNIQUE (employee_id, leave_type_id)
);

CREATE TABLE leave_request (
    id            UUID PRIMARY KEY,
    employee_id   UUID NOT NULL REFERENCES employee (id),
    leave_type_id UUID NOT NULL REFERENCES leave_type (id),
    start_date    DATE NOT NULL,
    end_date      DATE NOT NULL,
    duration      NUMERIC(5, 2) NOT NULL,     -- days requested
    reason        TEXT,
    status        TEXT NOT NULL,              -- FILED | APPROVED | REJECTED | CANCELLED
    decided_by    TEXT,
    decided_at    TIMESTAMPTZ,
    CHECK (end_date >= start_date)
);

CREATE INDEX idx_leave_request_emp ON leave_request (employee_id, status);
CREATE INDEX idx_leave_request_dates ON leave_request (employee_id, start_date, end_date);
