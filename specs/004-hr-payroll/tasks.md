---
description: "Task list for Employee HR & Payroll Service (hr-service)"
---

# Tasks: Employee HR & Payroll Service

**Input**: Design documents from `/specs/004-hr-payroll/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: INCLUDED (TDD per Constitution II). Contract tests (OpenAPI), Testcontainers integration +
migration tests, computation unit tests (payroll/deduction/attendance incl. rounding), and a
concurrency test (no double-pay / double-finalize) are written before the code they cover.

**Module**: new Gradle module `backend/hr-service`, package root `com.kita.hr`, schema `hr`.

## Format: `[ID] [P?] [Story] Description`
- **[P]**: parallelizable (different files, no dependency)

---

## Phase 1: Setup

- [X] T001 Add `:hr-service` to `backend/settings.gradle.kts` and create `backend/hr-service/build.gradle.kts` mirroring `operations-service` (Spring Web, Data JPA, Validation, Actuator, Flyway, PostgreSQL driver, Testcontainers; Spotless/Checkstyle; Windows Testcontainers workaround)
- [X] T002 Create package skeleton under `backend/hr-service/src/main/java/com/kita/hr/{employee,attendance,payroll,deduction,leave,remittance,common,api}` and the `HrServiceApplication` entry point
- [X] T003 [P] Add `backend/hr-service/src/main/resources/application.yml` (datasource via env, JPA, Flyway, Actuator health, JSON logging) — no secrets in file
- [X] T004 [P] Create Flyway baseline `backend/hr-service/src/main/resources/db/migration/V1__init_hr_schema.sql` (schema `hr`, extensions, common columns)
- [X] T005 [P] Add the `hr-service` build to CI `.github/workflows/ci.yml` (build + test job)

---

## Phase 2: Foundational (Blocking)

- [X] T006 [P] `common/Money.java` + rounding policy (half-up to minor unit) and shared `BigDecimal` helpers, with unit tests
- [X] T007 [P] `common/EffectiveDated` support (query "record effective for date") used by compensation, deduction/premium rules, holidays
- [X] T008 [P] `common/AuditEvent` entity + repository + append-only writer (who/when/action/detail, PII/secret-scrubbed)
- [X] T009 [P] `common/` global exception handler + validation error DTO (RFC-7807 style) for the API boundary
- [X] T010 Role model + API security scaffolding (HR_ADMIN, PAYROLL_OFFICER, MANAGER, EMPLOYEE_SELF) with method/route guards
- [X] T011 Testcontainers singleton base test (`AbstractHrIT`) with `@DynamicPropertySource` + per-test TRUNCATE, mirroring operations-service

**Checkpoint**: build compiles; migrations apply; base test harness runs.

---

## Phase 3: User Story 1 - Maintain employee records (Priority: P1)

**Goal**: create/update/retrieve employees with effective-dated compensation + history.
**Independent Test**: create an employee + compensation, edit salary (effective-dated), separate, and
read back the record and its history.

### Tests (write first, must FAIL) ⚠️
- [X] T012 [P] [US1] Contract test for `/employees` + `/employees/{id}` + `/employees/{id}/compensation` in `.../employee/EmployeeApiContractTest`
- [X] T013 [P] [US1] Migration/integration test: employee + compensation persistence and history retention (Testcontainers)

### Implementation
- [X] T014 [P] [US1] Flyway `V2__employee.sql` (employee, compensation_record, attribute/status history)
- [X] T015 [P] [US1] `employee/Employee` + `employee/CompensationRecord` entities + top-level repositories
- [X] T016 [US1] `employee/EmployeeService` (create/update, status lifecycle, effective-dated compensation, history)
- [X] T017 [US1] `api/EmployeeController` + DTOs; validation (unique employee_no, required fields); statutory/tax IDs never in responses/logs
- [X] T018 [US1] Enforce EMPLOYEE_SELF read-only-own on employee endpoints

**Checkpoint**: employee directory works end-to-end and is independently testable. **(MVP part 1)**

---

## Phase 4: User Story 2 - Run payroll for a pay period (Priority: P1) 🎯 MVP

**Goal**: create → compute → finalize a run; payslips + register reconcile; no double-pay.
**Independent Test**: run + finalize payroll for salaried employees; register totals = Σ payslips;
re-finalize rejected.

### Tests (write first, must FAIL) ⚠️
- [X] T019 [P] [US2] Contract test for `/payroll/runs`, `/compute`, `/finalize`, `/register`, `/payslips`
- [X] T020 [P] [US2] Unit tests for basic gross/net computation + pro-ration (per `contracts/payroll-computation.md`), incl. rounding/reconciliation (SC-002)
- [X] T021 [P] [US2] Concurrency integration test: duplicate finalize / same-period pay is rejected (SC-004)

### Implementation
- [X] T022 [P] [US2] Flyway `V3__payroll.sql` (pay_period, payroll_run, payslip, pay_component; idempotency key)
- [X] T023 [P] [US2] `payroll/` entities + repositories (PayPeriod, PayrollRun, Payslip, PayComponent)
- [X] T024 [US2] `payroll/PayrollRunStateMachine` (DRAFT→COMPUTED→FINALIZED, CANCELLED) with guarded transitions
- [X] T025 [US2] `payroll/PayrollComputationService` — basic gross (pro-rated basic + allowances) → net; produces payslips + components
- [X] T026 [US2] `payroll/FinalizeService` — atomic, idempotent finalize (locks figures, idempotency key, duplicate rejected) with row locking
- [X] T027 [US2] `payroll/RegisterService` — register totals reconciling to payslips (SC-002)
- [X] T028 [US2] `api/PayrollController` + DTOs (create/compute/finalize/register/payslips); flag/exclude invalid-compensation employees (FR-010)

**Checkpoint**: salaried payroll runs and finalizes correctly. **(MVP complete with US1)**

---

## Phase 5: User Story 3 - Salary & mandatory deductions (Priority: P1)

**Goal**: statutory + tax + voluntary (loans) deductions on the payslip via a generic effective-dated
engine; PH seed.
**Independent Test**: configure a statutory table + bracketed tax + a loan; compute; verify each line
matches the rule and the loan balance decreases.

### Tests (write first, must FAIL) ⚠️
- [X] T029 [P] [US3] Unit tests for the rule engine (TABLE/BRACKET/PERCENT/FIXED) per `contracts/statutory-engine.md`
- [X] T030 [P] [US3] Golden-value tests for the PH seed (SSS/PhilHealth/Pag-IBIG/BIR) and base ordering (pre-tax before tax) (SC-003)
- [X] T031 [P] [US3] Loan installment test: N finalized runs settle the balance exactly, no over-deduction (SC-005)

### Implementation
- [X] T032 [P] [US3] Flyway `V4__deductions.sql` (deduction_rule + versions, loan/advance) and `V5__seed_ph_statutory.sql` (PH rules as data)
- [X] T033 [P] [US3] `deduction/` entities + repositories (DeductionRule, Loan)
- [X] T034 [US3] `deduction/DeductionRuleEngine` (effective-dated selection + TABLE/BRACKET/PERCENT/FIXED evaluation)
- [X] T035 [US3] `deduction/LoanService` (installment draw-down to zero → SETTLED)
- [X] T036 [US3] Integrate deductions into `PayrollComputationService` (pre-tax statutory → taxable income → tax → voluntary); employer contributions as components; net-pay floor guard (FR-015)
- [X] T037 [US3] `api/DeductionRuleController` (list/create effective-dated rules) + `/employees/{id}/loans`

**Checkpoint**: payslips carry correct statutory, tax, and loan deductions.

---

## Phase 6: User Story 6 - Time & attendance + premiums (Priority: P2)

**Goal**: compute worked time and premiums (OT/holiday/night-diff) from raw DTRs and feed gross.
**Independent Test**: load attendance with lateness/OT/holiday/night hours; verify computed values and
their appearance on the payslip.

### Tests (write first, must FAIL) ⚠️
- [X] T038 [P] [US6] Unit tests for worked-time computation (regular/tardiness/undertime/absence/night) vs schedule
- [X] T039 [P] [US6] Unit tests for premium pay (overtime, holiday by type, night differential) per `PremiumRule`/`HolidayCalendar`
- [X] T040 [P] [US6] Contract test for `/attendance` + `/attendance/worked-time`

### Implementation
- [X] T041 [P] [US6] Flyway `V6__attendance.sql` (work_schedule, attendance_record, premium_rule, holiday_calendar)
- [X] T042 [P] [US6] `attendance/` entities + repositories (WorkSchedule, AttendanceRecord, PremiumRule, HolidayCalendar)
- [X] T043 [US6] `attendance/WorkedTimeService` — compute worked time from DTR × schedule × holiday calendar; flag incomplete attendance
- [X] T044 [US6] `attendance/PremiumService` — overtime/holiday/night-differential pay
- [X] T045 [US6] Feed worked-time + premiums into `PayrollComputationService` gross; `api/AttendanceController` (ingest + worked-time query)

**Checkpoint**: hourly/premium pay is computed and flows into payroll.

---

## Phase 7: User Story 4 - Leave filing & management (Priority: P2)

**Goal**: file/approve/reject leave; balances accrue and draw down; unpaid leave reduces pay.
**Independent Test**: define a leave type, file + approve within balance, block an overlap, and confirm
unpaid approved leave reduces the period's pay.

### Tests (write first, must FAIL) ⚠️
- [X] T046 [P] [US4] Contract test for `/leave/types`, `/leave/requests`, `/decision`, `/leave/balances`
- [X] T047 [P] [US4] Integration tests: balance draw-down, overlap rejection, over-draw guard; unpaid-leave pay reduction (FR-020)

### Implementation
- [X] T048 [P] [US4] Flyway `V7__leave.sql` (leave_type, leave_balance, leave_request)
- [X] T049 [P] [US4] `leave/` entities + repositories (LeaveType, LeaveBalance, LeaveRequest)
- [X] T050 [US4] `leave/LeaveService` (file, approve/reject, accrual, overlap + over-draw guards) transactional
- [X] T051 [US4] Couple approved UNPAID leave into `PayrollComputationService` (reduce covered days); `api/LeaveController`

**Checkpoint**: leave works and correctly affects payroll.

---

## Phase 8: User Story 5 - Payslips, register & remittances (Priority: P3)

**Goal**: payslip retrieval (self), register, and per-agency remittance summaries.
**Independent Test**: finalize a run; fetch an employee's payslip (self only), the register, and a
remittance summary that equals the agency total across payslips.

### Tests (write first, must FAIL) ⚠️
- [X] T052 [P] [US5] Contract test for `/payslips` (self-scope) and `/payroll/runs/{id}/remittances`
- [X] T053 [P] [US5] Integration test: remittance per agency = Σ that component across payslips (SC-003)

### Implementation
- [X] T054 [P] [US5] `remittance/RemittanceService` — group statutory + employer contributions by agency for a period
- [X] T055 [US5] Payslip retrieval endpoint with EMPLOYEE_SELF scoping; `api/RemittanceController`

**Checkpoint**: distribution + statutory reporting outputs available.

---

## Phase 9: Polish & Cross-Cutting

- [X] T056 [P] Structured JSON logging with statutory/tax-ID + PII scrubbing across services (FR-004/023)
- [X] T057 [P] OpenAPI contract wired as source of truth; contract tests green against `contracts/hr-openapi.yaml`
- [X] T058 [P] `backend/hr-service/README.md` (module purpose, run/test, seed rules, endpoints)
- [X] T059 Adjustment-run path (ADJUSTMENT referencing a finalized run; original immutable) end-to-end (FR-011)
- [X] T060 Full `:hr-service:build` green (Spotless/Checkstyle/tests) in CI; fail-fast gate

---

## Dependencies & Execution Order
- Setup (P1) → Foundational (P2) → US1 → US2 → US3 → {US6 ∥ US4} → US5 → Polish.
- US2 depends on US1; US3 & US6 & US4 depend on US2 (pay impact); US5 depends on US2/US3.
- Within a phase, `[P]` tasks (distinct files) run in parallel; tests precede implementation (TDD).

## Implementation Strategy
MVP = Setup + Foundational + US1 + US2 (salaried payroll runs + finalizes, reconciling register). Then
US3 (deductions) completes correctness, US6 (attendance) and US4 (leave) enrich pay, US5 adds outputs.

## Notes
- Highest-risk: idempotent finalize / no double-pay (T026/T021), deduction correctness + base ordering
  (T034/T030), net-pay floor (T036), attendance/premium math (T043/T044) — keep those tests rigorous.
- Money/hours exact decimal everywhere; per-line rounding; reconcile to the cent.
- Commit after each task/group and push per project workflow.

---

## Phase 10: Coverage Gaps (added 2026-07-15 after `/speckit-analyze`)

Requirements that had no task and so were never verified. `AuditEvent` stored `actor`/`at` but
exposed neither, making SC-007's attribution unverifiable from code at all.

- [X] T061 Expose `AuditEvent.getActor()/getAt()/getDetail()` so attribution is assertable (SC-007)
- [X] T062 `common/AuditTrailIT` — payroll finalize + leave approval attributable to user and timestamp (FR-023/SC-007); statutory IDs absent from the trail (FR-004)
- [X] T063 `payroll/PayrollScaleAndEdgeCaseIT` — empty run is finalizable with zero totals (spec Edge Case)
- [X] T064 `payroll/PayrollScaleAndEdgeCaseIT` — 100-employee period computes, finalizes and reconciles (SC-001/SC-002)
- [X] T065 Align spec Key Entities to the implemented `PayComponent` (was EarningComponent/DeductionComponent)
