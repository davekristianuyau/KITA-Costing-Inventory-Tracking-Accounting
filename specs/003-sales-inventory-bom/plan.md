# Implementation Plan: Sales, Inventory, and Bill-of-Materials Backend Service

**Branch**: `003-sales-inventory-bom` | **Date**: 2026-07-08 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-sales-inventory-bom/spec.md`

## Summary

Build KITA's **operations service** — one Spring Boot microservice (per the feature-002
scaffold and the Q2=C decision) with internal modules for **catalog, inventory, BOM,
production, sales, and costing**, sharing one PostgreSQL schema and the item catalog. It tracks
stock by item × location × lot with a fully auditable movement ledger; reserves and consumes
stock with strong (transactional) consistency to prevent overselling; supports kit/recipe
(phantom) BOMs consumed at sale, manufactured BOMs produced by an atomic build, and unit-of-
measure conversions; and values inventory per item (Weighted Average by default, FIFO+FEFO for
perishables) with BOM cost roll-up and margin. Customer/supplier are validated by ID against a
separate **Party service** (external dependency). The service exposes an OpenAPI contract behind
the gateway; costing/movement data is exposed for the future Accounting feature.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.3.x (inherited from feature 002); Gradle Kotlin DSL
module in the existing `backend/` multi-module build.
**Primary Dependencies**: Spring Web, Spring Data JPA (Hibernate), Flyway, Bean Validation,
Spring Boot Actuator; PostgreSQL driver; a Party-service HTTP client (port + adapter);
Testcontainers (tests). OpenAPI is the contract source of truth.
**Storage**: PostgreSQL 16 — one schema for the operations service, evolved by Flyway
migrations. Monetary values as `NUMERIC`/`BigDecimal`; quantities as `NUMERIC` (fractional UoM).
**Testing**: JUnit 5, Spring Boot Test, **Testcontainers (PostgreSQL)** for integration +
migration tests, **a concurrency test** proving no oversell, OpenAPI contract tests; Spotless +
Checkstyle. TDD throughout.
**Target Platform**: Linux container (`operations-service` image) behind the Spring Cloud
Gateway; deployed by feature 001.
**Project Type**: Backend microservice (one combined bounded-context service).
**Performance Goals**: Order confirmation/reservation and kit/build operations complete < 1s
under normal load; availability queries return quickly for interactive use. (Modest; refined
later — not the focus of this feature.)
**Constraints**: No negative stock; no oversell under concurrency; all money/quantity math exact
(decimal); stock-affecting operations atomic and transactional; secrets externalized; party
references validated; every stock change recorded as an immutable movement with unit cost + lot.
**Scale/Scope**: Single-tenant per deployment (feature 001). Item catalog and movement ledger
are the highest-volume data; designed for typical SMB volumes (tens of thousands of items,
high movement counts) with indexed queries — not a high-frequency trading workload.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Specification-Driven Development | Clarified spec + this plan precede build | PASS |
| II. Test-Driven Development | Contract, Testcontainers integration/migration, concurrency (no-oversell), and unit tests written before implementation | PASS |
| III. Security & Data Integrity First | Decimal money/qty (`NUMERIC`/`BigDecimal`); atomic transactional stock ops with row locking; no negative stock; immutable audited movements; Bean Validation at boundaries; party validation; secrets via env | PASS — central to this feature |
| IV. Environment Isolation | Config externalized; per-env DB/schema; no env coupling | PASS |
| V. Observability & Debuggability | Structured JSON logs; Actuator `/health`; auditable business-event history | PASS |
| VI. Simplicity & YAGNI | One combined service (Odoo/SAP-style); managed frameworks; no discovery server; deferred BOM versioning & advanced manufacturing | PASS with justification (Complexity Tracking) |
| VII. Automated Quality Gates | fmt/lint/test/contract in CI; fail-fast | PASS |

Initial Constitution Check: **PASS**. Post-Design Check: **PASS** — the reservation and costing
models below preserve data integrity; no new principle tension introduced.

## Project Structure

### Documentation (this feature)

```text
specs/003-sales-inventory-bom/
├── plan.md              # This file
├── spec.md              # Spec (with Clarifications)
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/           # Phase 1
│   ├── operations-openapi.yaml   # Service API (source of truth)
│   ├── costing-model.md          # AVCO / FIFO+FEFO / roll-up / margin rules
│   ├── reservation-model.md      # Concurrency-safe reservation & consumption semantics
│   └── party-integration.md      # Party-service client port + validation contract
└── checklists/
    └── requirements.md
```

### Source Code (repository root) — new backend module

```text
backend/
├── settings.gradle.kts            # add include(":operations-service")
└── operations-service/            # the combined operations microservice
    ├── build.gradle.kts
    ├── Dockerfile
    └── src/
        ├── main/java/com/kita/operations/
        │   ├── catalog/           # Item, UnitOfMeasure, UomConversion
        │   ├── inventory/         # StockLocation, StockLevel, Lot, StockMovement,
        │   │                      #   receipts, transfers, adjustments, reservations
        │   ├── bom/               # BillOfMaterials, BomComponent, explosion, cycle check
        │   ├── production/        # Build (consume components → produce finished stock)
        │   ├── sales/             # SalesOrder, SalesOrderLine, fulfillment, kit consumption
        │   ├── costing/           # AVCO/FIFO valuation, FEFO selection, roll-up, margin
        │   ├── party/             # PartyClient port + HTTP adapter to the Party service
        │   ├── common/            # money/decimal, error model, config, audit
        │   └── api/               # REST controllers per module (conform to OpenAPI)
        ├── main/resources/
        │   ├── application.yml
        │   └── db/migration/      # Flyway V1__…  (catalog, inventory, bom, sales, costing)
        └── test/java/com/kita/operations/
            ├── contract/          # OpenAPI conformance
            ├── integration/       # Testcontainers: migrations, reservation, build, costing
            └── unit/              # explosion, cycle detection, UoM conversion, valuation math

contracts/operations-openapi.yaml  # repo-level copy of the API contract (source of truth)
```

**Structure Decision**: Add one Gradle module `backend/operations-service/` (following the
feature-002 reference-service template) with internal packages per domain module. One schema,
one deployable image, in-process transactions for stock consistency — realizing the Q2=C
"combined operations service" while remaining a single service in the microservices mesh.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Dual valuation methods (AVCO + FIFO/FEFO) in one service | The business needs weighted-average generally but FIFO/expiry-first for perishables (restaurant/food) — a confirmed requirement | A single method can't serve both a general store and a food business correctly; per-item method is the minimum that satisfies the requirement. Encapsulated behind a valuation strategy per item. |
| Combined multi-module service rather than separate inventory/sales/BOM services | Stock reservation/consumption must be strongly consistent; splitting introduces distributed-transaction complexity and oversell risk | The ERP-proven integrated approach (Odoo/SAP). Splitting later remains possible once boundaries harden. |
| Party validated over the network (external service) | Customer/supplier are shared master data owned elsewhere (Q1=A) | Embedding party here duplicates master data and re-couples domains; a thin client port with caching is the lighter long-term cost. |
