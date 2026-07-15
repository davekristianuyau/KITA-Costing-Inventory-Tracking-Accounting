-- Optional employee scope for a payroll run (FR-005). A run with no rows here covers every eligible
-- employee; rows restrict it to exactly that set. Kept as a child table rather than an array column
-- so the set is queryable and referentially sound.

CREATE TABLE payroll_run_employee (
    run_id      UUID NOT NULL REFERENCES payroll_run (id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employee (id),
    PRIMARY KEY (run_id, employee_id)
);
