# Contract: Costing & Valuation

**Feature**: 003-sales-inventory-bom | **Date**: 2026-07-08

Defines how stock is valued and how BOM cost/margin is computed. Per-item method (Clarification):
**AVCO** by default; **FIFO** (with FEFO consumption) for perishable/expiry-tracked items. All
math is exact decimal (`NUMERIC`/`BigDecimal`); no floating point (FR-017, SC-007).

## Per-movement cost

Every `StockMovement` stores the **unit cost applied** at the time, so history + valuation
reconcile and the Accounting feature can value any period (SC-008).

## AVCO (Weighted Average) — default

- Maintain a running average unit cost per item (`Item.standard_cost`).
- **Receipt** of `q` at cost `c` with prior on-hand `H` at avg `A`:
  `newAvg = (H·A + q·c) / (H + q)` (guard `H+q = 0`).
- **Issue/consume** of `q`: movement `unit_cost = A` (current average); average unchanged.

## FIFO (+ FEFO) — perishable items

- Each `Lot` carries its own `unit_cost` (set at receipt).
- **Consume**: select lots earliest-expiry-first (then earliest received), skipping expired;
  each consumed quantity is costed at **its lot's** unit cost. A single issue may span lots →
  multiple movement rows (one per lot) each with that lot's cost.

## BOM cost roll-up (US8)

- `cost(item)`:
  - leaf (raw material/component): its current unit cost (AVCO avg or representative lot cost).
  - BOM parent (KIT or MANUFACTURED): `Σ over components (convertToBase(componentQty) × cost(component))`,
    divided by `outputQuantity`, computed recursively (multi-level).
- Cycle-free by construction (BOM cycle detection, FR-011).

## Production build cost

- `BUILD_PRODUCE.unit_cost` of the finished good = `Σ actual consumed component costs / producedQty`
  (actual, from the `BUILD_CONSUME` movements) — so finished-good cost reflects what was really used.

## Margin (US8)

- Given a `salePrice`: `profit = salePrice − cost(item)`; `profitPercent = profit / salePrice`
  (return with defined rounding; reject `salePrice = 0` for percent).

## Boundary with Accounting

This service owns **operational costing**: unit costs on movements, valuation, BOM roll-up, and
margin. The future **Accounting** feature owns financial ledger/journal postings, statements, and
tax — consuming this service's movement/valuation data. No journal entries are produced here.
