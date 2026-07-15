# Research: Customer Records & Discounts Service

**Feature**: 005-customer-discounts | **Date**: 2026-07-12

Spec clarifications were resolved in the spec's Clarifications session. This records technical
decisions. No open NEEDS CLARIFICATION remain.

## Decision: Reuse the operations-service stack

- **Decision**: `crm-service` uses Java 17 / Spring Boot 3.5 / JPA / Flyway / PostgreSQL / Testcontainers,
  same conventions as `operations-service`.
- **Rationale**: Consistency, one CI pattern, low cognitive load (Constitution VI).
- **Alternatives**: Different stack — rejected (no benefit).

## Decision: Cascading (sequential) discount evaluator

- **Decision**: Model the computation as an **ordered list of discount tiers**; fold over them, each
  tier applying to the running remaining amount: `remaining ← remaining − round(remaining × rate)`
  (or minus a fixed amount). Return a breakdown `[{tier, base, amountRemoved}]`.
- **Rationale**: Matches the confirmed semantics (‑25% then ‑5% on 1000 = 712.50, not 700); breakdown
  gives SC-002 reconciliation and an audit of the math.
- **Alternatives**: Additive/summed percentages (rejected — wrong per spec); best-single-discount only
  (rejected — spec requires multi-tier).

## Decision: Exact decimal money & per-tier rounding

- **Decision**: `BigDecimal`, half-up to the currency minor unit, rounded **per tier**; final price =
  base − Σ(rounded tier amounts). Cap so price ≥ 0 (flag if capped).
- **Rationale**: Constitution III; SC-001/002/005.
- **Alternatives**: Round once at end (rejected — breakdown lines must each be exact and sum to total).

## Decision: Generic effective-dated discount-rule engine; PH senior/PWD as seed (Q2=C parity)

- **Decision**: `DiscountRule` rows (PERCENT | FIXED), statutory or promotional, effective-dated, with
  optional **VAT treatment** (e.g., PH senior/PWD: VAT-exempt base then 20% off). Ship PH senior/PWD as
  a Flyway seed migration; nothing jurisdiction-specific in code.
- **Rationale**: FR-009/012; consistent with the statutory approach chosen in spec 004.
- **Alternatives**: Hard-coded PH discounts (rejected — code change per law change, no portability).

## Decision: Configurable stacking policy (default most-favorable)

- **Decision**: `StackingPolicy` ∈ {MOST_FAVORABLE, STATUTORY_THEN_PROMO, PROMO_THEN_STATUTORY,
  STATUTORY_ONLY}. Default **MOST_FAVORABLE** — the customer gets the better of statutory vs. the
  promotional cascade, never silently both.
- **Rationale**: FR-013; the statutory-vs-promo interaction genuinely varies by client/jurisdiction and
  is legally sensitive; making it explicit + configurable avoids a wrong hard default.
- **Alternatives**: Always stack both (rejected — often illegal/overly generous); always statutory-only
  (rejected — denies promos). Flagged in the spec as a `/speckit.clarify` candidate if a client differs.

## Decision: Loyalty eligibility from provided/queried purchase history

- **Decision**: `LoyaltyTier` has configurable qualifying criteria (cumulative count/value over a
  period). Qualifying activity (purchase history) is sourced from `operations-service` or passed in at
  computation time; crm-service evaluates and caches the customer's current tier.
- **Rationale**: FR-010/011; keeps sales ownership in operations-service.
- **Alternatives**: crm-service owning order history (rejected — that's operations-service's domain).

## Decision: Stateless computation; sales stays in operations-service

- **Decision**: The compute endpoint takes {customerId (optional/walk-in), line items, date} and
  returns final price + breakdown; it does **not** persist orders.
- **Rationale**: FR-015; clear bounded context; operations-service owns orders/invoicing.
- **Alternatives**: crm-service persisting orders (rejected — duplicates operations-service).

## Decision: crm-service is the customer Party master

- **Decision**: `crm-service` provides the customer records `operations-service` references by ID
  (fulfilling the feature-003 Party integration port for customers).
- **Rationale**: Avoids duplicate customer masters; coherent with 003's external Party assumption.
- **Alternatives**: Duplicate customer data in operations-service (rejected — drift, integrity risk).
