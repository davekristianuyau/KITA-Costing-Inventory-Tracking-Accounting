# Quickstart: Operations Service (Sales / Inventory / BOM)

**Feature**: 003-sales-inventory-bom | **Date**: 2026-07-08

How to run and exercise the operations service once implemented. Doubles as the manual acceptance
walkthrough, mapped to the spec's success criteria. The service runs as the
`backend/operations-service` module behind the gateway (feature 002), against PostgreSQL.

## Prerequisites

- The feature-002 scaffold (backend build, Docker Compose, gateway).
- PostgreSQL (via Compose) and the Party service (or its dev stub) reachable at `PARTY_SERVICE_URL`.
- JDK 21; run through the Makefile / Gradle.

## 1. Start and migrate

Bring up the stack; Flyway applies the operations schema. Confirm `/api/operations/health` = UP.

## 2. Set up catalog + stock (US1)

- Create units of measure (`kg`, `g`, `pcs`, `tray`, `m`) and conversions (1 kg = 1000 g;
  1 tray = 30 pcs).
- Create items: raw materials (e.g., `tapa`, `egg`, `rice`, `cloth`, `thread`), components
  (`breaker`, `enclosure`), finished/kit items.
- Post a goods receipt from a supplier; confirm on-hand increases and a `RECEIPT` movement is
  recorded with unit cost. Re-query availability → on-hand reconciles to movements (SC-001).

## 3. Kit / recipe sale (US6) — electrical set & tapsilog

- Define a `KIT` BOM: electrical set = enclosure + 4 breakers; tapsilog = 250 g rice + 200 g tapa
  + 1 egg.
- Create and confirm a sales order for 1 kit → components are reserved (UoM-converted); available
  drops. Fulfill → component on-hand decremented; no finished-good stock involved (SC-009).
- Try to oversell a component → rejected naming the short component.

## 4. Production build (US7) — dresses

- Define a `MANUFACTURED` BOM: 1 dress = 1.7 m cloth + thread.
- Run a build of 5 dresses → 8.5 m cloth + thread consumed, 5 dresses added to stock, atomically
  (SC-010). With insufficient cloth → build rejected, no partial consumption.

## 5. Sales of a stocked good with reservations (US2)

- Confirm an order for a stocked finished good → hard reservation; concurrent confirms for the
  last unit → exactly one succeeds (SC-002). Fulfill (partial allowed) → on-hand decrements.

## 6. Costing & margin (US8)

- Assign raw-material costs; query `/items/{id}/cost?salePrice=…` for a kit/finished item →
  rolled-up cost = Σ component costs (multi-level), and profit/profit% returned (SC-011).
- For a perishable item (FIFO), consume stock across lots → FEFO order, each costed at its lot's
  cost; expired lots excluded (FR-031).

## 7. Availability & movement data (US5)

- Query availability (on-hand/reserved/available) and movement history for a period → numbers
  reconcile; this is the data the Accounting feature will consume (SC-008).

## Acceptance mapping

| Step | Validates |
|------|-----------|
| 2 | US1, FR-001/002/003/003a/013, SC-001 |
| 3 | US6, FR-024/025, SC-009 |
| 4 | US7, FR-026, SC-010 |
| 5 | US2, FR-005–009/018, SC-002 |
| 6 | US8, FR-027–031, SC-007/SC-011 |
| 7 | US5, FR-015/016, SC-006/SC-008 |

## Notes

- Party references validated via the Party service (or stub) — invalid/inactive party rejected
  (SC-005).
- All money/quantity values are exact decimals; no floating point (SC-007).
