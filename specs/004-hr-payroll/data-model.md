# Data Model: Employee HR & Payroll Service

**Feature**: 004-hr-payroll | **Date**: 2026-07-12 | Schema: `hr` (PostgreSQL, Flyway)

Money & hours are `NUMERIC` (`BigDecimal`); identifiers are surrogate UUIDs unless noted. All tables
carry `created_at`, `updated_at`; mutable master data keeps history rows rather than overwriting.

## Employee & compensation

### Employee
- `id`, `employee_no` (unique), `first_name`, `last_name`, `birth_date`, contact fields
- `employment_type` (regular | probationary | contractual | casual), `position`, `date_hired`,
  `date_separated` (nullable)
- `status` (ACTIVE | ON_LEAVE | SUSPENDED | SEPARATED)
- Statutory/tax identifiers (stored, never logged): e.g., SSS/PhilHealth/Pag-IBIG/TIN references
- **Rules**: unique `employee_no`; SEPARATED requires `date_separated`; excluded from runs whose period
  starts after separation.

### CompensationRecord (history)
- `id`, `employee_id → Employee`, `effective_date`, `basic_pay`, `pay_frequency`
  (MONTHLY | SEMI_MONTHLY), recurring allowances (list of {name, amount, taxable})
- **Rules**: at most one active record per employee per date; a run uses the record effective for the
  period; prior records retained.

## Time & attendance

### WorkSchedule / Shift
- `id`, `employee_id`, `effective_date`, working days, shift start/end, break, `night_window_start/end`
- **Rules**: used to judge attendance and night-differential hours.

### AttendanceRecord (Daily Time Record)
- `id`, `employee_id`, `work_date`, `time_in`, `time_out` (may be multiple punches), `source`
- **Rules**: raw input; immutable once used by a finalized run.

### WorkedTime (computed per employee per period)
- `regular_hours`, `overtime_hours`, `tardiness_minutes`, `undertime_minutes`, `absence_days`,
  `night_hours`, `holiday_hours` (by holiday type)
- **Rules**: derived from AttendanceRecord × WorkSchedule × HolidayCalendar; incomplete attendance →
  employee flagged, not assumed present.

### HolidayCalendar
- `id`, `date`, `name`, `type` (REGULAR | SPECIAL), `pay_multiplier`, `effective`/scope
### PremiumRule (effective-dated)
- `id`, `kind` (OVERTIME | REST_DAY | HOLIDAY | NIGHT_DIFF), `multiplier`, `effective_date`

## Payroll

### PayPeriod
- `id`, `frequency`, `start_date`, `end_date`, `pay_date`
- **Rules**: non-overlapping per frequency.

### PayrollRun
- `id`, `pay_period_id`, `type` (REGULAR | ADJUSTMENT), `adjusts_run_id` (nullable), `status`
  (DRAFT | COMPUTED | FINALIZED | CANCELLED), `idempotency_key`, `created_by`, `finalized_by`,
  `finalized_at`
- **State transitions**: DRAFT → COMPUTED → FINALIZED; DRAFT|COMPUTED → CANCELLED. No transition out of
  FINALIZED.
- **Rules**: finalize is atomic + idempotent on (client, period, employee-set); duplicate finalize
  rejected (FR-009); ADJUSTMENT must reference an existing FINALIZED run.

### Payslip
- `id`, `payroll_run_id`, `employee_id`, `gross`, `total_deductions`, `total_employer_contrib`,
  `net_pay`, list of **PayComponent**
- **Rules**: `net_pay = gross − total_deductions`; net ≥ configured floor else employee flagged;
  figures immutable once the run is FINALIZED.

### PayComponent
- `id`, `payslip_id`, `category` (EARNING | VOLUNTARY_DEDUCTION | STATUTORY_DEDUCTION |
  EMPLOYER_CONTRIB | TAX), `code`, `label`, `amount`, `basis` (audit of how it was computed)
- **Rules**: earnings positive, deductions reduce net; employer-contrib excluded from net.

## Deductions

### DeductionRule (effective-dated, versioned)
- `id`, `code`, `kind` (STATUTORY | TAX | VOLUNTARY_TEMPLATE), `computation` (TABLE | BRACKET |
  PERCENT | FIXED), `base` (which amount it applies to), `effective_date`, `agency` (for remittance),
  rule payload (bracket/table rows)
- **Rules**: a run uses rules effective for its period; PH SSS/PhilHealth/Pag-IBIG/BIR shipped as seed.

### Loan / Advance
- `id`, `employee_id`, `principal`, `installment_amount`, `installments_total`,
  `installments_paid`, `outstanding_balance`, `status` (ACTIVE | SETTLED | CANCELLED)
- **Rules**: each finalized run deducts one installment until `outstanding_balance = 0` → SETTLED; no
  over-deduction (FR-012, SC-005).

## Leave

### LeaveType
- `id`, `code`, `name`, `pay_treatment` (PAID | UNPAID | CONVERTIBLE), accrual policy
  (rate, period, cap), `allow_negative`
### LeaveBalance
- `id`, `employee_id`, `leave_type_id`, `balance`
- **Rules**: accrues per policy; drawn down on approval.
### LeaveRequest
- `id`, `employee_id`, `leave_type_id`, `start_date`, `end_date`, `duration`, `status`
  (FILED | APPROVED | REJECTED | CANCELLED), `decided_by`, `decided_at`
- **State transitions**: FILED → APPROVED | REJECTED | CANCELLED.
- **Rules**: no overlap with existing APPROVED leave (FR-019); APPROVED within balance unless
  `allow_negative`; APPROVED UNPAID days reduce the covering period's pay (FR-020).

## Audit

### AuditEvent
- `id`, `actor`, `action` (RUN_FINALIZED, LEAVE_APPROVED, COMP_CHANGED, RULE_CHANGED, …),
  `entity_ref`, `at`, `detail` (PII/secret-scrubbed)
- **Rules**: append-only; covers FR-023.

## Key relationships

- Employee 1—* CompensationRecord, WorkSchedule, AttendanceRecord, Loan, LeaveBalance, LeaveRequest.
- PayrollRun 1—* Payslip; Payslip 1—* PayComponent.
- PayrollRun *—1 PayPeriod; ADJUSTMENT run *—1 original PayrollRun.
- DeductionRule / PremiumRule / HolidayCalendar are effective-dated reference data consumed by a run.
