-- Deduction rule engine (statutory + tax + voluntary) and employee loans (feature 004, US3).
-- Rules are effective-dated and versioned by (code, effective_date). TABLE/BRACKET carry rows.

CREATE TABLE deduction_rule (
    id             UUID PRIMARY KEY,
    code           TEXT NOT NULL,
    kind           TEXT NOT NULL,            -- STATUTORY | TAX | VOLUNTARY_TEMPLATE
    computation    TEXT NOT NULL,            -- TABLE | BRACKET | PERCENT | FIXED
    base           TEXT NOT NULL,            -- GROSS | BASIC | TAXABLE_INCOME
    agency         TEXT,
    rate           NUMERIC(9, 6),            -- PERCENT (employee share)
    employer_rate  NUMERIC(9, 6),            -- PERCENT (employer share)
    fixed_amount   NUMERIC(19, 2),           -- FIXED
    floor          NUMERIC(19, 2),           -- PERCENT base floor
    cap            NUMERIC(19, 2),           -- PERCENT base cap
    effective_date DATE NOT NULL,
    UNIQUE (code, effective_date)
);

CREATE TABLE deduction_rule_row (
    id              UUID PRIMARY KEY,
    rule_id         UUID NOT NULL REFERENCES deduction_rule (id),
    low             NUMERIC(19, 2) NOT NULL,
    high            NUMERIC(19, 2),          -- null = no upper bound
    employee_amount NUMERIC(19, 2),          -- TABLE
    employer_amount NUMERIC(19, 2),          -- TABLE
    base_tax        NUMERIC(19, 2),          -- BRACKET
    rate_on_excess  NUMERIC(9, 6),           -- BRACKET
    excess_over     NUMERIC(19, 2)           -- BRACKET
);

CREATE INDEX idx_rule_row_rule ON deduction_rule_row (rule_id);
CREATE INDEX idx_deduction_rule_code ON deduction_rule (code, effective_date);

CREATE TABLE loan (
    id                  UUID PRIMARY KEY,
    employee_id         UUID NOT NULL REFERENCES employee (id),
    principal           NUMERIC(19, 2) NOT NULL,
    installment_amount  NUMERIC(19, 2) NOT NULL,
    installments_total  INT NOT NULL,
    installments_paid   INT NOT NULL DEFAULT 0,
    outstanding_balance NUMERIC(19, 2) NOT NULL,
    status              TEXT NOT NULL,       -- ACTIVE | SETTLED | CANCELLED
    created_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_loan_employee ON loan (employee_id, status);
