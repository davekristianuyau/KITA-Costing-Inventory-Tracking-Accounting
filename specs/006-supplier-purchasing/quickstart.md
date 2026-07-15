# Quickstart: Supplier Records & Purchasing Service

**Feature**: 006-supplier-purchasing | **Date**: 2026-07-12

Operator happy path once implemented; doubles as the manual acceptance walkthrough.
`procurement-service` runs behind the gateway with its own `procurement` PostgreSQL schema and talks to
`operations-service` via the OperationsPort (fake adapter in tests).

## 1. Onboard a supplier
Create a supplier with terms; add two supplied items (price, lead time, min order). Expected: unique
`supplier_code`, status ACTIVE, items selectable on POs, history retained on edits.

## 2. Create and manage a PO (MVP)
Create a PO with two lines → verify line/order totals → approve (under vs. over the threshold) → send.
Expected: totals reconcile to the cent; over-threshold needs an approver; sending locks the lines;
receiving a not-yet-sent PO is rejected; cancelling before receipt closes it with no inventory effect.

## 3. Receive against the PO
Record a partial receipt, then the remainder. Expected: PO goes PARTIALLY_RECEIVED → FULLY_RECEIVED →
CLOSED; over-receipt beyond ordered is prevented/flagged; each receipt emits exactly one goods-receipt
event to operations-service (verified via the fake adapter).

## 4. Restock suggestions
Feed items below reorder point with preferred suppliers; generate suggestions. Expected: each item’s
suggested quantity reaches its target level (rounded up to min order), consolidated into one suggested
PO per supplier; convert a suggestion into a draft PO; with auto-submit disabled (default) suggestions
wait for review.

## 5. Party integration
Fetch a supplier by id via the endpoint operations-service uses to validate a receipt/PO's supplier.

## Acceptance mapping
| Step | Validates |
|------|-----------|
| 1 | US1, FR-001/002/003 |
| 2 | US2, FR-004/005/006/007/008, SC-001/002 |
| 3 | US3, FR-009/010/011, SC-003/004 |
| 4 | US4, FR-012/013/014, SC-005 |
| 5 | FR-015 (supplier Party master) |
