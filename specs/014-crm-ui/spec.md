# Feature Specification: CRM Service UI

**Feature Branch**: `014-crm-ui`
**Created**: 2026-07-20
**Status**: Draft
**Input**: User description: "CRM full UI filling the 011 console workspace manifest for crm-service — customer records, multi-tier cascading discounts, loyalty/repeat tiers, government-mandated discounts (PH senior/PWD) and VAT handling, with a price-quote preview; read-first then the write actions; evolves the CRM manifest + result views, no backend code."

## Overview

The full per-service UI for `crm-service` (spec 005) on the 011 console foundation. It grows the Customers tab
into the complete CRM workspace: customer records, the multi-tier **cascading** discount configuration, loyalty
and repeat-purchase tiers, and government-mandated discounts (PH senior/PWD) with VAT handling — plus a
**price-quote preview** that shows how a price is built up for a given customer. Every function is an entry in
the CRM **manifest** rendered by the 011 workspace framework and called through the 009 edge. No backend
changes — `crm-service` already provides these capabilities (unlike features 012/013, no read endpoints are added).

## Clarifications

### Session 2026-07-20

Applied from Phase-0 backend grounding (see [research.md](./research.md); these are facts about `crm-service`,
not open decisions):

- The **quote** is `POST /discounts/compute`, which takes a customer, a sale date, and a **list of line items**
  (item, quantity, unit price) — not a single "base amount" — and returns an itemized `breakdown[]` (each
  cascading/statutory/VAT step) + `flags` and a `finalPrice`. The UI renders that result **verbatim** (FR-012).
- A customer's **applied tiers** are **composed from existing reads** (customer `type` INDIVIDUAL/BUSINESS +
  stored `loyaltyTierId` + entitlements + discount-rules + the quote breakdown) — there is no single
  "tiers-for-a-customer" endpoint, and none is added.
- There is **no per-customer "assign discount tier" endpoint**: discount tiers are **global rules**
  (`discount-rules`) applied at compute time by customer attributes/entitlements; **loyalty** status is set by the
  **evaluate** action (from activity → stored `loyaltyTierId`); a customer's `type` is set via create/update.
- `crm-service` endpoints are **role-gated** (`CRM_ADMIN`/`SALES`), but in the service's **stub security mode**
  (the sim default) a caller with no role header gets all roles, so the console demo session can exercise CRM;
  real-role deployments show a clear 403 when a role is missing.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse customers and their tiers (Priority: P1) 🎯 MVP

A sales/CRM user opens the Customers tab and browses customers: a list, a customer's detail, and the discount
and loyalty/repeat tiers that apply to them.

**Why this priority**: Customer visibility is the base of every CRM task, is read-only, and proves the
per-service pattern end-to-end with no risk of mutating pricing rules.

**Independent Test**: Sign in → Customers → "Customers" lists customers; selecting one shows detail; "Tiers"
shows the discount/loyalty tiers applied to that customer — all read-only via the edge.

**Acceptance Scenarios**:

1. **Given** a signed-in client, **When** they open Customers → Customers, **Then** customers render (name, id,
   type/segment) with loading and empty states.
2. **Given** a customer, **When** their detail is opened, **Then** their attributes are shown.
3. **Given** a customer, **When** their tiers are viewed, **Then** the discount, loyalty, and repeat tiers that
   apply to them are shown.

---

### User Story 2 - Preview a price quote (Priority: P2)

A user enters a customer and one or more line items (item, quantity, unit price) and sees the **cascading**
discount applied in order (e.g. a tier discount then a further discount), any government-mandated discount
(senior/PWD), and VAT — with a clear, itemized before/after breakdown.

**Why this priority**: The quote preview is the headline value of CRM pricing and does not mutate state; it makes
the cascading/statutory rules concrete and auditable.

**Independent Test**: Open Customers → Price quote → enter a customer + sale date + line items → the itemized
breakdown renders (each discount step, statutory discount, VAT, final price).

**Acceptance Scenarios**:

1. **Given** a customer with stacked discounts, **When** a quote is run for their line items, **Then** each
   discount is applied in the correct cascading order and the itemized steps + final price render.
2. **Given** a senior/PWD-eligible customer, **When** a quote is previewed, **Then** the government-mandated
   discount and VAT treatment are shown per the configured rule.

---

### User Story 3 - Review discount and statutory rules (Priority: P2)

A user reviews the configured discount rules: the multi-tier discount definitions, loyalty/repeat tier
thresholds, and the government-mandated discount rules (the generic engine + PH senior/PWD seed).

**Why this priority**: Understanding the rules precedes trusting a quote or editing anything; read-only.

**Independent Test**: Open Customers → Discount rules → the tier and statutory-discount definitions render.

**Acceptance Scenarios**:

1. **Given** configured discounts, **When** discount rules are viewed, **Then** each tier's basis and rate
   render in application order.
2. **Given** statutory discounts, **When** they are viewed, **Then** the mandated-discount rules (e.g. senior/PWD)
   and their VAT treatment render.

---

### User Story 4 - Manage customers and discount assignments (Priority: P3)

A user performs the write actions: create/update a customer, set its type / evaluate its loyalty tier, and mark
a customer as government-discount eligible (senior/PWD) — each a validated form with a clear result.

**Why this priority**: Writes change who gets what price; they follow the read/quote views so effects can be
verified. Each action is independently useful.

**Independent Test**: Create a customer → it appears in the list; set its type / evaluate its loyalty tier → the
customer's detail reflects it; mark senior/PWD eligible → a quote for that customer then applies the mandated
discount.

**Acceptance Scenarios**:

1. **Given** the create-customer form, **When** required fields are provided and submitted, **Then** the customer
   is created; **When** a required field is missing, **Then** the call is blocked with inline validation.
2. **Given** a customer, **When** its type is set or its loyalty tier is evaluated, **Then** the customer's
   detail updates and a subsequent quote reflects the applicable discounts.
3. **Given** a customer, **When** they are marked senior/PWD eligible, **Then** a quote applies the mandated
   discount and VAT rule.

### Edge Cases

- A function returns an empty list → a clear empty state.
- A quote for a customer with no applicable discounts → the breakdown shows base = final (no discount), not an error.
- Cascading order matters → the preview must apply discounts in the configured order and show each step.
- Missing required inputs → inline validation blocks the call before the edge.
- A read/write returns 4xx/5xx via the edge → a clear, non-revealing error with the failing action's context.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Customers tab MUST present its functions grouped by area (Customers, Quote, Discount rules) in
  the 011 left pane.
- **FR-002**: Users MUST be able to list customers and view a customer's detail.
- **FR-003**: Users MUST be able to view the discount, loyalty, and repeat tiers that apply to a customer.
- **FR-004**: Users MUST be able to preview a price quote for a customer and one or more line items (item,
  quantity, unit price) with a sale date, showing each cascading discount step, any government-mandated discount,
  and VAT, with a final price — rendered from the backend's computed result (the UI does not recompute).
- **FR-005**: Users MUST be able to view the configured discount-tier and statutory-discount rule definitions.
- **FR-006**: Users MUST be able to create/update a customer via a validated form.
- **FR-007**: Users MUST be able to influence which tiers apply to a customer via validated forms — set the
  customer's `type` (create/update) which drives discount-tier eligibility, and evaluate the customer's **loyalty**
  tier from supplied activity (stored as their loyalty tier). Discount tiers themselves are global rules
  (FR-005), not a per-customer assignment.
- **FR-008**: Users MUST be able to mark a customer government-discount eligible (e.g. senior/PWD) via a
  validated form, after which a quote applies the mandated discount and VAT rule.
- **FR-009**: Every function MUST show explicit loading, empty, result, and error states (reusing the 011
  framework), with results in the shape best suited to the data (list/table, detail, itemized breakdown).
- **FR-010**: All calls MUST go through the 009 edge with the signed-in client's session; the UI MUST NOT bypass
  the edge or embed credentials.
- **FR-011**: Write actions MUST block on missing required inputs with inline validation before calling the edge.
- **FR-012**: The feature MUST NOT modify `crm-service`; it consumes existing capabilities only, and the quote
  preview MUST reflect the backend's computed result (the UI does not re-implement the discount math).

### Key Entities *(include if feature involves data)*

- **Customer**: a buyer record — name, id, type/segment, and attributes.
- **Discount Tier**: a discount definition with a basis and rate, applied in a cascading order.
- **Loyalty/Repeat Tier**: a status earned by loyalty or repeat purchases, granting a discount.
- **Government-Mandated Discount**: a statutory discount rule (generic engine + PH senior/PWD seed) with VAT
  treatment.
- **Quote**: the itemized build-up of a price for a customer — base, each discount step, statutory discount,
  VAT, final.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From the Customers tab, a user can find a customer and see the tiers that apply to them in **under
  30 seconds**, with no page reloads between functions.
- **SC-002**: **100%** of CRM functions show a distinct loading, empty, result, and error state.
- **SC-003**: A price quote shows **every** discount step in cascading order plus the statutory discount and VAT,
  matching the backend result exactly.
- **SC-004**: A user can create a customer and then see it in the Customers list **without leaving the console**.
- **SC-005**: **0** service calls bypass the edge or expose credentials in the browser (session-cookie only).
- **SC-006**: The full CRM workspace is usable at the 011 responsive floor (down to **768px**) and fully
  keyboard-navigable.

## Assumptions

- `crm-service` (spec 005) exposes the read and write capabilities above via the edge under `/api/crm`; this
  feature maps them into manifest functions and adds no endpoints.
- The quote preview calls the backend's pricing computation; the UI only renders the returned itemized result and
  does not re-implement cascading/statutory/VAT math.
- Result rendering reuses the 011 shapes (table/json/detail/message); a CRM-specific itemized breakdown view may
  be added for the quote where a generic shape is insufficient.
- In the local simulation every client can reach all services (011 assumption A1).

## Dependencies

- **011 service-console-ui** — the console shell, workspace framework, manifest seam, and edge fetch.
- **009 client-login-deploy-sim** — the edge/session the calls route through.
- **005 customer-discounts** — the `crm-service` backend being surfaced.

## Out of Scope

- Any backend/API change to `crm-service`.
- Other services' UIs — their own specs.
- Real cloud deployment; marketing/campaign features; analytics dashboards; role/entitlement management.
