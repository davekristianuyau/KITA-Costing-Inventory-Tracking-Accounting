# Data Model: Customer Records & Discounts Service

**Feature**: 005-customer-discounts | **Date**: 2026-07-12 | Schema: `crm` (PostgreSQL, Flyway)

Money/percentages are `NUMERIC` (`BigDecimal`); ids are surrogate UUIDs. Master data keeps history
rows; `created_at`/`updated_at` on all tables.

## Customer

### Customer
- `id`, `customer_code` (unique), `type` (INDIVIDUAL | BUSINESS), `name`, contact fields,
  `status` (ACTIVE | INACTIVE)
- `loyalty_tier_id` (nullable → LoyaltyTier), evaluated tier
- **Rules**: unique `customer_code`; unknown customer at compute time treated as walk-in (no
  entitlements), not an error.

### CustomerAttributeHistory
- `id`, `customer_id`, `changed_at`, `field`, `old_value`, `new_value`, `actor`
- **Rules**: append-only; no destructive overwrite (FR-003).

### Entitlement
- `id`, `customer_id`, `kind` (SENIOR | PWD | …), `supporting_id_ref` (stored, never logged),
  `valid_from`, `valid_to`
- **Rules**: a government-mandated discount applies only if a valid entitlement with its supporting ID
  ref exists (FR-014); ID refs excluded from logs/list responses.

## Discount rules & loyalty

### DiscountRule (effective-dated, versioned)
- `id`, `code`, `origin` (STATUTORY | PROMOTIONAL | LOYALTY), `computation` (PERCENT | FIXED),
  `value`, `applies_to` (which base / line categories), `vat_treatment` (NONE | VAT_EXEMPT | …),
  `effective_date`, `priority` (order within the cascade)
- **Rules**: a computation uses rules effective for the sale date; PH senior/PWD shipped as seed;
  deterministic order by `priority` (FR-006/009).

### LoyaltyTier
- `id`, `code`, `name`, `qualifying_criteria` (count/value threshold over a period),
  `discount_rule_id → DiscountRule` (the tier's promotional discount)
- **Rules**: a customer qualifies when criteria met; contributes its discount as a cascade tier
  (FR-010/011).

### StackingPolicy (configuration, single active per client)
- `id`, `mode` (MOST_FAVORABLE | STATUTORY_THEN_PROMO | PROMO_THEN_STATUTORY | STATUTORY_ONLY)
- **Rules**: default MOST_FAVORABLE (FR-013).

## Computation (not persisted by default; may be logged for audit)

### DiscountComputation (value object / optional audit row)
- `customer_id?`, `sale_date`, input `line_items[]` (item ref, qty, unit price), `base_total`,
  `final_price`, `breakdown[]` of `{ tier_code, origin, base_applied, amount_removed }`,
  `stacking_mode`, `flags[]` (e.g., CAPPED, ENTITLEMENT_WITHHELD)
- **Rules**: `base_total − Σ(amount_removed) = final_price` to the cent (SC-002); `final_price ≥ 0`
  (SC-005).

## Audit

### AuditEvent
- `id`, `actor`, `action` (CUSTOMER_CHANGED, ENTITLEMENT_CHANGED, RULE_CHANGED, POLICY_CHANGED),
  `entity_ref`, `at`, `detail` (PII-scrubbed)
- **Rules**: append-only (FR-016).

## Key relationships

- Customer 1—* Entitlement, 1—* CustomerAttributeHistory, *—0..1 LoyaltyTier.
- LoyaltyTier *—1 DiscountRule; DiscountRule is effective-dated reference data.
- A DiscountComputation references a Customer (optional) and the DiscountRules that applied.
