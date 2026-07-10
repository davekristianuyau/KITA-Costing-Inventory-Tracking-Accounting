# Contract: Reservation & Consumption Semantics

**Feature**: 003-sales-inventory-bom | **Date**: 2026-07-08

Defines the concurrency-safe behavior that guarantees **no oversell** (SC-002) and correct stock
math (constitution: Security & Data Integrity First). All operations below run in a single
in-process database transaction (Q2=C combined service).

## Definitions

- **on_hand**: physical quantity present (Σ movements).
- **reserved**: quantity promised to confirmed orders/builds, not yet issued.
- **available** = `on_hand − reserved`.

## Reserve (sales order confirm, kit demand, build demand)

1. Resolve the target stock rows (item × location [× lot], FEFO order for FIFO/perishable items).
2. `SELECT … FOR UPDATE` those `StockLevel` rows (pessimistic lock).
3. Verify `available ≥ requested` (summed across eligible lots/locations). If not → **reject**
   (or back-order per policy) naming the short item; make no changes.
4. Increment `reserved` and create `Reservation` rows linking to the order line/build.
5. Commit. The lock guarantees two concurrent confirms cannot both reserve the last unit.

For **KIT** lines: explode to components first, then reserve each component (steps 1–5 per
component); all-or-nothing across components.

## Fulfill (issue)

1. Lock the reserved rows.
2. Create `ISSUE`/`SALE_ISSUE` movements (cost per valuation strategy — AVCO avg or FIFO lot).
3. Decrement `on_hand` and `reserved` by the fulfilled quantity; delete/settle the reservation.
4. Partial fulfillment allowed: remaining quantity stays reserved; order stays `CONFIRMED` until
   fully fulfilled, then `FULFILLED`.

## Build (production)

Atomic: lock component rows → verify all component requirements available → `BUILD_CONSUME`
movements (decrement components) + `BUILD_PRODUCE` movement (increment finished good) → commit.
If any component is short, **abort the whole transaction** (no partial consumption, FR-026).

## Cancel

Release reservations: decrement `reserved`, delete `Reservation` rows, set order `CANCELLED`.
`on_hand` is unchanged (nothing was issued).

## Invariants (testable)

- `reserved ≤ on_hand` and `on_hand ≥ 0` always (no negative stock).
- Under N concurrent confirms for the last unit, exactly one succeeds (concurrency test, SC-002).
- After fulfill/cancel, `reserved` returns to the correct value; ledger reconciles to StockLevel.
- Expired lots are never auto-consumed; FEFO order honored for FIFO items (FR-031).
