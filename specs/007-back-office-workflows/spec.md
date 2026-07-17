# Feature Specification: Back-Office Workflow Service

**Feature Branch**: `007-back-office-workflows`
**Created**: 2026-07-16
**Status**: Draft
**Input**: User description: "the service that connects sales, inventory, BOM, HR, customer and
supplier — the connection where employees create sales orders, purchase orders, build products from
raw materials, add/create/modify customer and supplier details; also receiving of deliveries/stocks,
or purchase goods needed for the business"

## Overview

`workflow-service` is KITA's **back-office workflow layer** — the place a staff member goes to *do
the work of running the business*: take a customer's order, order goods from a supplier, receive a
delivery into stock, build finished products from raw materials, and keep customer and supplier
records up to date.

It is the connective tissue between the existing domain services (sales/inventory/BOM/production,
HR, customers, suppliers/purchasing). It does **not** re-own their data. Its job is to (1) act on
behalf of a specific, authorized **employee**, (2) attribute every action to that employee, and (3)
compose actions that span more than one domain service into a single, all-or-nothing business
action. Today those domain services have no shared actor — inventory and production record changes
with no "who" at all — so this service is what makes back-office activity attributable and governed.

## Clarifications

### Session 2026-07-16
- Q: Does this service own business data, or only orchestrate? → A: **Orchestrate.** It persists only
  its own back-office activity log and its authorization mapping; customers, suppliers, employees,
  inventory, orders and purchases stay owned by their services and are referenced, never duplicated.
- Q: Who is the actor? → A: An **employee**, validated as existing and active against the HR source of
  record; the gateway establishes identity as it does for the other services.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Act as an authorized employee (Priority: P1)

Every back-office action is performed by a named staff member. Before any action proceeds, the
service confirms the person is a valid, active employee and that their role permits that action, and
it records the action against them. A separated employee, or one lacking the required role, cannot
act.

**Why this priority**: This is the reason the service exists. The four domain services already expose
their capabilities, but with no shared, verified actor and no cross-domain authorization. Attribution
and authorization are the foundation every other story builds on.

**Independent Test**: Present an action as employee A (active, authorized) and see it recorded against
A; present the same action as an unknown employee, a separated employee, and an employee without the
required role, and see each rejected with a clear reason and no side effect.

**Acceptance Scenarios**:

1. **Given** an active employee whose role permits the action, **When** they perform a back-office
   action, **Then** it proceeds and is recorded with the employee and a timestamp.
2. **Given** an employee who does not exist or is separated, **When** an action is attempted on their
   behalf, **Then** it is rejected and nothing is changed in any domain service.
3. **Given** an active employee whose role does not permit the action, **When** they attempt it,
   **Then** it is rejected with a clear "not permitted" reason and no side effect.

---

### User Story 2 - Take a customer sales order (Priority: P1) 🎯 MVP

A sales staff member takes an order from a customer: they pick the customer, add line items (product,
quantity, price), and confirm the order so stock is reserved. The order is attributed to them.

**Why this priority**: Selling to customers is the primary revenue action of the business, and
together with US1 it is the smallest thing that delivers real value — an authorized employee closing
a real order.

**Independent Test**: As an authorized sales employee, create an order for a valid customer with two
lines and confirm it; verify the order exists with the correct lines and reserved stock, is
attributed to the employee, and that an order for an unknown customer is rejected.

**Acceptance Scenarios**:

1. **Given** a valid customer and available stock, **When** an authorized employee creates and
   confirms an order, **Then** the order is recorded, stock is reserved, and the action is attributed
   to the employee.
2. **Given** an unknown or inactive customer, **When** an employee tries to create an order for them,
   **Then** the action is rejected and no order is created.
3. **Given** an order line exceeding available stock, **When** the employee confirms, **Then** the
   overselling is prevented and the employee is told why.

---

### User Story 3 - Buy goods from a supplier (Priority: P1)

A purchasing staff member raises a purchase order to buy goods the business needs: they pick the
supplier, add line items, and submit the order through its approval and sending lifecycle. The order
is attributed to them.

**Why this priority**: Replenishing and procuring goods is the other half of day-to-day trading and,
with receiving (US4), keeps the business stocked. It is P1 because a business cannot operate without
being able to buy.

**Independent Test**: As an authorized purchasing employee, raise a PO for a valid supplier, carry it
through approval and sending, and verify it is attributed to the employee; confirm a PO for an unknown
supplier is rejected and that an over-threshold order requires the authorized approver.

**Acceptance Scenarios**:

1. **Given** a valid supplier, **When** an authorized employee raises a purchase order with lines,
   **Then** the order is recorded against the employee with computed totals.
2. **Given** an order whose total exceeds the approval threshold, **When** an employee without
   approval authority tries to approve it, **Then** it is refused pending an authorized approver.
3. **Given** an unknown or inactive supplier, **When** an employee tries to raise a PO, **Then** it is
   rejected.

---

### User Story 4 - Receive a delivery into stock (Priority: P2)

A warehouse staff member records a delivery that has arrived against a purchase order. In one action,
the purchase record advances (partially/fully received) and the received quantities increase
inventory on hand. The receipt is attributed to them.

**Why this priority**: This is the clearest cross-service workflow — it must touch both purchasing and
inventory, and get them consistent — so it is the strongest demonstration of the service's purpose.
It comes after US3 because there must be a sent order to receive against.

**Independent Test**: With a sent PO, record a partial then a full receipt as an authorized employee;
verify the purchase order advances to partially- then fully-received, inventory on hand increases by
exactly the received quantities, over-receipt is refused, and both effects either happen together or
not at all.

**Acceptance Scenarios**:

1. **Given** a sent purchase order, **When** an authorized employee records a full receipt, **Then**
   the order becomes fully received and inventory on hand increases by the received quantities, as one
   action attributed to the employee.
2. **Given** a partial delivery, **When** it is received, **Then** the order shows the outstanding
   remainder and inventory reflects only what arrived.
3. **Given** a receipt exceeding the ordered quantity, **When** submitted, **Then** the over-receipt is
   refused and neither the purchase record nor inventory changes.
4. **Given** the inventory update cannot be completed, **When** a receipt is recorded, **Then** the
   whole receipt is rejected and the purchase record is left unchanged (no half-applied delivery).

---

### User Story 5 - Build finished products from raw materials (Priority: P2)

A production staff member triggers a build of a finished/assembled item. The build consumes the raw
materials/components defined by the item's bill of materials and increases finished-goods stock, as
one action attributed to them.

**Why this priority**: Manufacturing/assembly turns purchased materials into sellable goods and closes
the loop between purchasing and sales. It is P2 because trading (buy/sell/receive) can operate before
in-house production is enabled.

**Independent Test**: With components in stock and a defined bill of materials, trigger a build of a
finished item as an authorized employee; verify the exact component quantities are consumed and
finished stock increases, and that a build with insufficient components is rejected without consuming
anything.

**Acceptance Scenarios**:

1. **Given** sufficient components and a defined bill of materials, **When** an authorized employee
   builds N units, **Then** the exploded component quantities are consumed and finished stock rises by
   N, attributed to the employee.
2. **Given** insufficient components, **When** a build is attempted, **Then** it is rejected and no
   stock is consumed.

---

### User Story 6 - Maintain customer and supplier records (Priority: P2)

A staff member creates or updates a customer or a supplier — contact details, terms, and the items a
supplier supplies — so those records are ready to use in orders and purchases. Changes are attributed
to them.

**Why this priority**: Accurate party records underpin every order and purchase, but a business can
start with records seeded directly in the domain services, so this enriches rather than blocks the
trading workflows.

**Independent Test**: As an authorized employee, create a customer and a supplier and update each;
verify the changes are attributed to the employee and that the new records can immediately be used in
an order and a PO within the same session.

**Acceptance Scenarios**:

1. **Given** an authorized employee, **When** they create or update a customer, **Then** the change is
   applied to the customer record and attributed to the employee.
2. **Given** an authorized employee, **When** they create or update a supplier and its supplied items,
   **Then** the change is applied to the supplier record and attributed to the employee.
3. **Given** a newly created customer/supplier, **When** the same employee starts an order/PO,
   **Then** the new party is immediately usable.

---

### Edge Cases

- A downstream domain service is temporarily unavailable → the action is reported as temporarily
  failed (distinct from "not permitted"), and nothing is left half-applied.
- A multi-service workflow (receive delivery, build) fails on its second step → the first step is not
  left standing; the whole action is rejected.
- The acting employee is valid but has no role mapped to any back-office action → they can perform
  nothing and are told so, rather than silently seeing empty capability.
- An employee is separated between starting and submitting a long action → the action is rejected at
  submission on the current status.
- A customer/supplier referenced in an in-flight action becomes inactive → the action is rejected on
  validation, consistent with the owning service.
- Concurrent receipts/builds for the same order/item → the underlying services' guarantees hold (no
  over-receipt, no negative stock); the employee sees a single consistent outcome.

## Requirements *(mandatory)*

### Functional Requirements

**Employee identity, authorization & attribution**

- **FR-001**: The system MUST perform every back-office action on behalf of a specific employee and
  MUST validate that employee as existing and active against the HR source of record, rejecting
  actions for unknown, inactive, or separated employees.
- **FR-002**: The system MUST restrict each back-office action to employees holding an authorizing
  role, and MUST reject unauthorized attempts with a clear reason and no side effect.
- **FR-003**: The system MUST attribute every back-office action to the acting employee and MUST record
  an auditable history of who did what, when, and with what outcome (success or rejection).

**Sales**

- **FR-004**: An authorized employee MUST be able to create a sales order for a customer with one or
  more line items (item, quantity, unit price) and take it through confirmation.
- **FR-005**: The system MUST validate the customer against the customer source of record and MUST
  reject orders referencing an unknown or inactive customer.
- **FR-006**: The system MUST honor the inventory service's reservation and availability rules
  (including prevention of overselling) and MUST surface the outcome to the employee.

**Purchasing**

- **FR-007**: An authorized employee MUST be able to create a purchase order for a supplier with one or
  more line items to procure goods the business needs.
- **FR-008**: The system MUST validate the supplier against the supplier source of record and MUST
  reject purchase orders referencing an unknown or inactive supplier.
- **FR-009**: The system MUST carry a purchase order through the approval and sending lifecycle
  governed by the purchasing source of record, including honoring its approval threshold.

**Receiving**

- **FR-010**: An authorized employee MUST be able to record receipt of a delivery against a purchase
  order such that, in one business action, the purchase record advances (partially/fully received) and
  inventory on hand increases by the received quantities.
- **FR-011**: The system MUST prevent silent over-receipt (receiving more than ordered) and MUST
  surface the rejection to the employee.

**Production**

- **FR-012**: An authorized employee MUST be able to trigger a production build that consumes the
  exploded component quantities from the item's bill of materials and increases finished-goods stock,
  as one business action.
- **FR-013**: The system MUST reject a build when components are insufficient, without partially
  consuming any stock.

**Party maintenance**

- **FR-014**: An authorized employee MUST be able to create and update customer records.
- **FR-015**: An authorized employee MUST be able to create and update supplier records, including the
  items each supplier supplies.

**Cross-cutting integrity & integration**

- **FR-016**: The system MUST treat a workflow that spans more than one domain service as all-or-nothing
  at the business level: if a downstream step cannot complete, the action MUST be rejected and reported,
  never left silently half-applied.
- **FR-017**: The system MUST source customer, supplier, employee, inventory, order, and purchase data
  from their owning services and MUST NOT maintain duplicate master copies of that data.
- **FR-018**: The system MUST surface clear, actionable errors that distinguish "invalid / not
  permitted" from "temporarily unavailable" when an underlying service rejects a request or is
  unreachable.
- **FR-019**: The system MUST expose its capabilities through a documented, versioned API behind the
  platform gateway.
- **FR-020**: Monetary amounts and quantities exchanged between services MUST be represented exactly,
  with no floating-point drift.

### Key Entities *(include if feature involves data)*

- **Back-Office Activity Record**: the only data this service persists — the acting employee, the
  action performed, references to the affected domain records (order, PO, receipt, build, party), the
  timestamp, and the outcome. Append-only.
- **Authorization Mapping**: which employee roles may perform which back-office actions.
- **Employee (referenced)**: the acting staff member; owned by the HR service, referenced not stored.
- **Workflow**: a named business action — take sales order, raise purchase order, receive delivery,
  build product, maintain customer, maintain supplier — and the domain steps it coordinates.
- **Referenced domain records (not owned)**: Customer, Supplier and supplied items, Item/Inventory,
  Sales Order, Purchase Order, Goods Receipt, Bill of Materials, Build — each owned by its service.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An authorized employee can take a customer sales order end to end (valid customer, line
  items, confirmation with stock reserved) in a single back-office session.
- **SC-002**: An authorized employee can raise a purchase order and later receive its delivery, with
  inventory on hand reflecting exactly the received quantities, in a single session.
- **SC-003**: 100% of back-office actions are attributable to a specific, valid, active employee and a
  timestamp; every action attempted for an unknown, inactive, or separated employee is rejected.
- **SC-004**: 100% of actions attempted by an employee without an authorizing role are rejected with a
  clear reason and leave no side effect in any domain service.
- **SC-005**: No workflow spanning two services ever leaves a partially-applied result — a downstream
  failure rejects the whole action and the employee is told why.
- **SC-006**: Overselling and over-receipt are never silently accepted; in every such attempt the
  employee sees the rejection and no stock is misstated.
- **SC-007**: A production build consumes exactly the exploded component quantities and increases
  finished stock, or, when components are short, is rejected whole with nothing consumed.
- **SC-008**: A customer or supplier created/updated by an employee is immediately usable by that
  employee in an order or purchase within the same session.

## Assumptions

- **Thin orchestration.** This service owns only its back-office activity log and its authorization
  mapping. It composes the existing services — operations (sales/inventory/BOM/production), HR,
  customers, suppliers/purchasing — and never duplicates their masters.
- **Actor identity via the gateway.** The acting employee's identity is established by the platform
  gateway, as for the other services, and validated against the HR service.
- **Role-scoped actions.** Back-office actions are permitted by role (e.g., sales staff take orders,
  purchasing staff raise POs, warehouse staff receive and build), reusing/mapping to the domain
  services' existing role model rather than inventing a parallel one. *(A candidate for
  `/speckit-clarify`: whether every employee may perform every action instead.)*
- **Business-level atomicity, not distributed transactions.** Multi-service workflows are made
  consistent by ordering steps and rejecting/compensating on failure so nothing is left half-applied;
  a two-phase-commit across services is not assumed.
- **Single tenant per deployment; interactive SMB scale** (tens of concurrent staff), not high-volume
  batch. Money and quantities are exact decimal, consistent with the rest of the platform.

## Out of Scope

- A user-facing UI. This is the backend workflow service; the React front-end consumes it.
- Owning or replacing the customer, supplier, employee, inventory, order, or purchase masters — those
  remain in their services.
- Re-implementing payroll, discount computation, or costing/valuation — those are invoked through
  their services, not reproduced here.
- New approval-workflow engines beyond the lifecycle the purchasing service already enforces.
- Reporting, dashboards, and analytics over back-office activity.

## Dependencies

- **operations-service** (spec 003) — inventory, BOM, production/builds, sales orders, goods receipts.
- **hr-service** (spec 004) — employee validation and role source.
- **crm-service** (spec 005) — customer master.
- **procurement-service** (spec 006) — supplier master, purchase orders, receiving.
- **Gateway** (spec 001) — single public entry point and forwarded caller identity.
