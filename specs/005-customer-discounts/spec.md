# Feature Specification: Customer Records & Discounts Service

**Feature Branch**: `005-customer-discounts`
**Created**: 2026-07-12
**Status**: Draft
**Input**: User description: "customer records for special discounts and computation of discount should
be able to handle multi-tier discounts like -25%/-5% (‑25% first, then ‑5% on the discounted amount)
for repeat customers if any, or government-mandated discounts" (part of the 004–006 split)

## Overview

`crm-service` is the KITA backend service for **customer master data and discount computation**. It
maintains customer profiles and their discount eligibility (repeat-customer/loyalty tiers,
government-mandated entitlements such as senior-citizen/PWD), and provides a **discount computation
engine** that resolves the final price for a sale given a customer and a set of line items — including
**multi-tier cascading discounts** (each tier applies to the already-discounted amount). It is a
distinct bounded context from `operations-service`: sales/order capture stays in operations-service,
which calls crm-service to identify the customer and compute applicable discounts.

## Clarifications

### Session 2026-07-12
- Government-mandated discounts follow the same approach chosen for statutory rules in 004: a
  **generic, configurable, effective-dated discount-rule engine**, with a **Philippines seed ruleset**
  (senior-citizen and PWD discounts and their VAT treatment) shipped as adoptable sample data.
- "Multi-tier `-25%/-5%`" means **sequential/cascading**: apply ‑25% to the base, then ‑5% to the
  already-reduced amount (not ‑30% additive).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Maintain customer records (Priority: P1)

A sales/CRM administrator creates and maintains customer profiles: identity and contact details,
customer type (individual/business), and discount-relevant attributes (loyalty/repeat tier,
government-mandated entitlement such as senior-citizen or PWD with the supporting ID reference). Records
have a lifecycle (active/inactive) and a change history for audit.

**Why this priority**: Customer identity and entitlement data is the foundation for every discount
decision and delivers value on its own as a customer directory.

**Independent Test**: Create a customer with a loyalty tier and a government-mandated entitlement, edit
attributes, deactivate, and retrieve the record and its history — without any discount computation.

**Acceptance Scenarios**:

1. **Given** no matching customer, **When** the admin creates one with required fields, **Then** it is
   persisted with a unique customer code and status "active".
2. **Given** an existing customer, **When** the admin records a government-mandated entitlement with its
   supporting ID reference, **Then** the entitlement is stored and available to discount computation.
3. **Given** a customer, **When** attributes change, **Then** prior values are retained in history.
4. **Given** a duplicate customer code or missing required field, **When** saving, **Then** the change
   is rejected with a clear message.

---

### User Story 2 - Compute multi-tier cascading discounts (Priority: P1) 🎯 MVP

Given a customer and sale line items, the service computes the final price by applying eligible
discount tiers **in sequence**: each tier's percentage applies to the amount remaining after the prior
tier. For example, `-25%` then `-5%` on a 1000 base yields 750, then 712.50 (not 700). The breakdown
(each tier, its base, and the amount it removed) is returned so the sale can show a transparent
computation.

**Why this priority**: This is the core reason the service exists and, with US1, forms the MVP —
operations-service can price a sale for a customer correctly.

**Independent Test**: Configure a two-tier discount (‑25%, ‑5%), request a computation for a base
amount, and verify the result is the cascaded value with a per-tier breakdown that reconciles to the
final price.

**Acceptance Scenarios**:

1. **Given** tiers ‑25% then ‑5% and a base of 1000, **When** computed, **Then** the result is 712.50
   with a breakdown showing 250.00 then 37.50 removed.
2. **Given** a defined tier order, **When** computed, **Then** tiers apply strictly in that order and
   the result is independent of how the request lists them.
3. **Given** the sum of the breakdown, **When** reconciled, **Then** base − (sum of tier amounts) =
   final price exactly (consistent rounding per tier).
4. **Given** no eligible discount, **When** computed, **Then** the final price equals the base and the
   breakdown is empty.

---

### User Story 3 - Repeat-customer / loyalty tier discounts (Priority: P2)

Customers qualify for loyalty/repeat-customer discount tiers based on configurable criteria (e.g.,
cumulative purchase count or value over a period). When a qualifying customer is priced, their loyalty
tier contributes a discount tier to the cascade.

**Why this priority**: Rewards repeat business and is a common ask, but pricing works without it (US2)
for the MVP.

**Independent Test**: Configure a loyalty tier with a qualifying threshold, mark a customer as
qualifying, compute a sale, and verify the loyalty discount is included as a tier in the cascade; a
non-qualifying customer gets no loyalty tier.

**Acceptance Scenarios**:

1. **Given** a loyalty tier with a threshold and a customer who meets it, **When** priced, **Then** the
   loyalty discount is applied as a tier in the cascade.
2. **Given** a customer who does not meet any threshold, **When** priced, **Then** no loyalty tier is
   applied.
3. **Given** a customer whose qualifying activity changes, **When** re-evaluated, **Then** their tier
   is updated per the configured criteria.

---

### User Story 4 - Government-mandated discounts (Priority: P2)

Customers with a government-mandated entitlement (e.g., senior-citizen or PWD) receive the statutory
discount computed by the discount-rule engine, including any statutory treatment (such as VAT
exemption/adjustment) defined for that rule. The stacking policy between a government-mandated discount
and promotional/loyalty tiers is configurable (default per the Assumptions).

**Why this priority**: Legally required for eligible customers where applicable; important but distinct
from the general discount engine (US2).

**Independent Test**: Configure the seeded senior-citizen rule, price a sale for an entitled customer,
and verify the statutory discount and its VAT treatment are applied per the rule and per the configured
stacking policy; verify an entitled customer's outcome differs from a non-entitled one.

**Acceptance Scenarios**:

1. **Given** an entitled customer and the seeded statutory rule, **When** priced, **Then** the
   statutory discount (and its VAT treatment) is applied per the rule.
2. **Given** both a promotional tier and a government-mandated entitlement, **When** priced, **Then**
   the two combine per the configured stacking policy (default: the customer receives the more
   favorable of statutory vs. promotional, never silently both).
3. **Given** an entitlement requiring a supporting ID, **When** the customer lacks it, **Then** the
   statutory discount is not applied and the reason is reported.

---

### Edge Cases

- Total discount would exceed 100% or drive price below zero → capped at zero; flagged.
- Rounding across cascaded tiers → each tier rounds consistently and the breakdown reconciles to the
  final price to the cent.
- A discount rule expired for the sale date → not applied (effective-dated).
- Customer entitlement present but unsupported (missing ID reference) → statutory discount withheld.
- Concurrent updates to a customer's tier/entitlement → last write wins with history retained.
- Computation requested for an unknown customer → treated as no-entitlement walk-in (base price), not
  an error.

## Requirements *(mandatory)*

### Functional Requirements

**Customer records**
- **FR-001**: System MUST let staff create, update, and retrieve customer profiles, each with a unique
  customer code and an active/inactive status.
- **FR-002**: System MUST store discount-relevant attributes: customer type, loyalty/repeat tier, and
  government-mandated entitlements with a supporting ID reference.
- **FR-003**: System MUST retain a change history of customer attribute changes (no destructive
  overwrite) and never expose entitlement ID references in logs.

**Discount computation**
- **FR-004**: System MUST compute a final price for a customer and a set of line items by applying
  eligible discount tiers **sequentially**, each tier applying to the amount remaining after the prior
  tier (cascading, not additive).
- **FR-005**: System MUST return a per-tier breakdown (tier, base it applied to, amount removed) that
  reconciles exactly to the final price.
- **FR-006**: System MUST apply discount tiers in a deterministic, configured order, independent of
  request ordering.
- **FR-007**: System MUST support percentage and fixed-amount discount tiers.
- **FR-008**: System MUST cap total discount so the final price is never negative, flagging any capped
  computation.
- **FR-009**: Discount rules/tiers MUST be versioned with effective dates so a computation uses the
  rules in effect for the sale date.

**Loyalty / repeat customer**
- **FR-010**: System MUST evaluate a customer's loyalty/repeat tier from configurable criteria
  (e.g., cumulative purchase count/value over a period) and contribute the qualifying tier to the
  cascade.
- **FR-011**: System MUST recompute tier eligibility when the underlying qualifying activity changes.

**Government-mandated discounts**
- **FR-012**: System MUST compute government-mandated discounts via a **generic, configurable,
  effective-dated discount-rule engine** with no jurisdiction hardwired, and MUST ship a **Philippines
  seed ruleset** (senior-citizen and PWD discounts with their VAT treatment) as adoptable sample data.
- **FR-013**: System MUST apply a configurable **stacking policy** between government-mandated and
  promotional/loyalty discounts (default: most-favorable, not silently combined).
- **FR-014**: System MUST withhold a government-mandated discount when its required supporting ID
  reference is absent, and report the reason.

**Integration & audit**
- **FR-015**: System MUST expose discount computation as a callable operation for the sales flow
  (operations-service) that returns the final price and breakdown for given customer + line items,
  without owning order capture itself.
- **FR-016**: System MUST record an audit trail of discount-rule and customer-entitlement changes
  (who/when) and restrict record and rule management to authorized roles.

### Key Entities *(include if feature involves data)*

- **Customer**: identity/contact, type (individual/business), status, and discount attributes.
- **LoyaltyTier**: a repeat-customer tier with qualifying criteria and an associated discount.
- **Entitlement**: a government-mandated discount eligibility on a customer (e.g., senior/PWD) with a
  supporting ID reference.
- **DiscountRule**: a versioned, effective-dated rule (percentage or fixed, statutory or promotional)
  with any VAT treatment; the generic engine's unit of configuration (PH rules seeded).
- **DiscountComputation**: the result for a customer + line items — final price and an ordered per-tier
  breakdown.
- **StackingPolicy**: configuration for how statutory and promotional/loyalty discounts combine.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For tiers ‑25% then ‑5% on a base of 1000, the computed final price is exactly 712.50
  with a reconciling breakdown (250.00, then 37.50).
- **SC-002**: 100% of discount computations return a breakdown where base − sum(tier amounts) = final
  price to the cent.
- **SC-003**: An entitled customer's computed price reflects the seeded government-mandated rule
  (including VAT treatment) and matches the configured stacking policy versus promotional tiers.
- **SC-004**: A qualifying repeat customer automatically receives the configured loyalty tier; a
  non-qualifying customer does not.
- **SC-005**: No computation ever yields a negative price; any capped or withheld discount is reported
  with a reason.
- **SC-006**: Discount-rule and entitlement changes are attributable to a user and timestamp; only
  authorized roles can manage records and rules.

## Assumptions

- **Stacking default**: when both a government-mandated discount and promotional/loyalty tiers apply,
  the customer receives the **most favorable** outcome (statutory vs. promotional), not both stacked —
  configurable via StackingPolicy. (A candidate for `/speckit.clarify` if the client's policy differs.)
- **Discount computation lives here; order capture stays in `operations-service`**, which calls this
  service to price a sale. crm-service does not persist orders.
- **Loyalty criteria are configurable** (count/value thresholds over a period); the qualifying activity
  (purchase history) is sourced from operations-service or provided at computation time.
- **Government rules are data, not code** — generic engine, Philippines senior/PWD shipped as seed,
  consistent with the statutory approach in spec 004.
- Single currency per client; monetary rounding is applied per tier and reconciles to the cent.

## Out of Scope

- Employee HR/payroll (spec 004) and supplier records/purchase orders (spec 006).
- Sales order capture, invoicing, and payment (owned by `operations-service`).
- Marketing campaign management, coupons/vouchers issuance, and points-redemption mechanics beyond
  loyalty-tier discounting.

## Dependencies

- `operations-service` for the sales flow that consumes discount computation and for purchase-history
  inputs to loyalty evaluation.
- Platform authentication/roles; the gateway for exposure; its own PostgreSQL schema.
