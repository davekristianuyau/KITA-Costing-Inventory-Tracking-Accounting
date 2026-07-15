# Feature Specification: Employee HR & Payroll Service

**Feature Branch**: `004-hr-payroll`
**Created**: 2026-07-12
**Status**: Draft
**Input**: User description: "backend service of the employees — payroll, salary deductions, mandatory
deductions, filing leaves and records" (customers and suppliers split into specs 005 and 006)

## Overview

`hr-service` is the KITA backend service for **people management and payroll**: it maintains employee
records, runs payroll for a pay period, computes earnings and deductions (both voluntary salary
deductions and government-mandated statutory contributions/tax), and manages leave filing, approval,
and balances. It is a distinct bounded context from the sales/inventory `operations-service`; it
shares nothing but the platform (auth, gateway) and has its own database schema.

## Clarifications

### Session 2026-07-12
- Q: Does hr-service compute time & attendance, or consume already-computed worked time? →
  A: **Compute it** — the service owns worked-time, overtime, tardiness, holiday, and
  night-differential computation from raw daily time records (device capture stays external).
- Q: How are government-mandated statutory deductions handled? → A: A **generic configurable rule
  engine** with a **Philippines ruleset shipped as adoptable seed data** (SSS, PhilHealth, Pag-IBIG,
  BIR withholding), not hardwired.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Maintain employee records (Priority: P1)

An HR administrator creates and maintains an employee's record: personal details, employment details
(hire date, position, employment type, status), compensation (basic pay, pay frequency), and the
identifiers needed for statutory reporting and tax. Records have a lifecycle (active → on-leave →
suspended → separated) and a full change history for audit.

**Why this priority**: Every other capability (payroll, leave) depends on an accurate employee
record. It is the foundational master data and delivers value on its own as an employee directory.

**Independent Test**: Create an employee with compensation and statutory IDs, edit their position and
salary, change status to separated, and retrieve the record and its change history — all without any
payroll or leave features present.

**Acceptance Scenarios**:

1. **Given** no matching employee, **When** HR creates an employee with required fields, **Then** the
   employee is persisted with a unique employee number and status "active".
2. **Given** an active employee, **When** HR updates their basic pay effective a future date, **Then**
   the change is recorded with its effective date and the prior value is retained in history.
3. **Given** an active employee, **When** HR sets status to "separated" with a separation date, **Then**
   the employee is excluded from future payroll runs but remains queryable.
4. **Given** a required field is missing or a duplicate employee number is used, **When** HR saves,
   **Then** the system rejects the change with a clear validation message.

---

### User Story 2 - Run payroll for a pay period (Priority: P1) 🎯 MVP

A payroll officer initiates a payroll run for a pay period and a set of employees. The system computes,
per employee: gross earnings (basic pay pro-rated for the period + recurring allowances), all
deductions, and net pay; produces a payslip per employee; and produces a payroll register (totals) for
the run. A run is a reviewable draft before it is finalized; finalizing locks the figures.

**Why this priority**: This is the core reason the service exists. Together with US1 it is the MVP —
a payroll officer can pay people correctly.

**Independent Test**: With employees and their compensation in place, create a payroll run for a
period, compute it, review each payslip (gross, itemized deductions, net), and finalize — verifying
the register totals equal the sum of payslips.

**Acceptance Scenarios**:

1. **Given** active employees with basic pay, **When** the officer computes a run for a pay period,
   **Then** each employee gets a payslip with gross, itemized deductions, and net pay, and net =
   gross − total deductions.
2. **Given** an employee hired or separated mid-period, **When** the run is computed, **Then** basic
   pay is pro-rated for the actual covered days.
3. **Given** a computed draft run, **When** the officer finalizes it, **Then** the payslip figures are
   locked and the run cannot be silently recomputed; corrections require a new adjustment run.
4. **Given** a finalized run, **When** the officer re-runs payroll for the same period and employees,
   **Then** the system prevents duplicate payment for the same period (blocked/idempotent).
5. **Given** an employee with no valid compensation, **When** the run is computed, **Then** that
   employee is flagged and excluded rather than producing an incorrect payslip.

---

### User Story 3 - Compute salary and mandatory deductions (Priority: P1)

The payroll computation applies two kinds of deductions: **voluntary salary deductions** (e.g., cash
advances, loans with installment schedules, company-store charges) and **government-mandated statutory
deductions** (social security, health insurance, housing fund contributions, and withholding tax),
each computed from configurable rules/brackets against the correct base. Deductions are itemized on
the payslip and summarized for remittance.

**Why this priority**: A payslip is only correct if deductions are correct; statutory deductions are a
legal obligation. This is inseparable from a usable payroll run.

**Independent Test**: Configure a statutory contribution table and a bracketed withholding-tax rule,
attach a loan with an installment schedule to an employee, compute a run, and verify each deduction
line matches the rule and the loan balance decreases by the installment.

**Acceptance Scenarios**:

1. **Given** a statutory contribution table keyed by salary range, **When** payroll computes an
   employee whose base falls in a range, **Then** the correct employee (and employer) contribution is
   applied and itemized.
2. **Given** a bracketed withholding-tax rule, **When** payroll computes taxable income after
   pre-tax deductions, **Then** the tax withheld matches the applicable bracket.
3. **Given** an active loan of amount L with N installments, **When** N payroll runs finalize, **Then**
   each deducts one installment, the outstanding balance reaches zero, and no further deduction occurs.
4. **Given** total deductions would exceed net-pay-floor rules, **When** payroll computes, **Then** the
   employee is flagged for review rather than producing a negative or below-floor net pay.

---

### User Story 4 - File and manage leave (Priority: P2)

An employee files a leave request for a leave type and date range; a manager approves or rejects it.
Approved leave draws down the employee's leave balance for that type; balances accrue per policy.
Leave outcomes feed payroll (paid leave has no pay impact; unpaid leave reduces pay for those days;
convertible unused leave can be paid out).

**Why this priority**: Leave is essential HR functionality and affects payroll accuracy, but payroll
can run without it (treating everyone as present) for an initial release.

**Independent Test**: Configure a leave type with an accrual and starting balance, file a request,
approve it, and verify the balance decreases and the request appears in the employee's leave history;
file a request exceeding the balance and verify it is blocked or flagged per policy.

**Acceptance Scenarios**:

1. **Given** an employee with available balance for a leave type, **When** they file leave within
   balance and a manager approves it, **Then** the balance decreases by the leave duration and the
   request is marked approved.
2. **Given** a filed request, **When** the manager rejects it, **Then** no balance is consumed and the
   employee sees the rejection.
3. **Given** a request that overlaps an existing approved leave, **When** it is filed, **Then** the
   system prevents the overlap.
4. **Given** approved unpaid leave in a pay period, **When** payroll computes that period, **Then** pay
   is reduced for the unpaid days.

---

### User Story 5 - Payslips, payroll register, and remittance summaries (Priority: P3)

The service produces an individual payslip per employee per finalized run, a payroll register (all
employees, all components, totals) for the run, and remittance summaries that group statutory
deductions by agency for the period so they can be paid to the government.

**Why this priority**: Distribution and statutory reporting are important operationally but come after
correct computation exists.

**Independent Test**: Finalize a run and retrieve a specific employee's payslip, the full register
(verifying totals reconcile), and a remittance summary per statutory agency (verifying each equals the
sum of that deduction across employees).

**Acceptance Scenarios**:

1. **Given** a finalized run, **When** a payslip is requested for an employee, **Then** it shows
   itemized earnings, deductions, employer contributions, and net pay for the period.
2. **Given** a finalized run, **When** the register is requested, **Then** column totals equal the sum
   of the corresponding payslip lines.
3. **Given** a finalized run, **When** a remittance summary is requested per agency, **Then** it equals
   the total of that statutory deduction across all employees in the run.

---

### User Story 6 - Capture attendance and compute worked time & premiums (Priority: P2)

Raw attendance (daily time records / clock in-out punches) is provided per employee. Against each
employee's work schedule/shift, the service computes regular worked hours, tardiness, undertime, and
absences, and derives premium pay: overtime (by configurable multipliers), holiday pay (from a holiday
calendar), and night differential. These computed amounts flow into the payroll run's gross earnings.

**Why this priority**: This makes payroll accurate for hourly and premium-earning staff. Basic
salaried payroll (US2) can run without it, so it enriches rather than blocks the MVP.

**Independent Test**: Load a period of attendance for an employee on a defined schedule that includes
late arrivals, overtime hours, and a holiday; compute worked time; and verify regular hours, tardiness,
overtime pay, holiday pay, and night differential each match the schedule, holiday calendar, and
premium rules — then confirm those amounts appear on that employee's payslip.

**Acceptance Scenarios**:

1. **Given** an employee on a defined shift with attendance punches, **When** worked time is computed,
   **Then** regular hours, tardiness, undertime, and absences match the schedule.
2. **Given** hours worked beyond the shift and an overtime rule, **When** computed, **Then** overtime
   pay equals overtime hours × rate × the applicable multiplier.
3. **Given** a day marked in the holiday calendar and hours in the night window, **When** computed,
   **Then** holiday pay and night differential are applied at their configured multipliers.
4. **Given** missing or incomplete attendance for an employee in the period, **When** computed, **Then**
   the employee is flagged for review rather than assumed fully present.

---

### Edge Cases

- Retroactive pay change effective within an already-finalized period → handled via an adjustment run,
  never by mutating a finalized run.
- Employee separated mid-period with outstanding loan → final pay attempts full loan settlement and
  flags any shortfall.
- Duplicate leave filing / overlapping ranges → rejected.
- Payroll run initiated for a period with no eligible employees → produces an empty, finalizable run
  with zero totals (not an error).
- Statutory table has no matching range for an employee's base → the employee is flagged, not silently
  zero-rated.
- Concurrent finalize of the same run → only one succeeds; the other is rejected.
- Rounding: monetary rounding is applied consistently per line and totals reconcile to the cent.

## Requirements *(mandatory)*

### Functional Requirements

**Employee records**
- **FR-001**: System MUST let HR create, update, and retrieve employee records with personal,
  employment, and compensation details, each employee having a unique employee number.
- **FR-002**: System MUST track employee status (active, on-leave, suspended, separated) and exclude
  separated employees from payroll runs after their separation date.
- **FR-003**: System MUST retain a change history of compensation and status changes with effective
  dates (no destructive overwrite of prior values).
- **FR-004**: System MUST store the statutory/tax identifiers required for deductions and remittance
  without exposing them in logs.

**Payroll run**
- **FR-005**: System MUST let a payroll officer create a payroll run scoped to a pay period and a set
  of eligible employees.
- **FR-006**: System MUST compute, per employee, gross earnings, itemized deductions, employer
  contributions, and net pay, where net = gross − total employee deductions.
- **FR-007**: System MUST pro-rate basic pay for employees active for only part of the pay period.
- **FR-008**: System MUST support a draft → finalized lifecycle; finalizing locks payslip figures.
- **FR-009**: System MUST prevent duplicate payment for the same employee and pay period.
- **FR-010**: System MUST flag and exclude employees with invalid/missing compensation rather than
  producing incorrect payslips.
- **FR-011**: Corrections to a finalized run MUST be made via a separate adjustment run, never by
  mutating the finalized run.

**Deductions**
- **FR-012**: System MUST apply voluntary salary deductions, including loans/advances with installment
  schedules that draw down an outstanding balance to zero over successive finalized runs.
- **FR-013**: System MUST apply government-mandated statutory deductions and withholding tax computed
  from configurable tables/brackets against the correct base, itemized separately from voluntary ones.
- **FR-014**: System MUST compute employer-side statutory contributions where applicable and include
  them in remittance summaries (not deducted from employee net).
- **FR-015**: System MUST enforce net-pay-floor / maximum-deduction rules, flagging employees for
  review instead of producing negative or below-floor net pay.
- **FR-016**: Deduction rules/tables MUST be versioned with effective dates so a run uses the rules in
  effect for its pay period.

**Leave**
- **FR-017**: System MUST let employees file leave requests (type + date range) and managers approve or
  reject them.
- **FR-018**: System MUST maintain per-employee, per-type leave balances with policy-based accrual, and
  prevent approving leave that exceeds the available balance (unless the policy allows advance/negative).
- **FR-019**: System MUST prevent overlapping leave requests for the same employee.
- **FR-020**: Approved unpaid leave within a pay period MUST reduce pay for the unpaid days in that
  period's run.

**Outputs & audit**
- **FR-021**: System MUST produce an individual payslip per employee per finalized run and a payroll
  register whose totals reconcile to the payslips.
- **FR-022**: System MUST produce remittance summaries grouping statutory deductions and employer
  contributions by agency for a period.
- **FR-023**: System MUST record an audit trail of who initiated/finalized runs and approved leave, and
  when.
- **FR-024**: System MUST restrict payroll and employee-record actions to authorized roles (HR admin,
  payroll officer, manager, employee-self) — employees may only see their own records and payslips.

**Time & attendance** (hr-service owns this computation)
- **FR-025**: System MUST ingest raw attendance (daily time records / clock in-out punches) per
  employee and compute, against each employee's work schedule/shift, their regular worked hours,
  tardiness, undertime, and absences for the pay period.
- **FR-027**: System MUST compute overtime pay from configurable overtime rules (e.g., ordinary-day,
  rest-day, and premium multipliers) applied to overtime hours.
- **FR-028**: System MUST compute holiday pay from a configurable holiday calendar (regular vs. special
  holidays with their multipliers) and night-differential pay for hours worked within the configured
  night window.
- **FR-029**: System MUST feed computed worked time and premiums into the pay period's gross earnings,
  and flag employees with missing/incomplete attendance for review rather than assuming full attendance.

**Deductions (statutory engine)**
- **FR-026**: Statutory deductions and withholding tax MUST be computed by a **generic, configurable,
  effective-dated rule engine** with no jurisdiction hardwired into the computation. A **Philippines
  ruleset (SSS, PhilHealth, Pag-IBIG, BIR withholding tax) MUST ship as adoptable seed/sample data** a
  client can use as-is or replace.

### Key Entities *(include if feature involves data)*

- **Employee**: a person employed by the client — personal info, employment info (hire/separation
  dates, position, employment type, status), and statutory/tax identifiers.
- **CompensationRecord**: an employee's pay definition effective from a date — basic pay, pay
  frequency, recurring allowances; historical entries retained.
- **PayPeriod**: a bounded date range for which payroll is run (frequency-driven).
- **PayrollRun**: a computation for a pay period over a set of employees; has state (draft, computed,
  finalized) and links to payslips and a register.
- **Payslip**: one employee's computed result for a run — itemized earnings, deductions, employer
  contributions, net pay.
- **EarningComponent / DeductionComponent**: named lines on a payslip (e.g., basic, allowance;
  statutory contribution, tax, loan installment) with amount and category (earning/voluntary/statutory).
- **DeductionRule**: a versioned, effective-dated table/bracket defining how a statutory or tax
  deduction is computed from a base (generic engine; Philippines rules provided as seed data).
- **WorkSchedule / Shift**: an employee's expected working days, hours, and night window used to judge
  attendance.
- **AttendanceRecord (Daily Time Record)**: raw clock in-out punches for an employee on a date, the
  input to worked-time computation.
- **WorkedTime**: computed per employee per period — regular hours, overtime hours, tardiness,
  undertime, absences, and night-window hours.
- **HolidayCalendar**: dated holidays (regular vs. special) with pay multipliers.
- **PremiumRule**: configurable multipliers for overtime, rest-day, holiday, and night-differential pay.
- **Loan/Advance**: an employee obligation with principal, installment schedule, and outstanding
  balance drawn down by payroll.
- **LeaveType**: a category of leave with an accrual policy and pay treatment (paid/unpaid/convertible).
- **LeaveRequest**: an employee's request for a leave type over a date range with an approval state.
- **LeaveBalance**: per-employee, per-type available balance.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A payroll officer can run and finalize payroll for a 100-employee period, review payslips,
  and reconcile the register in a single session without manual recalculation.
- **SC-002**: For every finalized run, the payroll register totals equal the sum of the individual
  payslips to the cent (100% reconciliation).
- **SC-003**: 100% of statutory deductions and withholding tax on finalized payslips match the
  effective rule/table for the pay period (independently verifiable against the published tables).
- **SC-004**: The system never pays the same employee twice for the same pay period and never produces
  a payslip with net pay below the configured floor.
- **SC-005**: Loans/advances with an installment schedule reach a zero balance after exactly the
  scheduled number of finalized runs, with no over- or under-deduction.
- **SC-006**: An employee can file leave and see an approve/reject outcome, with the leave balance
  reflecting the decision, and approved unpaid leave correctly reduces the next run's pay.
- **SC-007**: Every payroll finalize and leave approval is attributable to a user and timestamp in the
  audit trail; employees can access only their own records and payslips.
- **SC-008**: For a period of attendance with lateness, overtime, a holiday, and night hours, the
  computed regular hours, tardiness, overtime pay, holiday pay, and night differential each match the
  schedule, holiday calendar, and premium rules, and appear correctly on the payslip.

## Assumptions

- **Pay frequency** is configurable per client; the initial release supports **monthly** and
  **semi-monthly** (e.g., 15th / end-of-month) periods.
- **Currency** is a single currency per client (the client's local currency); multi-currency payroll is
  out of scope.
- **hr-service computes time & attendance** (worked hours, tardiness, overtime, holiday, night
  differential) from raw daily time records. The **physical capture device integration** (biometric
  hardware / time-clock firmware) is out of scope — raw punches are provided to the service via its
  API/import; it owns the computation from that point.
- **Payment disbursement** (bank files/ACH) is out of scope for this spec; the service computes and
  records pay, producing register/remittance outputs a later integration can consume.
- **Statutory rules are data, not code**: the deduction engine is generic and effective-dated; a
  Philippines ruleset ships as adoptable seed data, so government-table updates need no code change.
- This service is separate from `operations-service`; employee data here is not the same as the
  `party` (customer/supplier) data there.

## Out of Scope

- Customer records and discount computation (spec 005).
- Supplier records and purchase orders (spec 006).
- Recruitment/applicant tracking, performance reviews, training, and benefits administration beyond
  statutory contributions.
- Time & attendance **hardware/device** integration (raw punches are fed in; computation is in scope);
  bank disbursement file generation.

## Dependencies

- Platform authentication and role model (for FR-024 role-based access).
- The KITA gateway for exposure; its own PostgreSQL schema for persistence.
