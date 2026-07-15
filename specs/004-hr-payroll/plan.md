# Implementation Plan: Employee HR & Payroll Service

**Branch**: `004-hr-payroll` | **Date**: 2026-07-12 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-hr-payroll/spec.md`

## Summary

Build KITA's **`hr-service`** — a standalone Spring Boot microservice for people management and
payroll. It owns employee master data (with effective-dated compensation history), computes **time &
attendance** (regular hours, tardiness/undertime, overtime, holiday, and night-differential pay) from
raw daily time records, runs **payroll** for a pay period (gross → deductions → net) producing payslips
and a reconciling register, applies **voluntary and government-mandated statutory deductions** via a
generic effective-dated rule engine (Philippines SSS/PhilHealth/Pag-IBIG/BIR shipped as seed), and
manages **leave** filing/approval/balances that feed payroll. Payroll runs are draft→finalized with
duplicate-payment prevention and correction via adjustment runs. All money is exact decimal. It is a
separate bounded context from `operations-service` with its own PostgreSQL schema, exposed behind the
gateway.

## Technical Context

**Language/Version**: Java 17, Spring Boot 3.5.0 (matches `backend/` root build); Gradle Kotlin DSL
module in the existing multi-module build (`backend/hr-service`).
**Primary Dependencies**: Spring Web, Spring Data JPA (Hibernate), Flyway, Bean Validation, Spring Boot
Actuator; PostgreSQL driver; Testcontainers (tests). OpenAPI is the contract source of truth.
**Storage**: PostgreSQL 16 — one schema (`hr`) for this service, evolved by Flyway migrations. Money as
`NUMERIC`/`BigDecimal`; hours/quantities as `NUMERIC`; dates as `DATE`.
**Testing**: JUnit 5, Spring Boot Test, **Testcontainers (PostgreSQL)** for integration + migration
tests, computation unit tests for payroll/deduction/attendance math (including rounding), a
**concurrency test** proving a period cannot be double-paid and a run cannot be double-finalized,
OpenAPI contract tests; Spotless + Checkstyle. TDD throughout.
**Target Platform**: Linux container (`hr-service` image) behind the Spring Cloud Gateway; deployed as
a Release-Set entry by feature 001.
**Project Type**: Backend microservice (single bounded context, internal modules).
**Performance Goals**: A payroll run for ~100 employees computes and finalizes interactively (< a few
seconds); employee/leave queries return quickly. Modest SMB scale; not the focus of this feature.
**Constraints**: Exact decimal money math (no floating point); payroll finalize is atomic and
idempotent (no double pay); net pay never negative/below floor; statutory & premium rules are
effective-dated data, not code; secrets externalized; statutory/tax IDs never logged; every finalize
and leave approval audited.
**Scale/Scope**: Single-tenant per deployment. Employees in the hundreds to low thousands; payslip and
attendance rows are the highest-volume data, indexed by employee + period.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Specification-Driven Development | Clarified spec (Session 2026-07-12) + this plan precede build | PASS |
| II. Test-Driven Development | Contract, Testcontainers integration/migration, payroll/deduction/attendance computation unit tests, and a double-pay/double-finalize concurrency test written before implementation | PASS |
| III. Security & Data Integrity First | Decimal money (`NUMERIC`/`BigDecimal`); atomic transactional finalize with locking; no duplicate pay; net-pay floor; immutable audited runs/approvals; statutory/tax IDs excluded from logs; Bean Validation at boundaries; secrets via env | PASS — central to this feature |
| IV. Environment Isolation | Config externalized; per-env DB/schema; no env coupling | PASS |
| V. Observability & Debuggability | Structured JSON logs (secrets/PII scrubbed); Actuator `/health`; auditable finalize/approval history | PASS |
| VI. Simplicity & YAGNI | One service, managed frameworks, no workflow engine (simple state machines); statutory rules as data; disbursement/device capture out of scope | PASS with justification (Complexity Tracking) |
| VII. Automated Quality Gates | fmt/lint/test/contract in CI (extend existing `ci.yml` backend job); fail-fast | PASS |

Initial Constitution Check: **PASS**. Post-Design Check: **PASS** — the run/adjustment state machine
and effective-dated rule model below preserve data integrity and add no unjustified complexity.

## Project Structure

### Documentation (this feature)

```text
specs/004-hr-payroll/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (OpenAPI + computation/engine contracts)
└── tasks.md             # Phase 2 output (/speckit.tasks — not created here)
```

### Source Code (repository root)

```text
backend/hr-service/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── java/com/kita/hr/
    │   │   ├── employee/        # employee master + compensation history
    │   │   ├── attendance/      # daily time records, schedules, worked-time computation
    │   │   ├── payroll/         # payroll runs, payslips, register, run state machine
    │   │   ├── deduction/       # deduction rules engine (statutory + voluntary), loans
    │   │   ├── leave/           # leave types, requests, balances, accrual
    │   │   ├── remittance/      # remittance summaries by agency
    │   │   ├── common/          # money/rounding, effective-dating, audit, error handling
    │   │   └── api/             # REST controllers + DTOs (OpenAPI-backed)
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/    # Flyway V1__*, seed data (PH statutory ruleset)
    └── test/
        └── java/com/kita/hr/    # unit / integration (Testcontainers) / contract tests
```

**Structure Decision**: New Gradle module `backend/hr-service` added to `backend/settings.gradle.kts`,
mirroring `operations-service` conventions (top-level repository interfaces, package-per-module, Flyway
migrations, Testcontainers). It is deployed as its own container/Release-Set entry (feature 001).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Generic effective-dated rule engine for deductions | Statutory tables change yearly and vary by client; must update without code changes (FR-016/026) | Hard-coding PH rules would force a code change + redeploy for every government table update and block non-PH clients |
| Adjustment-run model (not editing finalized runs) | Finalized payroll must be immutable for audit/integrity (FR-011) | Mutating a finalized run destroys the audit trail and risks silent double-pay |
| hr-service owns time & attendance computation | User decision (Q1=B): compute OT/tardiness/holiday/night-diff from raw DTRs | Consuming pre-computed time would push premium-pay rules into an undefined external system |
