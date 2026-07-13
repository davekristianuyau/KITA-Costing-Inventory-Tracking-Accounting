# Data Model: Supplier Records & Purchasing Service

**Feature**: 006-supplier-purchasing | **Date**: 2026-07-12 | Schema: `procurement` (PostgreSQL, Flyway)

Money/quantities are `NUMERIC` (`BigDecimal`); ids are surrogate UUIDs. Master data keeps history rows;
`created_at`/`updated_at` on all tables.

## Supplier

### Supplier
- `id`, `supplier_code` (unique), `name`, contact fields, `payment_terms`, `delivery_terms`,
  `status` (ACTIVE | INACTIVE)
- **Rules**: unique `supplier_code`; INACTIVE keeps open POs but accepts no new POs (edge case).

### SupplierItem
- `id`, `supplier_id`, `item_ref` (operations-service item id), `supplier_price`, `lead_time_days`,
  `min_order_qty`, `preferred` (bool)
- **Rules**: price history retained; `preferred` marks the default source for restock of an item.

### SupplierChangeHistory
- `id`, `supplier_id`, `changed_at`, `field`, `old_value`, `new_value`, `actor` — append-only (FR-003).

## Purchase orders

### PurchaseOrder
- `id`, `po_no` (unique), `supplier_id`, `status` (DRAFT | APPROVED | SENT | PARTIALLY_RECEIVED |
  FULLY_RECEIVED | CLOSED | CANCELLED), `order_total`, `approved_by`, `approved_at`, `sent_at`,
  `created_by`, `origin` (MANUAL | RESTOCK)
- **State transitions**: DRAFT → APPROVED → SENT → PARTIALLY_RECEIVED → FULLY_RECEIVED → CLOSED;
  DRAFT/APPROVED/SENT(pre-receipt) → CANCELLED. No transition out of CLOSED/CANCELLED.
- **Rules**: approval gated by configurable threshold (FR-006); lines lock at SENT (FR-007); cancel has
  no inventory effect (FR-008); transitions atomic, guarded, single-winner under concurrency.

### PurchaseOrderLine
- `id`, `purchase_order_id`, `item_ref`, `qty_ordered`, `agreed_price`, `qty_received`,
  `qty_outstanding` (derived), `line_total` (qty_ordered × agreed_price)
- **Rules**: agreed price fixed once SENT; `qty_received ≤ qty_ordered` unless over-receipt policy
  allows (else prevented/flagged, FR-010).

## Receiving

### GoodsReceipt
- `id`, `purchase_order_id`, `received_at`, `received_by`, lines of `{po_line_id, qty_received, cost}`,
  `posted_to_operations` (bool), `post_idempotency_key`
- **Rules**: advances PO to PARTIALLY/FULLY_RECEIVED; emits exactly one goods-receipt event to
  operations-service (idempotent on `post_idempotency_key`, FR-011); no silent over-receipt (FR-010).

## Restock

### RestockSuggestion
- `id`, `generated_at`, `supplier_id` (preferred), lines of `{item_ref, suggested_qty, reason}`,
  `status` (OPEN | CONVERTED | DISMISSED), `converted_po_id` (nullable)
- **Rules**: `suggested_qty` reaches target level, rounded up to `min_order_qty`; consolidated per
  supplier; convert → draft PO (FR-012/013); auto-submit only if per-item flag enabled (FR-014).

## Audit

### AuditEvent
- `id`, `actor`, `action` (PO_APPROVED, PO_SENT, GOODS_RECEIVED, SUPPLIER_CHANGED, …), `entity_ref`,
  `at`, `detail` — append-only (FR-016).

## External references (not owned here)

- `item_ref` and reorder points/stock levels live in **operations-service**; read via the
  `OperationsPort`. Goods receipts are posted there to update inventory + average cost.

## Key relationships

- Supplier 1—* SupplierItem, 1—* PurchaseOrder, 1—* SupplierChangeHistory.
- PurchaseOrder 1—* PurchaseOrderLine, 1—* GoodsReceipt.
- RestockSuggestion *—1 Supplier, 0..1—1 PurchaseOrder (on conversion).
