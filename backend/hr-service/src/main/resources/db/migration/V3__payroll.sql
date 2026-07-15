-- Payroll runs, payslips, and pay components (feature 004, US2).
-- Money columns are NUMERIC(19,2) (cents). A run has one idempotency key to prevent double-pay.

CREATE TABLE pay_period (
    id         UUID PRIMARY KEY,
    frequency  TEXT NOT NULL,
    start_date DATE NOT NULL,
    end_date   DATE NOT NULL,
    pay_date   DATE NOT NULL
);

CREATE TABLE payroll_run (
    id              UUID PRIMARY KEY,
    pay_period_id   UUID NOT NULL REFERENCES pay_period (id),
    type            TEXT NOT NULL,
    adjusts_run_id  UUID REFERENCES payroll_run (id),
    status          TEXT NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    created_by      TEXT,
    finalized_by    TEXT,
    finalized_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE payslip (
    id                     UUID PRIMARY KEY,
    payroll_run_id         UUID NOT NULL REFERENCES payroll_run (id),
    employee_id            UUID NOT NULL REFERENCES employee (id),
    gross                  NUMERIC(19, 2) NOT NULL,
    total_deductions       NUMERIC(19, 2) NOT NULL,
    total_employer_contrib NUMERIC(19, 2) NOT NULL,
    net_pay                NUMERIC(19, 2) NOT NULL,
    UNIQUE (payroll_run_id, employee_id)
);

CREATE INDEX idx_payslip_run ON payslip (payroll_run_id);

CREATE TABLE pay_component (
    id         UUID PRIMARY KEY,
    payslip_id UUID NOT NULL REFERENCES payslip (id),
    category   TEXT NOT NULL,
    code       TEXT NOT NULL,
    label      TEXT,
    amount     NUMERIC(19, 2) NOT NULL,
    basis      TEXT
);

CREATE INDEX idx_pay_component_payslip ON pay_component (payslip_id);
