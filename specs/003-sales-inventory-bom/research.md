# Phase 0 Research: Sales, Inventory, and Bill-of-Materials Backend Service

**Feature**: 003-sales-inventory-bom | **Date**: 2026-07-08

Resolves the technical approach for the operations service. Stack families (Java/Spring Boot,
PostgreSQL/Flyway, OpenAPI) are fixed by features 001/002; this pins the domain-specific
patterns. Architecture (separate Party service; one combined operations service) and behavior
(multi-location, lot tracking, kit vs manufactured BOM, AVCO/FIFO+FEFO costing) are fixed by the
spec's Clarifications.

## R1. Stock model & the movement ledger

- **Decision**: On-hand is **derived from an immutable append-only movement ledger**, not stored
  as an authoritative mutable counter. A `StockMovement` row records item, location, lot,
  signed quantity, type (receipt/issue/adjustment/transfer/build-consume/build-produce), unit
  cost, reason, timestamp, and source reference. A `StockLevel` row (item × location × lot) holds
  a **cached** on-hand + reserved, updated within the same transaction as the movement for fast
  queries; it must always reconcile to the ledger (SC-001).
- **Rationale**: An append-only ledger gives a provable audit trail and correct valuation
  (constitution III), while the cached level keeps availability queries fast. Reconciliation is
  testable.
- **Alternatives considered**: Mutable counter only (no ledger) — rejected (no audit/valuation);
  event-sourcing framework — rejected (overkill; a plain ledger table suffices).

## R2. Reservation & no-oversell concurrency

- **Decision**: Reservations are **hard**: confirming a sales order (or kit/build demand) selects
  the relevant `StockLevel` rows **`SELECT … FOR UPDATE`** within a serial transaction, checks
  `available = on_hand − reserved`, and increments `reserved`; fulfillment converts reservation to
  an issue movement and decrements on_hand + reserved. All in one in-process DB transaction.
- **Rationale**: Pessimistic row locking in a single service/DB is the simplest correct defense
  against double-reserving the last unit (SC-002) and honors data-integrity-first. Feasible only
  because Q2=C keeps this in one service.
- **Alternatives considered**: Optimistic locking with retry — viable but more complex under
  contention; distributed sagas — rejected (no distribution here); no locking — rejected (oversell).

## R3. Lot tracking & FEFO selection

- **Decision**: Stock is keyed by item × location × **lot**; a `Lot` carries an optional expiry.
  For FIFO/perishable items, consumption selects lots **earliest-expiry-first (FEFO)** (then
  earliest-received); expired lots are excluded from automatic consumption and flagged.
- **Rationale**: Directly implements the clarified lot + FIFO/FEFO decision and the food example;
  lot-keyed levels make FEFO a simple ordered query.
- **Alternatives considered**: Serial (per-unit) tracking — out of scope (spec); non-lot FIFO
  cost layers without lots — rejected (we already track lots, so lots carry cost for FIFO items).

## R4. Costing / valuation (per-item strategy)

- **Decision**: A **valuation strategy per item**:
  - **AVCO (default)**: maintain a running weighted-average unit cost per item; receipts recompute
    the average `((onHand·avg) + (qty·cost)) / (onHand + qty)`; issues cost at current average.
  - **FIFO (perishable)**: each lot carries its own unit cost; consumption (FEFO order) costs at
    the consumed lot's cost.
  - Every movement stores the **unit cost applied**, so valuation and history reconcile.
- **Rationale**: Satisfies the hybrid clarification; storing cost on the movement keeps the ledger
  self-describing for the Accounting feature (SC-008).
- **Alternatives considered**: Standard cost + variances — deferred (manufacturing-heavy); single
  global method — rejected (can't serve food + general store).

## R5. BOM: kit/phantom vs manufactured, explosion, cycle detection

- **Decision**: A `BillOfMaterials` has a **type**: `KIT` (phantom — parent not stocked;
  selling/ordering consumes components) or `MANUFACTURED` (parent stocked; produced by a build).
  Components reference items with per-unit quantities in the component's UoM. **Explosion** walks
  the structure recursively multiplying quantities; **cycle detection** runs on save (reject if an
  item appears in its own expansion). Multi-level supported; a single active BOM per parent
  (versioning deferred).
- **Rationale**: Implements the two example modes (electrical kit / tapsilog recipe vs dresses);
  cycle detection protects integrity (FR-011).
- **Alternatives considered**: Versioned/effectivity-dated BOMs — deferred per spec; flattening
  BOMs at save time — rejected (loses structure; recompute on demand instead).

## R6. Kit sale vs production build

- **Decision**: **Kit/recipe sale** — when a KIT item is ordered/sold, the order's fulfillment
  reserves/consumes the exploded **components** (UoM-converted), not a finished-good count.
  **Production build** — an explicit `Build` operation for a MANUFACTURED item atomically issues
  the exploded component requirements and produces a finished-good receipt; fails wholesale if any
  component is short (no partial consumption).
- **Rationale**: Encodes examples 1–3 exactly; both are single transactions preserving conservation
  of quantity and correct cost flow (component cost → finished-good cost).
- **Alternatives considered**: Treating kits as stocked assemblies — rejected (contradicts the
  "not stocked, consume components at sale" example).

## R7. Unit-of-measure conversions

- **Decision**: Each item has a **base UoM**; `UomConversion` records factors within a UoM family
  (mass: kg↔g; count: tray↔piece; length: m↔cm). All quantities are converted to the item's base
  UoM for storage/movement; BOM component quantities, order lines, and receipts convert on entry.
- **Rationale**: The restaurant example (kg tapa → 200g; tray of 30 → 1 egg) requires conversions;
  storing in a base UoM keeps stock math consistent (FR-022).
- **Alternatives considered**: Free-form units without conversion — rejected (silent errors);
  per-transaction ad-hoc factors — rejected (not reproducible).

## R8. Party (customer/supplier) integration

- **Decision**: A **`PartyClient` port** (interface) with an HTTP adapter calling the Party
  service to validate a customer/supplier ID (exists + active) at order/receipt time; results are
  short-cached. Until the Party service exists, the adapter targets a configurable endpoint and is
  **stubbed in tests**; a feature flag can allow a local dev stub.
- **Rationale**: Keeps party master data out of this service (Q1=A) while allowing this feature to
  be built and tested independently of the not-yet-built Party service.
- **Alternatives considered**: Local party table — rejected (duplicates master data); hard
  dependency with no stub — rejected (blocks independent development/testing).

## R9. API surface & contract

- **Decision**: Contract-first **OpenAPI** (`operations-openapi.yaml`) exposing resources: items &
  UoM, stock levels/availability, movements, receipts, transfers, adjustments, sales orders
  (+ lifecycle actions), BOMs, builds, and cost/margin queries. Backend validated by contract
  tests; the frontend generates its client from it.
- **Rationale**: Matches the project's contract-first convention (feature 002) and lets Accounting
  and the UI integrate cleanly.
- **Alternatives considered**: Code-first OpenAPI — rejected (contract must lead).

## R10. Money & quantity precision

- **Decision**: All monetary and quantity values use `NUMERIC` in PostgreSQL and `BigDecimal` in
  Java, with explicit scale and rounding rules (e.g., money scale 2–4, quantity scale sufficient
  for grams/meters). No `double`/`float` anywhere in domain math.
- **Rationale**: Constitution III (exact money); the food/fabric examples need fractional
  quantities without float error (FR-017, SC-007).
- **Alternatives considered**: Floating point — rejected outright.

## Deferred (not blocking)

- Versioned/effectivity-dated BOMs; back-ordering; advanced shop-floor manufacturing; serial
  tracking; standard-cost variances — all recorded as future enhancements in the spec.
- Party service itself and the Accounting feature — separate features consuming/served by this one.
