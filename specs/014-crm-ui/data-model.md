# Data Model — CRM Service UI

No persistence and **no backend change** — the feature maps existing crm-service capabilities into manifest
functions. Field names/types mirror `crm-service`'s DTOs so result rendering matches the wire. Reuses the 012/013
shared inputs; adds one small generic result enhancement (detail sub-table for nested arrays).

## Surfaced CRM entities (read/response shapes)

- **Customer** (`CustomerResponse`): `id`, `customerCode`, `type` (INDIVIDUAL/BUSINESS), `name`, `email`, `phone`,
  `address`, `status` (CustomerStatus), `loyaltyTierId` (nullable — the stored loyalty tier).
- **Entitlement** (`EntitlementResponse`): a government-mandated eligibility — `kind` (SENIOR/PWD),
  `supportingIdRef`, `validFrom`, `validTo`.
- **Discount Rule** (`DiscountRuleDto.Response`): `code`, `value`, `priority`, `effectiveDate` (the cascading
  tier definitions).
- **Discount Policy**: the stacking `mode` (how discounts cascade/stack).
- **Loyalty Tier** (`LoyaltyTierDto.Response`): a loyalty/repeat tier definition (threshold + discount).
- **Quote** (`ComputeDiscountResponse`): `baseTotal`, `finalPrice`, `breakdown[]` (`{tierCode, origin,
  baseApplied, amountRemoved}` — each cascading/statutory/VAT step), `flags[]`. The itemized build-up of a price.

## Request shapes (write/compute forms) — required (`*`) from the DTOs

- **Create customer** (`CreateCustomerRequest`): `customerCode*`, `type*` (enum), `name*`, `email?`, `phone?`,
  `address?`.
- **Update customer** (`UpdateCustomerRequest`): `name?`, `email?`, `phone?`, `address?`, `status?`.
- **Add entitlement** (`EntitlementRequest`): `kind*` (SENIOR/PWD), `supportingIdRef?`, `validFrom*`, `validTo?`.
- **Evaluate loyalty** (`EvaluateLoyaltyRequest`): `purchaseCount*`, `purchaseValue*` → returns the assigned tier.
- **Quote** (`ComputeDiscountRequest`): `customerId?`, `saleDate*`, `lineItems*[{itemRef?, quantity*,
  unitPrice*}]` — the **012 list input**.
- **Create discount rule** (`DiscountRuleDto`): `code*`, `origin*` (enum), `computation*` (enum), `value*`,
  `effectiveDate*`.
- **Set discount policy**: `mode*` (StackingMode).
- **Create loyalty tier** (`LoyaltyTierDto`): fields read at implementation time.

## Enums (for `select` inputs)

`CustomerType` (INDIVIDUAL/BUSINESS), `CustomerStatus`, `EntitlementKind` (SENIOR/PWD), `DiscountOrigin`,
`DiscountComputationKind`, `StackingMode` — values read from the crm-service enums at implementation.

## Manifest-model additions

**None to `InputField`.** 014 reuses the 012/013 `reference`/`list` kinds + `resultRefs` unchanged. The only
framework change is the **detail sub-table** for array-of-objects result fields (see
[contracts/workspace-result-enhancement.md](./contracts/workspace-result-enhancement.md)). The 014 branch must
**sync `main`** (post-012/013-merge) so the shared inputs exist before implementation.

## Notes

- Monetary/decimal values (baseTotal, finalPrice, amountRemoved) are displayed exactly as returned; the UI
  performs no cascading/statutory/VAT arithmetic (FR-012).
- A customer's applied tiers are composed from existing reads (customer `type` + `loyaltyTierId` + entitlements +
  discount-rules + the quote breakdown) — there is no single per-customer "tiers" endpoint, and none is added.
