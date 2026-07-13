# Contract: Purchase-Order Lifecycle & Operations Integration

**Feature**: 006-supplier-purchasing | **Date**: 2026-07-12

Defines the PO state machine and the integration with `operations-service`. Money is exact decimal
(`BigDecimal`), totals rounded to the cent (FR-004/005/007/010/011, SC-002/003/004).

## State machine

```
DRAFT ──approve──▶ APPROVED ──send──▶ SENT ──receive(partial)──▶ PARTIALLY_RECEIVED
                                          │                              │
                                          └──receive(full)───────────────┴──▶ FULLY_RECEIVED ──▶ CLOSED
DRAFT | APPROVED | SENT(pre-receipt) ──cancel──▶ CANCELLED
```

- **approve**: allowed only from DRAFT; if `order_total` > approval threshold, requires an authorized
  approver (FR-006). Records `approved_by/at`.
- **send**: allowed only from APPROVED; **locks all lines** (agreed prices/quantities immutable, FR-007).
- **receive**: allowed only from SENT / PARTIALLY_RECEIVED; see receiving below.
- **cancel**: allowed before any receipt; no inventory effect (FR-008).
- Illegal transitions (e.g., receive a DRAFT, edit a SENT line) are rejected (SC-002). Transitions are
  atomic and single-winner under concurrency (no double-approve/receive).

## Totals

`line_total = qty_ordered × agreed_price`; `order_total = Σ line_total`, rounded to the cent. Agreed
price is fixed at send even if the supplier catalog price later changes.

## Receiving

For each receipt against a SENT/PARTIALLY_RECEIVED PO:
1. For each line, `qty_received += received`; reject/flag if it would exceed `qty_ordered` (no silent
   over-receipt, FR-010).
2. Recompute `qty_outstanding`; if all lines fully received → FULLY_RECEIVED → CLOSED, else
   PARTIALLY_RECEIVED.
3. Emit **exactly one** goods-receipt event via `OperationsPort` (idempotent on the receipt id).

## OperationsPort (integration boundary)

- `getReorderSignals()` → items at/below reorder point with current stock + target level (input to
  restock). Sourced from operations-service; procurement does not store stock balances.
- `postGoodsReceipt(receipt)` → operations-service updates inventory on-hand and **average cost**
  (AVCO/FIFO per its costing model). Idempotent; retries must not double-post (FR-011, SC-004).

Built and tested against a **fake adapter**; the real adapter is an HTTP client to operations-service.
This mirrors the feature-003 Party port and lets procurement-service be developed/tested in isolation.

## Restock sizing

`suggested_qty = max(target_level − on_hand, 0)` rounded **up** to the supplier's `min_order_qty`;
suggestions consolidated per preferred supplier; convert → draft PO. Auto-submit only when the per-item
flag is enabled (default off, FR-012/013/014, SC-005).

## Tests

- State-machine: every legal transition succeeds; every illegal one is rejected.
- Receiving: partial then full closes the PO; over-receipt prevented/flagged; exactly one receipt event
  per receipt (idempotent).
- Concurrency: no double-approve, no double-receive.
- Restock: sizing respects target + min order; consolidation per supplier; auto-submit gated off.
