# Research: Employee HR & Payroll Service

**Feature**: 004-hr-payroll | **Date**: 2026-07-12

All spec clarifications were resolved in the spec's Clarifications session; this records the technical
decisions for the plan. No open NEEDS CLARIFICATION remain.

## Decision: Reuse the operations-service stack (Java 17 / Spring Boot 3.5 / JPA / Flyway / PG)

- **Decision**: Build `hr-service` with the same stack and conventions as `operations-service`.
- **Rationale**: Consistency across services, proven Testcontainers-on-Windows setup, one CI pattern,
  lowest cognitive load for a solo developer (Constitution VI).
- **Alternatives considered**: A different language/framework for payroll — rejected (no benefit, more
  surface area). An off-the-shelf payroll library — rejected (jurisdiction lock-in vs. the required
  configurable engine).

## Decision: Exact decimal money & consistent rounding

- **Decision**: All monetary and hour values are `NUMERIC`/`BigDecimal`; define one rounding policy
  (half-up to the currency's minor unit) applied per computed line; totals are the sum of rounded
  lines so payslip ↔ register reconcile to the cent.
- **Rationale**: Constitution III; SC-002 requires exact reconciliation.
- **Alternatives**: Floating point (rejected — rounding drift); rounding only at the end (rejected —
  line items on the payslip must each be exact and sum to the total).

## Decision: Generic effective-dated deduction rule engine, PH ruleset as seed (Q2=C)

- **Decision**: Model statutory contributions and withholding tax as **DeductionRule** rows —
  effective-dated, versioned, table/bracket-based, evaluated against a defined base. Ship a Flyway
  **seed migration** with the Philippines rules (SSS, PhilHealth, Pag-IBIG, BIR withholding) as
  adoptable data; nothing jurisdiction-specific in code.
- **Rationale**: FR-013/016/026; government tables change yearly; other clients/jurisdictions can add
  rules as data. Keeps the engine testable in isolation.
- **Alternatives**: Hard-coded PH formulas (rejected — code change per table update, no portability);
  external tax API (rejected — offline/local clients, over-engineering for now).

## Decision: hr-service computes Time & Attendance (Q1=B)

- **Decision**: Ingest raw daily time records (punches) via API/import; compute worked hours,
  tardiness, undertime, absences against each employee's **WorkSchedule/Shift**; derive overtime,
  holiday, and night-differential pay from **PremiumRule** + **HolidayCalendar** (also effective-dated
  data). Feed results into gross for the period.
- **Rationale**: User decision; keeps premium-pay rules owned and testable here.
- **Alternatives**: Consume pre-computed time (rejected per Q1); integrate a device SDK (out of scope —
  raw punches are provided to the service).

## Decision: Payroll run state machine + adjustment runs

- **Decision**: `PayrollRun` states **DRAFT → COMPUTED → FINALIZED** (+ CANCELLED from DRAFT/COMPUTED).
  Finalize is a single transaction that locks payslip figures and records an idempotency key
  (client + period + employee set); a second finalize for the same key is rejected. Corrections use a
  new **ADJUSTMENT** run referencing the original, never mutation.
- **Rationale**: FR-008/009/011; Constitution III (atomic, auditable, no double-pay). SC-004.
- **Alternatives**: Editable finalized runs (rejected — destroys audit/immutability); soft-delete +
  recreate (rejected — loses traceability).

## Decision: No workflow engine for approvals

- **Decision**: Leave approval and PO-style gates are simple explicit state fields + role checks, not a
  BPM/workflow engine.
- **Rationale**: Constitution VI (YAGNI); approval here is single-step.
- **Alternatives**: A workflow engine (rejected — disproportionate complexity for one approval step).

## Decision: Leave accrual & balances

- **Decision**: Per-employee, per-type **LeaveBalance** with policy-based accrual computed on a
  schedule/event; **LeaveRequest** draws down on approval within a transaction that guards against
  overlap and over-draw. Unpaid approved leave in a period reduces that period's pay.
- **Rationale**: FR-017–020; keeps leave→payroll coupling explicit and testable.
- **Alternatives**: Ledger-style accrual entries (deferred — a running balance + audit rows suffices at
  this scale).

## Decision: Role-based access & PII handling

- **Decision**: Roles HR-admin, payroll-officer, manager, employee-self enforced at the API boundary;
  employees may read only their own employee record and payslips; statutory/tax IDs are stored but
  never emitted in logs or list responses.
- **Rationale**: FR-004/024, Constitution III/V.
- **Alternatives**: Open access (rejected — payroll/PII sensitivity).

## Decision: Disbursement out of scope; produce outputs for a future integration

- **Decision**: The service computes/records pay and produces register + remittance summaries; bank
  file/ACH generation is out of scope (spec) and left to a later integration.
- **Rationale**: Scope control (Constitution VI); disbursement varies per bank/country.
