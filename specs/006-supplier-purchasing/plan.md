# Implementation Plan: Supplier Records & Purchasing Service

**Branch**: `006-supplier-purchasing` | **Date**: 2026-07-12 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-supplier-purchasing/spec.md`

## Summary

Build KITA's **`procurement-service`** — a standalone Spring Boot microservice owning supplier master
data and the **purchase-order lifecycle**. It stores suppliers and what each supplies (price, lead
time, minimum order), creates POs with computed totals, and moves them through **draft → approved
(threshold-gated) → sent → partially/fully received → closed** (plus cancelled), enforcing legal
transitions and locking a PO once sent. Receiving records full/partial quantities and **emits a
goods-receipt event** to `operations-service` (which owns inventory balances and average-cost) — this
service never mutates stock itself. It also turns low-stock signals (reorder points sourced from
operations-service) into **restock suggestions** grouped by preferred supplier, convertible to POs;
per-item auto-submit is opt-in (off by default). It is the **supplier** side of the Party master the
operations service references by ID (feature 003). Exact decimal money; own PostgreSQL schema, behind
the gateway.

## Technical Context

**Language/Version**: Java 17, Spring Boot 3.5.0 (matches `backend/` root build); Gradle Kotlin DSL
module `backend/procurement-service`.
**Primary Dependencies**: Spring Web, Spring Data JPA (Hibernate), Flyway, Bean Validation, Spring Boot
Actuator; PostgreSQL driver; an `operations-service` client (port + adapter) for reorder signals and
goods-receipt posting; Testcontainers (tests). OpenAPI is the contract source of truth.
**Storage**: PostgreSQL 16 — one schema (`procurement`), Flyway migrations. Money as
`NUMERIC`/`BigDecimal`; quantities as `NUMERIC`.
**Testing**: JUnit 5, Spring Boot Test, **Testcontainers (PostgreSQL)** for integration + migration
tests, PO **state-machine unit tests** (legal/illegal transitions), receiving/over-receipt tests, a
**concurrency test** (no double-approve/double-receive), restock-suggestion tests, OpenAPI contract
tests; Spotless + Checkstyle. TDD throughout.
**Target Platform**: Linux container (`procurement-service` image) behind the Spring Cloud Gateway; a
Release-Set entry (feature 001).
**Project Type**: Backend microservice (supplier master + purchasing).
**Performance Goals**: PO create/approve/receive operations complete interactively (sub-second);
restock-suggestion generation is a batch over items below reorder point.
**Constraints**: Exact decimal money (no float); PO state transitions enforced; sent-PO lines locked;
no silent over-receipt; goods receipt posted to operations-service exactly once per receipt; reorder
signals sourced from operations-service (not duplicated); actions audited; secrets externalized.
**Scale/Scope**: Single-tenant per deployment. Suppliers in the hundreds/thousands; POs and receipts
are the higher-volume data, indexed by supplier + state + date.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Specification-Driven Development | Clarified spec + this plan precede build | PASS |
| II. Test-Driven Development | Contract, Testcontainers integration/migration, PO state-machine, over-receipt, no-double-approve/receive concurrency, and restock tests written before implementation | PASS |
| III. Security & Data Integrity First | Decimal money; enforced state machine; sent-PO lines immutable; exactly-once goods-receipt emission; atomic transactional transitions with locking; Bean Validation; secrets via env; audit trail | PASS — central (PO integrity) |
| IV. Environment Isolation | Config externalized; per-env DB/schema | PASS |
| V. Observability & Debuggability | Structured JSON logs; Actuator `/health`; auditable PO/approval/receipt history | PASS |
| VI. Simplicity & YAGNI | One service; explicit state fields + role checks (no workflow engine); AP/invoice-matching/RFQ out of scope | PASS |
| VII. Automated Quality Gates | fmt/lint/test/contract in CI; fail-fast | PASS |

Initial Constitution Check: **PASS**. Post-Design Check: **PASS** — the PO state machine and the
operations-service integration port keep integrity explicit and testable with no unjustified complexity.

## Project Structure

### Documentation (this feature)

```text
specs/006-supplier-purchasing/
├── plan.md · research.md · data-model.md · quickstart.md
├── contracts/           # OpenAPI + PO-lifecycle + operations integration contract
└── tasks.md             # (/speckit.tasks — not created here)
```

### Source Code (repository root)

```text
backend/procurement-service/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── java/com/kita/procurement/
    │   │   ├── supplier/        # supplier master + supplied items + history
    │   │   ├── purchaseorder/   # PO aggregate + state machine + totals
    │   │   ├── receiving/       # goods receipts + reconciliation
    │   │   ├── restock/         # reorder suggestions from low-stock signals
    │   │   ├── operations/      # port + adapter to operations-service (signals, receipt posting)
    │   │   ├── common/          # money/rounding, audit, error handling
    │   │   └── api/             # REST controllers + DTOs (OpenAPI-backed)
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/    # Flyway V1__*
    └── test/java/com/kita/procurement/  # unit / integration (Testcontainers) / contract
```

**Structure Decision**: New Gradle module `backend/procurement-service` in `backend/settings.gradle.kts`,
mirroring `operations-service` conventions. Deployed as its own container/Release-Set entry. Provides
the **supplier** Party master `operations-service` references by ID (feature-003 Party port for
suppliers); goods receipts post back to operations-service inventory/costing via a port.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| operations-service integration port (signals + receipt posting) | This service must not own inventory balances/costing (that's operations-service); coupling is via an explicit port so it is buildable/testable in isolation | Duplicating stock/cost here would create two sources of truth and integrity drift |
| Explicit PO state machine | PO integrity (no illegal transitions, sent-PO immutability, no over-receipt) is core to correctness (FR-005/007/010) | Ad-hoc status flags without a guarded machine invite illegal states and silent over-receipt |
