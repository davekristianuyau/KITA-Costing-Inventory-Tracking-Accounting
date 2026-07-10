# Feature Specification: Sales, Inventory, and Bill-of-Materials Backend Service

**Feature Branch**: `003-sales-inventory-bom`
**Created**: 2026-07-08
**Status**: Implemented (all user stories; tested against real PostgreSQL)
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
- Q: Inventory location model? → A: **Multiple locations with simple transfers** — stock is
  keyed by item × location from the start; a transfer is a paired issue/receipt movement.
  Advanced routing/putaway/picking rules are out of scope for now.
- Q: Lot/batch and serial tracking? → A: **Lot/batch tracking** — stock and movements carry a
  lot/batch identifier (with optional expiry); no per-unit serial tracking.
- Q: How do BOMs behave (from user examples)? → A: BOMs drive consumption in **two modes**:
  (1) **Kit / recipe (phantom)** — the parent is assembled on demand and NOT stocked; selling or
  ordering it deducts its components (electrical set; restaurant tapsilog at POS). (2)
  **Manufactured / assembled** — a production/assembly action consumes raw-material components
  and produces a stocked finished good (clothing: cloth + thread → dresses), adjusting both
  sides in one atomic operation. **Unit-of-measure conversions** are required (e.g., kg↔g,
  tray↔pcs, meters). Item **cost and profit % roll up from raw-material/component costs**
  (valuation method resolved below).
- Q: Inventory costing / valuation method? → A: **Per-item configurable.** Default **Weighted
  Average Cost (AVCO)**; **FIFO with earliest-expiry-first (FEFO)** consumption for
  perishable/expiry-tracked items so expiring lots are consumed and costed first. Lot tracking
  underpins FEFO.

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

### User Story 6 - Sell a kit/recipe that deducts components (Priority: P1)

An operator sells a kit or recipe item (a "phantom" BOM that is not stocked as a finished good)
— for example an electrical set (enclosure + 4 breakers) or a restaurant dish (tapsilog) — and
the system deducts the underlying **component** stock, not a finished-good count, at the point of
sale.

**Why this priority**: This is a core selling pattern for the target businesses (bundles, POS
recipes) and directly affects how sales consume inventory. It depends on inventory (US1), sales
(US2), and BOM definitions (US3).

**Independent Test**: Define a kit/recipe BOM; sell one unit of the kit; confirm each component's
stock is deducted by its per-unit quantity (unit-of-measure-converted) and that no finished-good
stock is required or created.

**Acceptance Scenarios**:

1. **Given** a kit/recipe item with a component BOM, **When** one unit is sold/ordered, **Then**
   each component's on-hand is reduced by (kit qty × component per-unit qty), converted to the
   component's stock unit of measure.
2. **Given** a recipe requiring 250g rice, 200g tapa, and 1 egg, **When** a tapsilog is sold,
   **Then** exactly those quantities are deducted from raw-ingredient stock (kg↔g, tray↔pcs
   conversions applied), with no "tapsilog" stock item maintained.
3. **Given** insufficient stock of any component, **When** the kit/recipe sale is attempted,
   **Then** it is rejected (or back-ordered per policy) identifying the short component.

---

### User Story 7 - Produce/assemble finished goods from raw materials (Priority: P2)

An operator records a production/assembly action that consumes raw-material components per a
manufactured BOM and adds the finished goods to stock — for example, sewing 8.5m of cloth plus
thread into 5 dresses — adjusting both sides in one atomic operation.

**Why this priority**: This is how stocked finished goods come into existence for manufacturers;
it closes the make-to-stock loop and depends on inventory (US1) and manufactured BOMs (US3).

**Independent Test**: Define a manufactured BOM for a finished item; run a build for a target
quantity; confirm components are deducted by their required totals and the finished-good on-hand
increases by the produced quantity, all in one transaction.

**Acceptance Scenarios**:

1. **Given** a manufactured BOM and sufficient component stock, **When** a build of N finished
   units is recorded, **Then** each component's on-hand decreases by its exploded requirement and
   the finished good's on-hand increases by N, atomically.
2. **Given** insufficient stock of any component, **When** a build is attempted, **Then** it is
   rejected identifying the short component; no partial consumption occurs.
3. **Given** a completed build, **When** movement history is viewed, **Then** the component
   issues and the finished-good receipt are recorded and linked to the build.

---

### User Story 8 - Roll up cost and margin from raw materials (Priority: P2)

An operator can see the computed cost of a finished/kit item derived from its component
(raw-material) costs, and the profit margin/percentage against its sale price.

**Why this priority**: The business explicitly needs costing and profit % based on the raw
materials consumed. It depends on item costs, BOM structure (US3), and movements carrying cost.

**Independent Test**: Assign costs to raw materials, define a BOM, and request the finished/kit
item's rolled-up cost and its margin vs sale price; confirm the numbers match a hand calculation
using the chosen valuation method.

**Acceptance Scenarios**:

1. **Given** components with known unit costs and a BOM, **When** the finished/kit item's cost is
   requested, **Then** it equals the sum of (component qty × component unit cost) across the BOM
   (multi-level), using the configured valuation method.
2. **Given** a finished/kit item cost and a sale price, **When** margin is requested, **Then** the
   system returns profit amount and profit percentage.
3. **Given** raw-material costs change, **When** cost is recomputed, **Then** the finished/kit
   item cost reflects the updated component costs per the valuation method.

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
- Unit-of-measure mismatches between BOM components, stock, and orders: must be validated and
  converted, never silently assumed (e.g., grams vs kilograms, pieces vs trays).
- Which lot is consumed when multiple lots exist (e.g., earliest-expiry-first): the selection
  rule must be defined and applied consistently; expired lots must not be silently consumed.
- A kit/recipe sale or a production build where one component is short: the whole operation is
  rejected (no partial deduction), naming the short component.

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
- **FR-003a**: The system MUST support multiple stock locations and MUST support transferring
  stock between two locations as a paired issue (source) and receipt (destination) that
  conserves total quantity. Advanced routing/putaway/picking rules are out of scope.
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
  (orders confirmed/fulfilled/cancelled, receipts, adjustments, BOM changes, builds).
- **FR-022**: The system MUST support units of measure with conversions (e.g., kg↔g, a tray of
  30↔pieces, meters) and MUST apply conversions consistently across purchasing/receipt, stock,
  BOM component quantities, and sales/consumption.
- **FR-023**: The system MUST track stock and every movement by lot/batch identifier, with an
  optional expiry attribute, so component consumption and finished goods are traceable to lots.
- **FR-024**: The system MUST support two BOM types: **kit/recipe (phantom)** — the parent is
  not stocked and selling/ordering it consumes its components — and **manufactured** — the parent
  is stocked and produced by a build that consumes components.
- **FR-025**: When a kit/recipe item is sold or ordered, the system MUST deduct each component's
  stock by (parent quantity × component per-unit quantity, unit-converted) rather than a
  finished-good count, and MUST validate component availability (reject/back-order if short).
- **FR-026**: The system MUST support a production/assembly "build" that, in one atomic
  operation, consumes the manufactured BOM's exploded component requirements and increases the
  finished good's on-hand by the produced quantity; a build MUST fail wholesale if any component
  is short (no partial consumption).
- **FR-027**: Each stock movement MUST carry a unit cost, so inventory can be valued and finished
  goods can inherit component costs.
- **FR-028**: The system MUST compute a finished/kit item's rolled-up cost from its components'
  costs across all BOM levels, using the configured valuation method, and MUST expose that cost.
- **FR-029**: The system MUST compute profit amount and profit percentage for a finished/kit item
  as (sale price − rolled-up cost) and ((sale price − cost) / sale price), using exact decimals.
- **FR-030**: The valuation method MUST be configurable per item (or item category): **Weighted
  Average Cost (AVCO)** by default, and **FIFO** for perishable/expiry-tracked items; the chosen
  method MUST be applied uniformly to that item's stock valuation and cost roll-up.
- **FR-031**: For perishable/expiry-tracked (FIFO) items, stock consumption MUST follow
  earliest-expiry-first (FEFO) lot selection, and the consumed lot's cost MUST be used for
  valuation; expired lots MUST NOT be consumed silently.

### Key Entities *(include if feature involves data)*

- **Item**: An inventory item (product, component, or raw material) — identifier/SKU, name, base
  unit of measure, type (raw material/component, stocked finished good, or kit/recipe), and unit
  cost; the thing that is stocked, sold, and assembled.
- **Unit of Measure / Conversion**: A measure (kg, g, piece, tray, meter) and the conversion
  factors between related units, applied across receipts, stock, BOM, and sales.
- **Lot / Batch**: An identifiable batch of an item (with optional expiry) that stock quantities
  and movements are tracked against.
- **Production Build**: A record of consuming a manufactured BOM's components and producing a
  quantity of the finished good, atomically; links the component issues and the finished receipt.
- **Item Cost / Valuation**: The unit cost of an item and the rolled-up cost of a finished/kit
  item computed from its components, used for inventory valuation and margin.
- **Stock Location**: A place stock is held (warehouse/bin); items have quantities per location.
- **Stock Level**: The current on-hand, reserved, and available quantities for an item at a
  location (derived from movements and reservations).
- **Stock Movement**: An immutable record of a stock change — type (receipt/issue/adjustment/
  transfer), item, location, quantity, reason, timestamp, source reference. The audit trail.
- **Sales Order** / **Sales Order Line**: A customer's order and its lines (item, quantity,
  price); has a status lifecycle and drives reservations and fulfillment.
- **Bill of Materials** / **BOM Component**: A parent item's component structure — parent item,
  component items, per-unit quantities (in the component's unit of measure); typed as
  kit/recipe (phantom, not stocked) or manufactured (stocked, produced by a build); supports
  nesting and (optionally) versioning.
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
- **SC-009**: Selling one unit of a kit/recipe deducts exactly the unit-converted component
  quantities (verified against a hand calculation for the electrical-set and tapsilog examples),
  with no finished-good stock item involved.
- **SC-010**: A production build consumes the exact exploded component quantities and increases
  finished-good stock by the produced amount, atomically — verified against the dresses example
  (8.5m cloth + thread → 5 dresses) with no partial consumption on failure.
- **SC-011**: A finished/kit item's rolled-up cost equals the hand-computed sum of component
  costs, and its profit % matches (sale price − cost) / sale price exactly, for a representative
  multi-level item.

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
- **Valuation (confirmed)**: Per-item method — Weighted Average (AVCO) default; FIFO + FEFO for
  perishable/expiry-tracked items. Applied to both stock valuation and BOM cost roll-up.
- **Reservation model (default, not asked)**: A confirmed sales order places a hard reservation
  on available stock; partial fulfillment/shipment is allowed (remaining stays reserved).
  Revisit during planning if needed.
- **BOM versioning (default, not asked)**: A single active BOM per parent item for now; versioned
  BOMs with effectivity dates are deferred to a later enhancement.
- **Production build scope**: A basic build (consume components → produce finished stock) is in
  scope; shop-floor scheduling, routings, and operations are not.

## Dependencies

- A **Party (customer/supplier) master-data service** as the source of truth for party
  references (recommended separate feature).
- The multi-service scaffold (feature 002): gateway, service structure, OpenAPI contract,
  PostgreSQL + Flyway conventions.
- The costing/accounting domain (future) as the primary downstream consumer of movement data.

## Out of Scope

- Customer and supplier profile management itself (recommended to live in a separate Party
  service — this feature only references parties).
- Financial ledger/journal postings, financial statements, and tax (separate accounting feature).
  NOTE: inventory valuation and BOM **cost roll-up + margin** ARE in scope here (they drive the
  raw-material-based costing the business needs); the accounting feature consumes these.
- Purchasing/procurement approval workflow beyond the goods receipt that replenishes stock.
- Advanced shop-floor manufacturing (work-order scheduling, routings, capacity, operations).
  NOTE: a basic **production/assembly build** (consume components → produce finished stock) IS in
  scope here (User Story 7).
- Pricing/discount/tax engines, payments, and shipping/carrier integration.
- Frontend/UI (a later feature consumes this service's contract).
- Authentication/authorization implementation.
