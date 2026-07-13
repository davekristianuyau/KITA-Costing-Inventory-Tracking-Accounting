# Contract: Discount Computation Engine

**Feature**: 005-customer-discounts | **Date**: 2026-07-12

Deterministic, **cascading** discount computation. Exact decimal (`BigDecimal`), half-up to the
currency minor unit, rounded **per tier**. Result never negative (FR-004/005/006/008, SC-001/002/005).

## Inputs

- `customerId` (optional; absent → walk-in with no entitlements/loyalty), `saleDate`, `lineItems`
  (item ref, qty, unit price) → `baseTotal = Σ qty × unitPrice`.
- Effective `DiscountRule`s for `saleDate`; the customer's `Entitlement`s and `LoyaltyTier`; active
  `StackingPolicy`.

## Building the tier list

1. **Promotional/loyalty tiers**: gather applicable PROMOTIONAL rules + the customer's LOYALTY tier
   rule, ordered by `priority` → the promotional cascade.
2. **Statutory tiers**: gather STATUTORY rules the customer is entitled to (valid Entitlement with
   supporting ID; else withhold and add flag `ENTITLEMENT_WITHHELD`), applying any `vat_treatment`
   (e.g., compute on the VAT-exempt base for PH senior/PWD).

## Cascading fold

For an ordered tier list against a starting amount:
```
remaining = base
for tier in tiers (by priority):
    amt = tier.kind == PERCENT ? round(remaining * tier.value) : min(tier.value, remaining)
    breakdown.add({tier, baseApplied: remaining, amountRemoved: amt})
    remaining -= amt
finalPrice = remaining        # ≥ 0; if a tier would overshoot, amt is capped and flag CAPPED
```
Example: base 1000, tiers ‑25% then ‑5% → 250 removed (base 1000), then 37.50 removed (base 750) →
finalPrice 712.50. Breakdown reconciles: 1000 − 250 − 37.50 = 712.50 (SC-001/002).

## Applying the stacking policy

Compute the **promotional cascade** result P and the **statutory** result S (statutory applied to the
appropriate base), then combine per `StackingPolicy.mode`:
- **MOST_FAVORABLE** (default): return whichever yields the lower final price; breakdown reflects the
  chosen path. Never silently apply both.
- **STATUTORY_THEN_PROMO** / **PROMO_THEN_STATUTORY**: one continuous cascade in that order.
- **STATUTORY_ONLY**: ignore promotional/loyalty tiers.

## Guarantees / tests

- `baseTotal − Σ amountRemoved = finalPrice` to the cent (SC-002).
- `finalPrice ≥ 0`; overshoot capped + flagged (SC-005).
- Deterministic: same inputs + rules ⇒ same result, independent of request tier ordering (FR-006).
- Golden tests: the ‑25%/‑5% example; a senior-citizen VAT-exempt example; a stacking example under
  each policy mode; an entitlement-withheld (missing ID) example.
