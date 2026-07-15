-- Employee master + effective-dated compensation history (feature 004, US1).

CREATE TABLE employee (
    id              UUID PRIMARY KEY,
    employee_no     TEXT NOT NULL UNIQUE,
    first_name      TEXT NOT NULL,
    last_name       TEXT NOT NULL,
    birth_date      DATE,
    email           TEXT,
    phone           TEXT,
    employment_type TEXT NOT NULL,
    position        TEXT,
    date_hired      DATE NOT NULL,
    date_separated  DATE,
    status          TEXT NOT NULL,
    sss_no          TEXT,
    philhealth_no   TEXT,
    pagibig_no      TEXT,
    tin             TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

-- One row per compensation change; the row effective for a period is used by payroll. History kept.
CREATE TABLE compensation_record (
    id             UUID PRIMARY KEY,
    employee_id    UUID NOT NULL REFERENCES employee (id),
    effective_date DATE NOT NULL,
    basic_pay      NUMERIC(19, 4) NOT NULL,
    pay_frequency  TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    UNIQUE (employee_id, effective_date)
);

CREATE INDEX idx_comp_employee ON compensation_record (employee_id, effective_date);
