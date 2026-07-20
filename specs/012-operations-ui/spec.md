# Feature Specification: Operations Service UI

**Feature Branch**: `012-operations-ui`
**Created**: 2026-07-20
**Status**: Draft
**Input**: User description: "Operations service full UI — the per-service front-end that fills the 011 console workspace framework's manifest for operations-service (catalog, inventory, BOM, production, sales, costing), read-first then create/build/post actions; evolves the operations manifest + result views, no backend code."

## Overview

The first **full per-service UI** built on the 011 console foundation. The Operations tab already exists (011
shipped one reference function); this feature grows it into the complete Operations workspace so a client can
run the day-to-day operations of `operations-service` (catalog, inventory, bills of material, production,
sales, and costing) from the browser. Every function is an entry in the Operations **manifest** rendered by the
011 workspace framework and called through the 009 edge. It is **mostly frontend**, with a **bounded backend
addition**: `operations-service` (spec 003) exposes most capabilities already, but several resources are
currently **write-only** (create returns the object, but there is no way to list or re-fetch it). This feature
**adds the missing read (GET) endpoints** for those resources so the UI can list/view them and so actions are
verifiable — closing the gap now rather than leaving future errors. No other backend behavior changes.

## Clarifications

### Session 2026-07-20

- Q: How should 012 verify write actions on write-only resources (sales orders, builds, goods receipts have no
  GET) under the "no backend changes" constraint? → A: **Add the missing read endpoints to operations-service
  now** (option C) — don't defer it, to avoid future gaps/errors. 012's scope therefore includes a bounded set of
  backend GET endpoints (see FR-015).
- Q: How should a form field that references another record (e.g. an item) behave for large lists, so users
  don't type identifiers? → A: **Load the list once and filter client-side (type-ahead), with a sensible render
  cap** (option A) — no new backend search endpoint; the picker stays reusable across every per-service UI
  (see FR-017).
- Applied from Phase-0 backend grounding (see [research.md](./research.md), not open questions): valuation
  methods are **AVCO | FIFO** (FEFO is a perishable/lot policy, not a selectable valuation method); **BOM
  explosion returns a flat component-requirements list** (rendered as a table, not a recursive tree); an item's
  attributes come from the item record (a `GET /items/{id}` detail endpoint is added under FR-015).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse the catalog and current stock (Priority: P1) 🎯 MVP

An operations user opens the Operations tab and, without any setup, browses what the business sells and makes
and how much is on hand: a list of catalog items, an item's detail, and multi-location stock levels for an item.

**Why this priority**: Read-only visibility is the safest, highest-frequency need and proves the whole
per-service pattern end-to-end (manifest → workspace → edge → real result) with zero risk of mutating data.

**Independent Test**: Sign in → Operations → "Items" lists catalog items; selecting an item shows its detail;
"Stock on hand" for that item shows quantity per location. All read-only, returning real data via the edge.

**Acceptance Scenarios**:

1. **Given** a signed-in client, **When** they open Operations → Items, **Then** a list of catalog items renders
   (sku, name, type, base unit) with a clear loading state and a clear empty state.
2. **Given** an item, **When** they open its detail, **Then** the item's attributes are shown.
3. **Given** an item, **When** they view stock on hand, **Then** on-hand quantity per location is shown.
4. **Given** the backend returns an error, **When** a read runs, **Then** a clear, non-technical error is shown.

---

### User Story 2 - Trace inventory movements and reservations (Priority: P2)

An operations user investigates stock: the movement ledger for an item (receipts, issues, transfers, builds)
and the outstanding reservations that make on-hand differ from available.

**Why this priority**: Once you can see stock, the next question is always "why is it this number?" — the ledger
and reservations answer it. Still read-only.

**Independent Test**: Open Operations → Movements for an item → the movement ledger renders in time order;
Reservations lists open reservations with quantities.

**Acceptance Scenarios**:

1. **Given** an item with history, **When** the movement ledger is viewed, **Then** movements render in
   chronological order with type, quantity, location, and resulting balance.
2. **Given** open reservations, **When** reservations are viewed, **Then** each reservation's item, quantity, and
   location are shown, and available = on-hand − reserved is derivable.

---

### User Story 3 - Explode a bill of materials (Priority: P2)

A user selects a manufactured item and sees its BOM exploded to components with per-level and rolled-up
quantities, safely (no infinite loop on a cyclic definition).

**Why this priority**: BOM explosion is the signature manufacturing view and the input to production planning;
it is read-only and self-contained.

**Independent Test**: Open Operations → BOM explosion for a manufactured item → the flat list of component
requirements renders with quantities; a cyclic BOM surfaces a clear message instead of hanging.

**Acceptance Scenarios**:

1. **Given** a manufactured item with a BOM, **When** it is exploded, **Then** the component requirements render
   as a flat list with each component's required quantity and unit (already summed by the service).
2. **Given** a cyclic BOM, **When** explosion is attempted, **Then** a clear "cycle detected" message is shown.

---

### User Story 4 - Record operations: create, build, post, sell (Priority: P3)

A user performs the core write actions: create a catalog item (and unit of measure), post a stock movement,
run a production build, and create a sales order — each a validated run-form with a clear success/error result.

**Why this priority**: Writes deliver the most value but carry risk; they come after the read views so users can
verify effects. Each action is independently useful.

**Independent Test**: Create an item via its form → it then appears in the Items list; post a movement → the
item's stock and ledger reflect it; run a build → components consumed and output produced, and the build appears
in the builds list; create a sales order → it appears in the sales-order list with its lines and status.

**Acceptance Scenarios**:

1. **Given** the create-item form, **When** required fields are provided and submitted, **Then** the item is
   created and a success result is shown; **When** a required field is missing, **Then** the call is blocked with
   inline validation.
2. **Given** the post-movement form, **When** a valid movement is submitted, **Then** stock and the ledger update.
3. **Given** a production build form, **When** submitted, **Then** components are consumed and the output produced
   (reflected in stock), and the build is listable via its read endpoint.
4. **Given** the sales-order form, **When** submitted, **Then** the order is created and appears in the
   sales-order list with its lines and status; lifecycle actions (confirm/fulfill/cancel) update that status.

---

### User Story 5 - Costing and margin (Priority: P3)

A user views inventory valuation and product costing: an item's current cost under the configured method
(AVCO or FIFO), the rolled-up cost of a manufactured item, and its margin vs. price.

**Why this priority**: Costing depends on catalog, stock, and BOM being visible first; it is the analytical
payoff and is read-only.

**Independent Test**: Open Operations → Costing for an item → current unit cost renders; for a manufactured item,
the cost roll-up and margin render.

**Acceptance Scenarios**:

1. **Given** an item, **When** its cost is viewed, **Then** the current unit cost and valuation method are shown.
2. **Given** a manufactured item, **When** cost roll-up is viewed, **Then** the summed component cost and the
   resulting margin against selling price are shown.

### Edge Cases

- A function returns an empty list → a clear empty state (not a blank pane or an error).
- A read/write returns 4xx/5xx via the edge → a clear, non-revealing error with the failing action's context.
- Required inputs missing → inline validation blocks the call before it reaches the edge.
- A cyclic BOM → a "cycle detected" message from the explosion view.
- Large lists → the workspace remains responsive; record-reference pickers load once and filter client-side
  (type-ahead) with a sensible render cap (FR-017).
- A write the signed-in client is not permitted to perform → a clear "not allowed" result, not a crash.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Operations tab MUST present its functions grouped by area (Catalog, Inventory, BOM, Production,
  Sales, Costing) in the 011 left pane.
- **FR-002**: Users MUST be able to list catalog items and view an item's detail.
- **FR-003**: Users MUST be able to view multi-location on-hand stock for an item.
- **FR-004**: Users MUST be able to view an item's movement ledger and its open reservations.
- **FR-005**: Users MUST be able to explode a manufactured item's BOM, with cyclic definitions surfaced as a
  clear message rather than a failure/hang.
- **FR-006**: Users MUST be able to create a catalog item and a unit of measure via validated forms.
- **FR-007**: Users MUST be able to post a stock movement (receipt/issue/transfer) via a validated form.
- **FR-008**: Users MUST be able to run a production build via a validated form.
- **FR-009**: Users MUST be able to create a sales order (with lines) via a validated form.
- **FR-010**: Users MUST be able to view an item's current cost/valuation, a manufactured item's cost roll-up,
  and its margin.
- **FR-011**: Every function MUST show explicit loading, empty, result, and error states (reusing the 011
  framework), with results rendered in the shape best suited to the data (list/table, detail, or message).
- **FR-012**: All calls MUST go through the 009 edge with the signed-in client's session; the UI MUST NOT bypass
  the edge or embed credentials.
- **FR-013**: Write actions MUST block on missing required inputs with inline validation before calling the edge.
- **FR-014**: The feature MUST NOT change any existing `operations-service` endpoint or its write/business
  behavior; the only backend change permitted is adding the **read-only** endpoints in FR-015.
- **FR-015**: `operations-service` MUST gain read (GET) endpoints so today's write-only resources become
  listable/viewable: a single item (`GET /items/{id}`); list locations; **list + get** sales orders, production
  builds, and goods receipts. These endpoints MUST be read-only, tenant-scoped consistently with existing
  endpoints, and covered by tests (unit/contract) per the constitution's TDD requirement.
- **FR-016**: Users MUST be able to list and view sales orders, production builds, and goods receipts in the UI
  (enabled by FR-015), and the effect of each create/lifecycle action MUST be verifiable via these reads (in
  addition to Stock on hand / Movement ledger for stock effects).
- **FR-017**: Where a form field references another record (e.g. an item), the UI MUST present a **searchable
  picker** — the referenced list loads once, filters as the user types (type-ahead), and caps the rendered
  options sensibly — rather than requiring the user to enter a raw identifier. (No server-side search endpoint is
  added.)

### Key Entities *(include if feature involves data)*

- **Catalog Item**: a sellable/manufacturable good — sku, name, type, base unit of measure.
- **Unit of Measure**: how an item is counted/measured (family, code).
- **Location**: a place stock is held; stock is per item per location.
- **Stock Level**: on-hand and available (on-hand − reserved) quantity for an item at a location.
- **Movement**: a ledger entry changing stock (receipt/issue/transfer/build) with type, quantity, location, time.
- **Reservation**: a hold on stock reducing availability without removing on-hand.
- **BOM**: the component structure of a manufactured item; explosion yields a flat list of component
  requirements (already summed) with quantities and units.
- **Production Build**: consumes components and produces output, posting movements; has a status; listable via
  its read endpoint (FR-015).
- **Sales Order**: a customer order with a status and lines referencing items and quantities; listable via its
  read endpoint (FR-015).
- **Goods Receipt**: received goods against a supplier at a location; listable via its read endpoint (FR-015).
- **Cost/Valuation**: an item's unit cost under a method (AVCO or FIFO), roll-up for manufactured items, margin.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From the Operations tab, a user can find an item and see its current on-hand stock across locations
  in **under 30 seconds**, with no page reloads between functions.
- **SC-002**: **100%** of Operations functions show a distinct loading, empty, result, and error state (no blank
  or spinning-forever panes).
- **SC-003**: A user can create a catalog item and then see it in the Items list **without leaving the console**.
- **SC-004**: Exploding a cyclic BOM returns a clear message in **under 2 seconds** and never hangs the workspace.
- **SC-005**: **0** service calls bypass the edge or expose credentials in the browser (session-cookie only).
- **SC-006**: The full Operations workspace is usable at the 011 responsive floor (down to **768px**) and fully
  keyboard-navigable.

## Assumptions

- `operations-service` (spec 003) exposes most read and all write capabilities above via the edge under
  `/api/operations`; this feature maps them into manifest functions and **adds only the read endpoints in
  FR-015** for the currently write-only resources.
- In the local simulation every client can reach all services (011 assumption A1); real entitlement filtering is
  out of scope here.
- Result rendering reuses the 011 framework's shapes (table/detail/message); Operations-specific presentation
  helpers (e.g. resolving item ids to SKU/name) may be added where a generic shape is insufficient.
- Selling price used for margin comes from the item/sales data already in `operations-service`.

## Dependencies

- **011 service-console-ui** — login, one-tab-per-service shell, the workspace framework, the per-service
  manifest seam, and the generic authenticated edge fetch.
- **009 client-login-deploy-sim** — the edge/session the calls route through.
- **003 sales-inventory-bom** — the `operations-service` backend being surfaced.

## Out of Scope

- Backend changes to `operations-service` **beyond** the read-only endpoints in FR-015 (no change to existing
  endpoints or write/business logic; no new write endpoints).
- Other services' UIs (HR, CRM, Procurement, Workflow) — their own specs.
- Real cloud deployment; role/entitlement management; bulk import/export and reporting/analytics dashboards.
