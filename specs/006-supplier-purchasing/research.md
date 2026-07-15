# Research: Supplier Records & Purchasing Service

**Feature**: 006-supplier-purchasing | **Date**: 2026-07-12

Spec clarifications were resolved in the spec's Clarifications session. This records technical
decisions. No open NEEDS CLARIFICATION remain.

## Decision: Reuse the operations-service stack

- **Decision**: `procurement-service` uses Java 17 / Spring Boot 3.5 / JPA / Flyway / PostgreSQL /
  Testcontainers, same conventions as `operations-service`.
- **Rationale**: Consistency, one CI pattern, low cognitive load (Constitution VI).
- **Alternatives**: Different stack — rejected.

## Decision: Explicit, guarded PO state machine

- **Decision**: `PurchaseOrder.status` ∈ DRAFT → APPROVED → SENT → PARTIALLY_RECEIVED → FULLY_RECEIVED
  → CLOSED, plus CANCELLED (from DRAFT/APPROVED/SENT-before-receipt). Transitions are guarded methods on
  the aggregate; illegal transitions rejected; lines lock on SENT.
- **Rationale**: FR-005/007; integrity (Constitution III); SC-002.
- **Alternatives**: Free-form status field (rejected — invites illegal states); a workflow engine
  (rejected — YAGNI, transitions are simple and single-step).

## Decision: Receiving reconciles quantities; posts goods receipt exactly once

- **Decision**: A `GoodsReceipt` records received quantities against PO lines, tracks outstanding, and
  advances the PO. Each receipt emits exactly one goods-receipt event (item, qty, cost) to
  operations-service via the integration port; over-receipt beyond ordered is prevented/flagged.
- **Rationale**: FR-009/010/011; SC-003/004; this service does not own stock/cost.
- **Alternatives**: Mutating inventory directly here (rejected — two sources of truth); allowing
  over-receipt silently (rejected — integrity).

## Decision: operations-service integration via a port + adapter

- **Decision**: Define an `OperationsPort` for (a) reading reorder points / stock levels and (b)
  posting goods receipts. Build/test procurement-service against a fake adapter; the real HTTP adapter
  targets operations-service. Emission is idempotent (receipt id as key) so retries don't double-post.
- **Rationale**: FR-011/015; lets this feature be built and tested before wiring the live call; mirrors
  the 003 Party-port approach.
- **Alternatives**: Direct DB access to operations-service (rejected — bounded-context violation);
  synchronous tight coupling with no port (rejected — untestable in isolation).

## Decision: Restock suggestions, auto-submit off by default

- **Decision**: Generate `RestockSuggestion`s from items at/below reorder point (signals from
  operations-service), size each to reach target level respecting the supplier minimum order,
  consolidate by preferred supplier, convert to draft POs on review. Per-item auto-submit is opt-in,
  **default off** (FR-012/013/014, SC-005).
- **Rationale**: Reduces stockouts without surprising auto-purchases; safe default.
- **Alternatives**: Always auto-submit (rejected — unsafe default); no automation (rejected — misses
  the restock user story).

## Decision: Exact decimal money & agreed-price locking

- **Decision**: `BigDecimal` money; line total = qty × agreed price; order total = Σ lines, rounded to
  the cent. A sent PO keeps its agreed prices even if the supplier catalog price later changes.
- **Rationale**: Constitution III; FR-004; edge case (price change after send).
- **Alternatives**: Re-pricing from catalog at receipt (rejected — changes an agreed order).

## Decision: procurement-service is the supplier Party master

- **Decision**: Provides supplier records `operations-service` references by ID (feature-003 Party port
  for suppliers).
- **Rationale**: Avoids duplicate supplier masters; coherent with 003.
- **Alternatives**: Duplicate supplier data in operations-service (rejected — drift).

## Out of scope (deferred)

- Accounts payable, supplier invoice / 3-way matching, payment disbursement, RFQ/bidding, contracts.
