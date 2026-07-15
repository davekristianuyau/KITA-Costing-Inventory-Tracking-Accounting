# Implementation Plan: Customer Records & Discounts Service

**Branch**: `005-customer-discounts` | **Date**: 2026-07-12 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/005-customer-discounts/spec.md`

## Summary

Build KITA's **`crm-service`** — a standalone Spring Boot microservice owning customer master data and
a **discount computation engine**. It stores customer profiles, loyalty/repeat tiers, and
government-mandated entitlements (senior/PWD), and computes the final price for a customer + line items
by applying eligible discount tiers **sequentially/cascading** (‑25% then ‑5% on the reduced amount),
returning a reconciling per-tier breakdown. Government-mandated discounts use a **generic
effective-dated discount-rule engine** with a Philippines seed (senior/PWD + VAT treatment); stacking
between statutory and promotional/loyalty discounts follows a configurable policy (default:
most-favorable). It exposes discount computation for the sales flow in `operations-service` and serves
as the **customer** side of the Party master the operations service references by ID (from feature
003). All money math is exact decimal. Own PostgreSQL schema, behind the gateway.

## Technical Context

**Language/Version**: Java 17, Spring Boot 3.5.0 (matches `backend/` root build); Gradle Kotlin DSL
module `backend/crm-service`.
**Primary Dependencies**: Spring Web, Spring Data JPA (Hibernate), Flyway, Bean Validation, Spring Boot
Actuator; PostgreSQL driver; Testcontainers (tests). OpenAPI is the contract source of truth.
**Storage**: PostgreSQL 16 — one schema (`crm`), Flyway migrations. Money as `NUMERIC`/`BigDecimal`;
percentages as `NUMERIC`.
**Testing**: JUnit 5, Spring Boot Test, **Testcontainers (PostgreSQL)** for integration + migration
tests, **discount computation unit tests** (cascading order, rounding, caps, stacking, VAT treatment),
OpenAPI contract tests; Spotless + Checkstyle. TDD throughout.
**Target Platform**: Linux container (`crm-service` image) behind the Spring Cloud Gateway; a
Release-Set entry (feature 001).
**Project Type**: Backend microservice (customer master + pricing/discount engine).
**Performance Goals**: Discount computation returns quickly for interactive checkout (sub-second);
called synchronously by the sales flow.
**Constraints**: Exact decimal money (no float); cascading discount order deterministic and
reproducible; result never negative (capped, flagged); rules effective-dated data; entitlement ID
references never logged; changes audited; discount computation is stateless (does not persist orders).
**Scale/Scope**: Single-tenant per deployment. Customers in the thousands to tens of thousands;
computation is per-request and cheap.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Specification-Driven Development | Clarified spec + this plan precede build | PASS |
| II. Test-Driven Development | Contract, Testcontainers integration/migration, and discount-computation unit tests (cascading, rounding, caps, stacking, VAT) written before implementation | PASS |
| III. Security & Data Integrity First | Decimal money; deterministic cascading; non-negative result; entitlement IDs never logged; audit of rule/entitlement changes; Bean Validation at boundaries; secrets via env | PASS — central (discount math) |
| IV. Environment Isolation | Config externalized; per-env DB/schema | PASS |
| V. Observability & Debuggability | Structured JSON logs (PII scrubbed); Actuator `/health`; the computation breakdown is itself an audit of the math | PASS |
| VI. Simplicity & YAGNI | One service; managed frameworks; discount engine is a small ordered evaluator, no rules DSL; coupons/points out of scope | PASS |
| VII. Automated Quality Gates | fmt/lint/test/contract in CI; fail-fast | PASS |

Initial Constitution Check: **PASS**. Post-Design Check: **PASS** — the cascading evaluator and
effective-dated rule model keep money math exact and auditable with no unjustified complexity.

## Project Structure

### Documentation (this feature)

```text
specs/005-customer-discounts/
├── plan.md · research.md · data-model.md · quickstart.md
├── contracts/           # OpenAPI + discount-engine contract
└── tasks.md             # (/speckit.tasks — not created here)
```

### Source Code (repository root)

```text
backend/crm-service/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── java/com/kita/crm/
    │   │   ├── customer/        # customer master + attributes + history
    │   │   ├── loyalty/         # repeat-customer tiers + eligibility evaluation
    │   │   ├── entitlement/     # government-mandated entitlements (senior/PWD)
    │   │   ├── discount/        # rules + cascading computation engine + stacking policy
    │   │   ├── common/          # money/rounding, effective-dating, audit
    │   │   └── api/             # REST controllers + DTOs (OpenAPI-backed)
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/    # Flyway V1__*, seed (PH senior/PWD rules)
    └── test/java/com/kita/crm/  # unit / integration (Testcontainers) / contract
```

**Structure Decision**: New Gradle module `backend/crm-service` in `backend/settings.gradle.kts`,
mirroring `operations-service` conventions. Deployed as its own container/Release-Set entry. Provides
the **customer** master that `operations-service` references by ID (fulfilling the 003 Party port for
customers); the supplier side is `procurement-service` (006).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Generic effective-dated discount-rule engine | Government discounts (and promos) change and vary; must update as data (FR-009/012) | Hard-coded rates need code changes per promo/law change and block non-PH clients |
| Configurable stacking policy | Statutory-vs-promotional interaction is a real business-policy variable (FR-013) | A fixed rule would be wrong for some clients; the default (most-favorable) is explicit and overridable |
