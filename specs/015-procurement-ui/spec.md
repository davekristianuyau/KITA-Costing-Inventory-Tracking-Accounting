# Feature Specification: Procurement Service UI

**Feature Branch**: `015-procurement-ui`
**Created**: 2026-07-20
**Status**: Draft
**Input**: User description: "Procurement full UI filling the 011 console workspace manifest for procurement-service — supplier master, purchase-order lifecycle, receiving (posts goods receipt to operations), and restock/reorder suggestions; read-first then the write actions; evolves the Procurement manifest + result views, no backend code."

## Overview

The full per-service UI for `procurement-service` (spec 006) on the 011 console foundation. It grows the
Procurement tab into the complete workspace: the supplier master, the purchase-order lifecycle (draft → approved
→ received), receiving (which posts a goods receipt to `operations-service`), and restock/reorder suggestions.
Every function is an entry in the Procurement **manifest** rendered by the 011 workspace framework and called
through the 009 edge. No backend changes — `procurement-service` already provides these capabilities.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse suppliers and purchase orders (Priority: P1) 🎯 MVP

A procurement user opens the Procurement tab and browses suppliers and purchase orders: a supplier list, a
supplier's detail, a purchase-order list, and a PO's detail with its lines and current status.

**Why this priority**: Supplier and PO visibility is the base of every procurement task, is read-only, and proves
the per-service pattern end-to-end with no risk of mutating orders or stock.

**Independent Test**: Sign in → Procurement → "Suppliers" lists suppliers; selecting one shows detail; "Purchase
orders" lists POs; selecting one shows its lines and status — all read-only via the edge.

**Acceptance Scenarios**:

1. **Given** a signed-in client, **When** they open Procurement → Suppliers, **Then** suppliers render (name, id,
   status) with loading and empty states.
2. **Given** a supplier, **When** their detail is opened, **Then** their attributes are shown.
3. **Given** purchase orders, **When** they are listed, **Then** each PO's supplier, date, and status render.
4. **Given** a PO, **When** its detail is opened, **Then** its lines (item, quantity, price) and status render.

---

### User Story 2 - Restock and reorder suggestions (Priority: P2)

A user views the system's restock/reorder suggestions — items at or below their reorder point with a suggested
quantity and supplier — to decide what to purchase.

**Why this priority**: Suggestions are the decision input that drives new POs; they are read-only and
self-contained.

**Independent Test**: Open Procurement → Reorder suggestions → a list of items needing restock renders with the
suggested quantity and supplier.

**Acceptance Scenarios**:

1. **Given** items below their reorder point, **When** suggestions are viewed, **Then** each item, its shortfall,
   the suggested quantity, and a suggested supplier render.
2. **Given** no items below reorder point, **When** suggestions are viewed, **Then** a clear empty state renders.

---

### User Story 3 - Create and progress a purchase order (Priority: P3)

A user performs the PO write actions: create/update a supplier, create a purchase order (with lines), and move a
PO through its lifecycle (e.g. approve) — each a validated form with a clear result.

**Why this priority**: Writes create commitments to suppliers; they follow the read views so users can verify
suppliers, items, and suggestions first. Each action is independently useful.

**Independent Test**: Create a supplier → it appears in the list; create a PO → it appears as draft with its
lines; approve the PO → its status changes.

**Acceptance Scenarios**:

1. **Given** the create-supplier form, **When** required fields are provided and submitted, **Then** the supplier
   is created; **When** a required field is missing, **Then** the call is blocked with inline validation.
2. **Given** the create-PO form, **When** a valid supplier and lines are submitted, **Then** a draft PO is created
   with its lines.
3. **Given** a draft PO, **When** it is approved, **Then** its status transitions to approved.

---

### User Story 4 - Receive against a purchase order (Priority: P3)

A user records receiving for an approved PO (full or partial), which posts a goods receipt to
`operations-service`; the PO's received quantities and status update accordingly.

**Why this priority**: Receiving is the highest-consequence action — it moves real stock in operations — so it
comes last, after suppliers, POs, and approval are visible and trusted.

**Independent Test**: Receive an approved PO → the PO shows received quantities and an updated status; the linked
operations stock reflects the goods receipt.

**Acceptance Scenarios**:

1. **Given** an approved PO, **When** a receipt is recorded for some/all lines, **Then** the PO's received
   quantities and status update and a success result is shown.
2. **Given** a receipt is posted, **When** the corresponding operations stock is viewed, **Then** the received
   quantity is reflected (goods receipt posted to operations).
3. **Given** a receipt exceeding the ordered quantity, **When** submitted, **Then** a clear validation/error
   result is shown rather than an inconsistent state.

### Edge Cases

- A function returns an empty list (e.g. no POs, no suggestions) → a clear empty state.
- Receiving or approving a PO in the wrong state → a clear message explaining the required state, not a crash.
- Over-receipt (received > ordered) → blocked/flagged with a clear result.
- Missing required inputs → inline validation blocks the call before the edge.
- A read/write returns 4xx/5xx via the edge → a clear, non-revealing error with the failing action's context.
- Receiving depends on `operations-service`; if that call fails, the failure is surfaced clearly (no silent
  partial state in the UI).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Procurement tab MUST present its functions grouped by area (Suppliers, Purchase orders,
  Reorder, Receiving) in the 011 left pane.
- **FR-002**: Users MUST be able to list suppliers and view a supplier's detail.
- **FR-003**: Users MUST be able to list purchase orders and view a PO's lines and status.
- **FR-004**: Users MUST be able to view restock/reorder suggestions with suggested quantity and supplier.
- **FR-005**: Users MUST be able to create/update a supplier via a validated form.
- **FR-006**: Users MUST be able to create a purchase order (with lines) via a validated form.
- **FR-007**: Users MUST be able to progress a PO through its lifecycle (e.g. approve) via a validated action.
- **FR-008**: Users MUST be able to record receiving (full/partial) against an approved PO, which posts a goods
  receipt to operations; the PO's received quantities and status MUST reflect it.
- **FR-009**: Every function MUST show explicit loading, empty, result, and error states (reusing the 011
  framework), with results in the shape best suited to the data.
- **FR-010**: All calls MUST go through the 009 edge with the signed-in client's session; the UI MUST NOT bypass
  the edge or embed credentials.
- **FR-011**: Write actions MUST block on missing required inputs with inline validation before calling the edge,
  and MUST surface lifecycle/over-receipt errors clearly.
- **FR-012**: The feature MUST NOT modify `procurement-service` or `operations-service`; it consumes existing
  capabilities only (receiving's cross-service posting is done by the backend, not the UI).

### Key Entities *(include if feature involves data)*

- **Supplier**: a vendor record — name, id, status, and attributes.
- **Purchase Order**: an order to a supplier with a status lifecycle (draft/approved/received) and lines.
- **PO Line**: an ordered item with quantity, price, and received quantity.
- **Reorder Suggestion**: an item at/below its reorder point with a suggested quantity and supplier.
- **Goods Receipt**: the record of received goods; posting it updates operations stock (cross-service).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From the Procurement tab, a user can find a supplier and see their open purchase orders in **under
  30 seconds**, with no page reloads between functions.
- **SC-002**: **100%** of Procurement functions show a distinct loading, empty, result, and error state.
- **SC-003**: A user can create a PO and then progress it to approved entirely within the console.
- **SC-004**: Recording a receipt updates the PO's received quantities and is reflected in operations stock,
  observable from the console.
- **SC-005**: **0** service calls bypass the edge or expose credentials in the browser (session-cookie only).
- **SC-006**: The full Procurement workspace is usable at the 011 responsive floor (down to **768px**) and fully
  keyboard-navigable.

## Assumptions

- `procurement-service` (spec 006) exposes the read and write capabilities above via the edge under
  `/api/procurement`; this feature maps them into manifest functions and adds no endpoints.
- Receiving's goods-receipt posting to `operations-service` is performed by the backend; the UI triggers
  receiving and surfaces the result, and does not itself call operations for the posting.
- Result rendering reuses the 011 shapes (table/json/detail/message); a Procurement-specific PO detail view may
  be added where a generic shape is insufficient.
- In the local simulation every client can reach all services (011 assumption A1).

## Dependencies

- **011 service-console-ui** — the console shell, workspace framework, manifest seam, and edge fetch.
- **009 client-login-deploy-sim** — the edge/session the calls route through.
- **006 supplier-purchasing** — the `procurement-service` backend being surfaced.
- **003 sales-inventory-bom** — `operations-service`, which receiving posts goods receipts to (via the backend).

## Out of Scope

- Any backend/API change to `procurement-service` or `operations-service`.
- Other services' UIs — their own specs.
- Real cloud deployment; supplier bidding/RFQ; invoice matching; analytics dashboards; role/entitlement management.
