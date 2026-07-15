-- Time & attendance: schedules, raw daily time records, holiday calendar, premium rules
-- (feature 004, US6). hr-service computes worked time and premiums from raw punches.

CREATE TABLE work_schedule (
    id                   UUID PRIMARY KEY,
    employee_id          UUID NOT NULL REFERENCES employee (id),
    effective_date       DATE NOT NULL,
    shift_start          TIME NOT NULL,
    shift_end            TIME NOT NULL,
    break_minutes        INT NOT NULL DEFAULT 0,
    standard_daily_hours NUMERIC(5, 2) NOT NULL,
    night_start          TIME,
    night_end            TIME,
    UNIQUE (employee_id, effective_date)
);

CREATE TABLE attendance_record (
    id          UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employee (id),
    work_date   DATE NOT NULL,
    time_in     TIME NOT NULL,
    time_out    TIME NOT NULL,
    source      TEXT,
    UNIQUE (employee_id, work_date)
);

CREATE INDEX idx_attendance_emp_date ON attendance_record (employee_id, work_date);

CREATE TABLE holiday_calendar (
    id            UUID PRIMARY KEY,
    holiday_date  DATE NOT NULL UNIQUE,
    name          TEXT NOT NULL,
    type          TEXT NOT NULL,             -- REGULAR | SPECIAL
    pay_multiplier NUMERIC(5, 2) NOT NULL
);

CREATE TABLE premium_rule (
    id             UUID PRIMARY KEY,
    kind           TEXT NOT NULL,            -- OVERTIME | REST_DAY | HOLIDAY | NIGHT_DIFF
    multiplier     NUMERIC(6, 4) NOT NULL,
    effective_date DATE NOT NULL,
    UNIQUE (kind, effective_date)
);
