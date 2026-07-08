# Feature Specification: Sales, Inventory, and Bill-of-Materials Backend Service

**Feature Branch**: `003-sales-inventory-bom`
**Created**: 2026-07-08
**Status**: Draft
**Input**: User description: "this is spec 003 backend service for the sales, inventory, bill of materials system, also make suggestions for the customer and supplier profile/details — should it be in a separate service that connects to this?"

## Overview

This feature delivers KITA's core operational backend capabilities: **Inventory** (items and
stock), **Bill of Materials** (product component structures), and **Sales** (customer orders
that consume stock). It is the first real business domain built on the multi-service scaffold
(feature 002) and deployed by the multi-cloud pipeline (feature 001).

**Recommendation on customer/supplier data (the user's question):** customer and supplier
profiles are **master data** used well beyond sales/inventory (also by accounting/costing,
purchasing, and reporting). Best practice — and the recommendation here — is a **separate
"Party" master-data service** that owns customer and supplier profiles, which this operations
domain **references by ID** (single source of truth, no duplicated party data). This spec is
written to reference parties from that separate service; the Party service becomes its own
feature. See the Clarifications questions to confirm before planning.

## Clarifications

### Session 2026-07-08

- Q: Where do customer & supplier profiles live? → A: A **separate "Party" master-data
  service** owns customer and supplier profiles (they share most fields); this operations
  domain references parties by ID and validates against it. Mirrors Odoo's unified `res.partner`
  and SAP S/4HANA's converged Business Partner.
- Q: How is this domain split into services? → A: **One combined "operations" service** with
  internal modules for Inventory, BOM, and Sales sharing one schema and the item catalog — the
  integrated approach both Odoo (modular monolith) and SAP S/4HANA (SD/MM/PP on one DB) use.
  It is a single bounded-context service in the microservices mesh; stock reservations stay
  strongly consistent via in-process transactions. Sales may be carved out later if needed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Manage inventory items and stock (Priority: P1)

An operator maintains the catalog of inventory items (products, components, raw materials) and
tracks how much of each is on hand at each stock location, with every change recorded as a
traceable stock movement.

**Why this priority**: Inventory is the foundation — sales consume it and BOMs are built from
it. Nothing else in this feature works without item and stock tracking. It delivers standalone
value: a usable inventory ledger.

**Independent Test**: Create items, record receipts/issues/adjustments at a location, and query
on-hand quantity; confirm the quantity reflects the sum of movements and that every change is
recorded with reason, quantity, timestamp, and reference.

**Acceptance Scenarios**:

1. **Given** a new item, **When** it is created with a unit of measure and type, **Then** it is
   retrievable and starts at zero on-hand.
2. **Given** an item at a location, **When** a stock movement (receipt/issue/adjustment) is
   recorded, **Then** on-hand quantity changes by the movement amount and the movement is
   persisted with its reason and timestamp.
3. **Given** an item with on-hand quantity, **When** an issue would drive quantity below zero,
   **Then** the operation is rejected (no negative stock) with a clear message.
4. **Given** many recorded movements, **When** the movement history for an item is requested,
   **Then** it returns a complete, time-ordered audit trail.

---

### User Story 2 - Record sales orders that consume inventory (Priority: P1)

A sales operator creates a sales order for a customer with one or more line items and
quantities; the system reserves available stock, and on fulfillment decrements stock — while
preventing overselling.

**Why this priority**: Sales is the primary revenue-generating flow and the main consumer of
inventory. It builds directly on inventory (US1) and delivers the first end-to-end business
outcome.

**Independent Test**: Create a sales order referencing a customer and in-stock items; confirm
stock is reserved on confirmation, that available quantity drops, that fulfillment decrements
on-hand, and that ordering more than available is refused.

**Acceptance Scenarios**:

1. **Given** items with available stock, **When** a sales order is confirmed, **Then** the
   ordered quantities are reserved and reflected as a reduction in available (not yet on-hand).
2. **Given** a confirmed order, **When** it is fulfilled/shipped, **Then** on-hand quantity is
   decremented, the reservation is released, and the order status advances.
3. **Given** an order line quantity exceeding available stock, **When** confirmation is
   attempted, **Then** it is rejected (or back-ordered per policy) with a clear message.
4. **Given** a customer reference, **When** an order is created, **Then** the order is linked to
   that customer (validated against the party source of truth).
5. **Given** a confirmed order, **When** it is cancelled, **Then** reservations are released and
   stock availability is restored.

---

### User Story 3 - Define bills of materials (Priority: P2)

A production/engineering operator defines the component structure of a manufactured item — the
components and quantities required to build one unit — supporting multi-level structures, and
can view the full exploded component list for a finished item.

**Why this priority**: BOMs enable manufacturing, accurate costing, and component availability
checks. They depend on the item catalog (US1) but are not required for basic sales/inventory.

**Independent Test**: Define a BOM for a finished item referencing component items and
quantities; request the exploded structure and confirm it lists all components (including
sub-assemblies) with correct required quantities.

**Acceptance Scenarios**:

1. **Given** existing items, **When** a BOM is defined for a finished item with component items
   and per-unit quantities, **Then** it is retrievable as the item's structure.
2. **Given** a multi-level BOM (a component is itself manufactured), **When** the structure is
   exploded, **Then** all levels are expanded with correctly multiplied quantities.
3. **Given** a BOM, **When** a component would create a cycle (item contains itself), **Then**
   the definition is rejected with a clear message.
4. **Given** a finished item and a target build quantity, **When** component requirements are
   requested, **Then** the system returns the total quantity of each component needed.

---

### User Story 4 - Replenish inventory from suppliers (Priority: P2)

An operator records incoming stock received from a supplier (a goods receipt), increasing
on-hand quantity and linking the receipt to the supplier for traceability.

**Why this priority**: Inbound stock is how inventory is replenished; it closes the loop with
sales consumption. It depends on inventory (US1) and references supplier party data.

**Independent Test**: Record a goods receipt for a supplier with items and quantities; confirm
on-hand increases, a receipt movement is recorded, and the receipt is linked to the supplier.

**Acceptance Scenarios**:

1. **Given** a supplier reference and items, **When** a goods receipt is recorded, **Then**
   on-hand quantity for each item increases and a receipt-type stock movement is persisted.
2. **Given** a recorded receipt, **When** the item's movement history is viewed, **Then** the
   receipt appears with its supplier reference and quantities.

---

### User Story 5 - Stock availability and movement data for other domains (Priority: P3)

Other parts of KITA (costing/accounting, reporting, the frontend) can query current stock
availability (on hand, reserved, available) per item/location and retrieve stock-movement data
needed for valuation.

**Why this priority**: Makes the domain useful to the rest of the system (especially
costing/accounting) but depends on the operational flows (US1–US4) producing the data.

**Independent Test**: Query availability for an item across locations and retrieve its movement
history; confirm the numbers reconcile (on hand = Σ movements; available = on hand − reserved).

**Acceptance Scenarios**:

1. **Given** items with movements and reservations, **When** availability is queried, **Then**
   it returns on-hand, reserved, and available quantities that reconcile with the movements.
2. **Given** a reporting/costing consumer, **When** movement data for a period is requested,
   **Then** it returns the movements needed to value inventory for that period.

---

### Edge Cases

- Concurrent sales orders competing for the last available unit: reservations must be
  consistent — the system must not reserve the same unit twice (no oversell under concurrency).
- Adjustments that would make on-hand negative: rejected; corrections use explicit adjustment
  movements with a reason.
- A referenced customer/supplier does not exist (or is inactive) in the party source of truth:
  the operation must fail validation with a clear message rather than create an orphaned link.
- A BOM references an item that is later deactivated: the system must surface the impact rather
  than silently break the structure.
- Deleting/deactivating an item that has stock on hand or appears in a BOM or open order: must
  be prevented or handled explicitly (no dangling references).
- Money and quantity precision: monetary amounts and unit quantities must use exact decimal
  representations (no floating-point rounding errors).
- Unit-of-measure mismatches between BOM components, stock, and orders: must be validated.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow creating and maintaining inventory items with at least a
  unique identifier/SKU, name, unit of measure, and type (e.g., stocked material, component,
  finished good).
- **FR-002**: The system MUST track on-hand quantity per item per stock location and MUST derive
  it from an auditable series of stock movements.
- **FR-003**: The system MUST record every stock change as a movement (receipt, issue,
  adjustment, transfer) with quantity, reason, timestamp, and a reference to its source
  document (order, receipt, adjustment).
- **FR-004**: The system MUST prevent on-hand quantity from going negative and MUST reject or
  explicitly handle operations that would do so.
- **FR-005**: The system MUST allow creating sales orders that reference a customer and one or
  more items with quantities and prices.
- **FR-006**: The system MUST reserve available stock when a sales order is confirmed and MUST
  reflect reservations in available quantity without changing on-hand until fulfillment.
- **FR-007**: The system MUST decrement on-hand quantity when a sales order is fulfilled/shipped
  and MUST release reservations on fulfillment or cancellation.
- **FR-008**: The system MUST prevent overselling — confirming/fulfilling more than available
  MUST be rejected or back-ordered per a defined policy.
- **FR-009**: The system MUST maintain sales-order status through a defined lifecycle (e.g.,
  draft → confirmed → fulfilled → closed, plus cancelled) with valid transitions enforced.
- **FR-010**: The system MUST allow defining a bill of materials for a finished item as a set of
  component items with per-unit quantities, supporting multi-level (nested) structures.
- **FR-011**: The system MUST reject BOM definitions that create a cycle (an item that directly
  or indirectly contains itself).
- **FR-012**: The system MUST compute exploded component requirements for a given finished item
  and build quantity (total quantity of each component, across all BOM levels).
- **FR-013**: The system MUST allow recording goods receipts from a supplier that increase
  on-hand quantity and are linked to the supplier for traceability.
- **FR-014**: The system MUST validate customer and supplier references against the party source
  of truth and MUST reject operations that reference a non-existent or inactive party.
- **FR-015**: The system MUST expose current availability (on-hand, reserved, available) per item
  and per location on request.
- **FR-016**: The system MUST expose stock-movement data sufficient for the costing/accounting
  domain to value inventory for a period.
- **FR-017**: Monetary amounts and quantities MUST be stored and computed using exact decimal
  representations; unit-of-measure consistency MUST be validated across items, BOMs, and orders.
- **FR-018**: Stock-affecting operations MUST be atomic and consistent under concurrency — the
  same available stock MUST NOT be reserved or consumed twice.
- **FR-019**: The system MUST enforce referential integrity so items in use (with stock, in a
  BOM, or on an open order) cannot be removed without explicit handling.
- **FR-020**: The system MUST provide its capabilities through a documented, versioned API
  contract consumable by the frontend and other services (per the project's contract-first
  convention).
- **FR-021**: The system MUST record an auditable history of significant business events
  (orders confirmed/fulfilled/cancelled, receipts, adjustments, BOM changes).

### Key Entities *(include if feature involves data)*

- **Item**: An inventory item (product, component, or raw material) — identifier/SKU, name, unit
  of measure, type; the thing that is stocked, sold, and assembled.
- **Stock Location**: A place stock is held (warehouse/bin); items have quantities per location.
- **Stock Level**: The current on-hand, reserved, and available quantities for an item at a
  location (derived from movements and reservations).
- **Stock Movement**: An immutable record of a stock change — type (receipt/issue/adjustment/
  transfer), item, location, quantity, reason, timestamp, source reference. The audit trail.
- **Sales Order** / **Sales Order Line**: A customer's order and its lines (item, quantity,
  price); has a status lifecycle and drives reservations and fulfillment.
- **Bill of Materials** / **BOM Component**: A finished item's component structure — parent item,
  component items, per-unit quantities; supports nesting and (optionally) versioning.
- **Goods Receipt** / **Receipt Line**: Inbound stock from a supplier that increases on-hand.
- **Reservation**: A hold placed on available stock by a confirmed sales order until fulfillment.
- **Party Reference (Customer / Supplier)**: An identifier referencing a customer or supplier
  owned by the separate Party master-data service (not stored in full here — see Overview).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On-hand quantity for any item always equals the sum of its recorded stock
  movements — reconciliation holds 100% of the time.
- **SC-002**: Under concurrent orders for the same item, available stock is never reserved or
  consumed beyond what exists (zero oversell), verified under a concurrency test.
- **SC-003**: A sales order can be taken from creation through reservation and fulfillment, with
  stock quantities correct at each step, with no manual data fix-ups.
- **SC-004**: Exploding a multi-level BOM returns component quantities that match a hand-computed
  expectation for a representative structure.
- **SC-005**: Any operation referencing a non-existent or inactive customer/supplier is rejected
  100% of the time (no orphaned party links created).
- **SC-006**: Availability queries (on-hand/reserved/available) reconcile with movements and
  reservations for every item, verified by inspection.
- **SC-007**: All monetary and quantity computations are exact (no floating-point rounding
  discrepancies) across a representative set of transactions.
- **SC-008**: The costing/accounting domain can retrieve the movement data it needs to value
  inventory for a period without additional manual data extraction.

## Assumptions

- **Party service is separate (confirmed)**: Customer and supplier profiles are owned by a
  distinct Party master-data service; this domain references them by ID and validates against
  them. It becomes its own feature.
- **Domain scope**: This feature covers Inventory, BOM, and Sales, plus the supplier-side goods
  receipt needed to replenish stock. Full purchasing/procurement workflow, manufacturing/work
  orders, pricing/discount engines, tax, and payments are out of scope (future features).
- **Architecture fit (confirmed)**: Built as **one combined "operations" service** on the
  feature-002 scaffold, behind the gateway, with internal modules for Inventory, BOM, and Sales
  sharing one PostgreSQL schema and the item catalog (Flyway migrations, OpenAPI contract). Stock
  reservations use in-process transactions for strong consistency. It is one bounded-context
  service in the mesh alongside the separate Party service and a future Accounting service.
- **Costing/accounting** consumes this domain's data but is a separate feature; this feature only
  exposes the data it needs.
- **Authentication/authorization** is handled at the gateway/auth layer (separate feature); this
  spec assumes requests are already authenticated.
- **Back-order policy**: Default is to reject over-available confirmations; back-ordering can be
  enabled later (documented as a policy point).

## Dependencies

- A **Party (customer/supplier) master-data service** as the source of truth for party
  references (recommended separate feature).
- The multi-service scaffold (feature 002): gateway, service structure, OpenAPI contract,
  PostgreSQL + Flyway conventions.
- The costing/accounting domain (future) as the primary downstream consumer of movement data.

## Out of Scope

- Customer and supplier profile management itself (recommended to live in a separate Party
  service — this feature only references parties).
- Costing, valuation math, and accounting/ledger postings (separate feature; this domain exposes
  the source data).
- Purchasing/procurement approval workflow beyond the goods receipt that replenishes stock.
- Manufacturing/work-order execution (BOM here defines structure; it does not run production).
- Pricing/discount/tax engines, payments, and shipping/carrier integration.
- Frontend/UI (a later feature consumes this service's contract).
- Authentication/authorization implementation.
