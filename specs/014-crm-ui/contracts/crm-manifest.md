# Contract — CRM service manifest

The concrete manifest that fills the 011 Customers tab. Every function maps to a **real** crm-service endpoint
(Phase 0 D1) under `basePath: "/api/crm"`, rendered by the 011 `FunctionWorkspace`, using the **012/013 shared
inputs**. `{param}` tokens fill from inputs; empty optional query params are dropped. The **customer reference
source** = `GET /api/crm/customers` (value `id`, label `customerCode — name`); `resultRefs` label `customerId`
from it and `loyaltyTierId` from the loyalty-tiers list. **No backend changes.**

## Reads

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `customers` | Customers | GET | `/customers` | — | table |
| `customer` | Customer detail | GET | `/customers/{id}` | id: reference→customers | detail |
| `entitlements` | Customer entitlements | GET | `/customers/{id}/entitlements` | id: reference→customers | table |
| `discount-rules` | Discount rules | GET | `/discount-rules?asOf={asOf}` | asOf?: text (ISO date) | table |
| `discount-policy` | Discount policy | GET | `/discount-policy` | — | detail |
| `loyalty-tiers` | Loyalty tiers | GET | `/loyalty/tiers` | — | table |

## Quote (compute POST)

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `quote` | Price quote | POST | `/discounts/compute` | `customerId?` reference→customers; `saleDate*` text (ISO date); `lineItems*` list of { `itemRef?` text, `quantity*` number, `unitPrice*` number } | **detail** (baseTotal, finalPrice, `breakdown[]` sub-table, flags) |

## Writes

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `create-customer` | New customer | POST | `/customers` | `customerCode*` text; `type*` select(INDIVIDUAL/BUSINESS); `name*` text; `email?`,`phone?`,`address?` text | detail |
| `update-customer` | Update customer | PATCH | `/customers/{id}` | `id*` reference→customers; `name?`,`email?`,`phone?`,`address?` text; `status?` select(CustomerStatus) | detail |
| `add-entitlement` | Mark senior/PWD eligible | POST | `/customers/{id}/entitlements` | `id*` reference→customers; `kind*` select(SENIOR/PWD); `supportingIdRef?` text; `validFrom*` text (ISO date); `validTo?` text | detail |
| `evaluate-loyalty` | Evaluate loyalty tier | POST | `/customers/{id}/loyalty/evaluate` | `id*` reference→customers; `purchaseCount*` number; `purchaseValue*` number | detail (assigned tier or empty) |
| `create-discount-rule` | New discount rule | POST | `/discount-rules` | `code*` text; `origin*` select(DiscountOrigin); `computation*` select(DiscountComputationKind); `value*` number; `effectiveDate*` text (ISO date) | detail |
| `set-discount-policy` | Set discount policy | PUT | `/discount-policy` | `mode*` select(StackingMode) | detail |
| `create-loyalty-tier` | New loyalty tier | POST | `/loyalty/tiers` | (LoyaltyTierDto fields — read at implementation time) | detail |

*(Enum option lists — CustomerStatus, DiscountOrigin, DiscountComputationKind, StackingMode, and the
LoyaltyTierDto shape — are read from the crm-service enums/DTO at implementation time.)*

## Left-pane grouping

- **Customers**: Customers, Customer detail, Customer entitlements, New customer, Update customer, Mark
  senior/PWD eligible, Evaluate loyalty tier
- **Quote**: Price quote
- **Discount rules**: Discount rules, Discount policy, Loyalty tiers, New discount rule, Set discount policy,
  New loyalty tier

## Rules

- `reference→customers` inputs load from `GET /api/crm/customers` (value `id`, label `customerCode — name`).
- Required (`*`) inputs block the call with inline validation; the quote's line items use the 012 `list` input.
- The **quote** renders the returned result verbatim (baseTotal + finalPrice as fields; `breakdown[]` as a
  **sub-table** — see workspace-result-enhancement.md; `flags[]` shown as a list). The UI performs no pricing math.
- Result tables/detail resolve `customerId` → `customerCode — name` and `loyaltyTierId` → the tier label via
  `resultRefs`.
- Role/validation errors surface through 011's error state; in stub mode the demo session has all roles.

## Acceptance

- Opening Customers shows all groups; each read returns real data (or a clear empty/error) via the edge.
- The quote shows every cascading discount step (breakdown), the statutory discount + VAT (by origin), and the
  final price, matching the backend result.
- Writes validate required inputs and render the response; after `create-customer` the Customers list shows it;
  after `add-entitlement` + a quote, the mandated discount applies.
