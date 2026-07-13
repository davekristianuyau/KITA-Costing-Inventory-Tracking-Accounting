# Feature Specification: Supplier Records & Purchasing Service

**Feature Branch**: `006-supplier-purchasing`
**Created**: 2026-07-12
**Status**: Draft
**Input**: User description: "supplier records to create purchase orders or resupplying stock" (part of
the 004–006 split)

## Overview

`procurement-service` is the KITA backend service for **supplier master data and purchasing**. It
maintains suppliers (and what each supplies at what price/terms), manages the **purchase-order (PO)
lifecycle** from draft through approval, sending, receiving, and closing, and supports **restock** by
turning low-stock signals into purchase suggestions/POs. It is a distinct bounded context from
`operations-service`: this service owns suppliers and POs, while goods receipt posts into
operations-service inventory/costing (which already tracks stock and average cost).

## Clarifications

### Session 2026-07-12
- **PO lifecycle ownership**: procurement-service owns the PO from draft to closed; the **physical
  goods receipt that changes inventory/costing is posted in `operations-service`**, and procurement
  reconciles received quantities against the PO.
- **Restock**: reorder-point/low-stock signals (from operations-service inventory) generate purchase
  **suggestions**; POs are created from suggestions or manually. Fully automatic PO submission without
  review is opt-in per client, off by default.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Maintain supplier records (Priority: P1)

A purchasing administrator creates and maintains suppliers: identity/contact, payment and delivery
terms, and a catalog of what the supplier supplies (item, supplier price, lead time, minimum order).
Records have an active/inactive lifecycle and a change history for audit.

**Why this priority**: Suppliers and their supply terms are the foundation for every purchase order
and deliver value on their own as a supplier directory.

**Independent Test**: Create a supplier with terms and two supplied items (price + lead time), edit a
price, deactivate the supplier, and retrieve the record and its history — without any PO features.

**Acceptance Scenarios**:

1. **Given** no matching supplier, **When** the admin creates one with required fields, **Then** it is
   persisted with a unique supplier code and status "active".
2. **Given** a supplier, **When** the admin adds a supplied item with price and lead time, **Then** it
   is available for selection on a purchase order.
3. **Given** a supplier, **When** attributes/prices change, **Then** prior values are retained in
   history.
4. **Given** a duplicate supplier code or missing required field, **When** saving, **Then** the change
   is rejected with a clear message.

---

### User Story 2 - Create and manage purchase orders (Priority: P1) 🎯 MVP

A purchasing officer creates a purchase order for a supplier with line items (item, quantity, agreed
price), reviews the computed totals, and moves it through its lifecycle: draft → approved → sent →
(partially/fully) received → closed. Approval is gated by a configurable threshold. A cancelled PO
never affects inventory.

**Why this priority**: This is the core reason the service exists and, with US1, forms the MVP — an
officer can order stock from a supplier and track it to completion.

**Independent Test**: Create a PO with two lines, verify line and order totals, approve it (respecting
the threshold), send it, and confirm the state transitions are enforced (e.g., cannot receive a draft).

**Acceptance Scenarios**:

1. **Given** an active supplier and supplied items, **When** the officer creates a PO with quantities
   and prices, **Then** line totals and the order total are computed and the PO is saved as "draft".
2. **Given** a draft PO under the approval threshold, **When** submitted, **Then** it is approved;
   **Given** one over the threshold, **Then** it requires an authorized approver.
3. **Given** an approved PO, **When** sent to the supplier, **Then** its state is "sent" and its lines
   are locked against edits.
4. **Given** a PO not yet approved/sent, **When** a receipt is attempted, **Then** it is rejected
   (illegal transition).
5. **Given** any PO, **When** cancelled before receipt, **Then** it closes with no inventory effect.

---

### User Story 3 - Receive against a purchase order (Priority: P2)

When goods arrive, a receiver records received quantities against the PO's lines (full or partial).
Procurement reconciles received-vs-ordered and advances the PO to partially/fully received, then
closed; the actual stock and cost update is posted into `operations-service` inventory/costing.

**Why this priority**: Closing the loop on a PO matters, but the PO lifecycle up to "sent" (US2) is
independently useful first.

**Independent Test**: Against a sent PO, record a partial receipt then a final receipt, and verify the
PO moves partially-received → fully-received → closed, that over-receipt beyond ordered quantity is
prevented or flagged, and that a goods-receipt event is emitted to operations-service.

**Acceptance Scenarios**:

1. **Given** a sent PO, **When** a partial receipt is recorded, **Then** the PO is "partially received"
   and outstanding quantities are tracked.
2. **Given** outstanding quantities, **When** the remainder is received, **Then** the PO is "fully
   received" and then "closed".
3. **Given** a receipt exceeding the ordered quantity, **When** recorded, **Then** it is prevented or
   flagged per policy (no silent over-receipt).
4. **Given** a recorded receipt, **When** it posts, **Then** a goods-receipt event carrying item,
   quantity, and cost is emitted for operations-service to update inventory and average cost.

---

### User Story 4 - Restock / reorder suggestions (Priority: P2)

Low-stock signals (items at or below their reorder point, from operations-service inventory) are turned
into restock **suggestions** grouped by preferred supplier, sized to reach the target level (respecting
minimum order quantities). An officer reviews suggestions and converts them into POs; optional per-item
automation can submit POs without review when explicitly enabled.

**Why this priority**: Automating replenishment reduces stockouts and manual work, but manual PO
creation (US2) already covers the need for an MVP.

**Independent Test**: Feed a set of items below reorder point with preferred suppliers, generate
suggestions, and verify each suggestion targets the correct supplier and quantity (respecting minimums);
convert one suggestion into a PO and verify it matches.

**Acceptance Scenarios**:

1. **Given** items at/below reorder point with a preferred supplier, **When** suggestions are
   generated, **Then** each item yields a suggested order quantity to reach its target level,
   respecting the supplier's minimum order.
2. **Given** suggestions for several items from one supplier, **When** grouped, **Then** they are
   consolidated into a single suggested PO per supplier.
3. **Given** a reviewed suggestion, **When** converted, **Then** a draft PO is created matching the
   suggested lines.
4. **Given** per-item auto-submit is enabled, **When** a suggestion is generated, **Then** a PO is
   created and advanced per policy; **Given** it is disabled (default), **Then** the suggestion waits
   for review.

---

### Edge Cases

- Supplier deactivated while it has open POs → existing POs proceed; no new POs can be created for it.
- Price on a supplied item changes after a PO is sent → the sent PO keeps its agreed price (locked).
- Partial receipts across multiple deliveries → outstanding quantity tracked until fully received.
- Over-receipt (more than ordered) → prevented or flagged per policy.
- Reorder suggestion below a supplier's minimum order → rounded up to the minimum, flagged.
- Duplicate/concurrent approval or receipt of the same PO → only one succeeds.
- Currency/rounding → line and order totals reconcile to the cent.

## Requirements *(mandatory)*

### Functional Requirements

**Supplier records**
- **FR-001**: System MUST let staff create, update, and retrieve suppliers, each with a unique supplier
  code and an active/inactive status.
- **FR-002**: System MUST maintain payment/delivery terms and a catalog of supplied items per supplier
  (supplier price, lead time, minimum order quantity).
- **FR-003**: System MUST retain a change history of supplier and supplied-item changes (no destructive
  overwrite).

**Purchase orders**
- **FR-004**: System MUST let an officer create a PO for a supplier with line items (item, quantity,
  price) and compute line and order totals.
- **FR-005**: System MUST enforce a PO lifecycle — draft → approved → sent → partially/fully received →
  closed (plus cancelled) — and reject illegal transitions (e.g., receiving a draft).
- **FR-006**: System MUST gate approval by a configurable threshold, requiring an authorized approver
  above it.
- **FR-007**: System MUST lock a PO's lines against edits once it is sent to the supplier.
- **FR-008**: A cancelled PO MUST have no inventory or cost effect.

**Receiving**
- **FR-009**: System MUST record full and partial receipts against a sent PO and track outstanding
  quantities until fully received, then close the PO.
- **FR-010**: System MUST prevent or flag over-receipt beyond the ordered quantity (no silent
  over-receipt).
- **FR-011**: On receipt, System MUST emit a goods-receipt event (item, quantity, cost) for
  `operations-service` to update inventory and average cost; procurement does not itself mutate stock.

**Restock / reorder**
- **FR-012**: System MUST generate restock suggestions from low-stock signals (items at/below reorder
  point), sizing each to reach the target level and respecting the supplier's minimum order quantity.
- **FR-013**: System MUST consolidate suggestions by preferred supplier into a suggested PO per
  supplier and let an officer convert a suggestion into a draft PO.
- **FR-014**: System MUST support opt-in, per-item automatic PO submission from suggestions, **off by
  default** (suggestions await review).

**Integration & audit**
- **FR-015**: System MUST source stock levels/reorder points and post goods receipts via
  `operations-service` (this service owns suppliers and POs, not inventory balances).
- **FR-016**: System MUST record an audit trail of PO approvals, sends, receipts, and supplier changes
  (who/when) and restrict actions to authorized roles.

### Key Entities *(include if feature involves data)*

- **Supplier**: identity/contact, payment and delivery terms, status.
- **SupplierItem**: an item a supplier provides — supplier price, lead time, minimum order quantity.
- **PurchaseOrder**: an order to a supplier with a state, lines, totals, and approval metadata.
- **PurchaseOrderLine**: item, quantity ordered, agreed price, quantity received, outstanding quantity.
- **GoodsReceipt**: a recorded delivery against a PO (quantities + cost) that triggers an
  operations-service inventory/cost update.
- **RestockSuggestion**: a proposed order (supplier + lines + quantities) derived from low-stock
  signals, pending conversion to a PO.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A purchasing officer can create a supplier, add supplied items, raise a PO, get it
  approved per threshold, and send it in a single session; line and order totals reconcile to the cent.
- **SC-002**: 100% of PO state changes follow the allowed lifecycle; illegal transitions (e.g.,
  receiving a draft, editing a sent PO) are always rejected.
- **SC-003**: Partial and full receipts correctly track outstanding quantity and close the PO on full
  receipt; over-receipt is never silently accepted.
- **SC-004**: Every recorded receipt emits exactly one goods-receipt event to operations-service with
  matching item, quantity, and cost.
- **SC-005**: Restock suggestions target the correct supplier and quantity (respecting minimum order)
  for every item at/below reorder point; auto-submit stays off unless explicitly enabled.
- **SC-006**: PO approvals, sends, receipts, and supplier changes are attributable to a user and
  timestamp; only authorized roles can perform them.

## Assumptions

- **This service owns suppliers and POs; `operations-service` owns inventory balances and costing.**
  Goods receipts are posted here and applied to stock/average-cost there via an event/integration.
- **Reorder points and stock levels** are sourced from operations-service; procurement computes
  suggestions from them.
- **Approval threshold and auto-submit** are configurable per client; auto-submit defaults off.
- Single currency per client; monetary rounding reconciles to the cent.
- Supplier payment (accounts payable) and invoice matching are out of scope for this spec.

## Out of Scope

- Employee HR/payroll (spec 004) and customer records/discounts (spec 005).
- Inventory balances, stock movements, and average-cost computation (owned by `operations-service`).
- Accounts payable, supplier invoice/3-way matching, and payment disbursement.
- Supplier sourcing/RFQ/bidding and contract management.

## Dependencies

- `operations-service` for stock levels / reorder points and for applying goods receipts to inventory
  and costing.
- Platform authentication/roles; the gateway for exposure; its own PostgreSQL schema.
