# Research — CRM Service UI

Phase 0 for 014. Grounded by reading `backend/crm-service/src/main/java/com/kita/crm/` (controllers, DTOs,
`common/security/CallerContext`).

## D1 — Endpoint inventory (grounded); all needed reads exist → NO backend change

Under `/api/crm`. **Reads (GET):**

| Function | Method + path | Inputs | Returns |
|---|---|---|---|
| List customers | `GET /customers` | — | `CustomerResponse[]` |
| Customer detail | `GET /customers/{id}` | id | `CustomerResponse` (incl. `type`, `status`, `loyaltyTierId`) |
| Customer entitlements | `GET /customers/{id}/entitlements` | id | `EntitlementResponse[]` (SENIOR/PWD + validity) |
| Discount rules | `GET /discount-rules?asOf=` | asOf? (ISO date) | `DiscountRuleDto.Response[]` |
| Discount policy | `GET /discount-policy` | — | `{ mode }` (stacking mode) |
| Loyalty tiers | `GET /loyalty/tiers` | — | `LoyaltyTierDto.Response[]` |

**Writes / compute (POST/PATCH/PUT):** `POST /customers`, `PATCH /customers/{id}`, `POST /customers/{id}/entitlements`,
`POST /discounts/compute` (the **quote**), `POST /discount-rules`, `PUT /discount-policy`, `POST /loyalty/tiers`,
`POST /customers/{id}/loyalty/evaluate`.

**Decision**: every read a user story needs already exists — customers list/detail, per-customer entitlements,
discount rules/policy, loyalty tiers, and the quote (a compute POST). **014 adds NO backend endpoints** (honors
the spec's "no backend change"). This differs from 012/013, which had genuine write-only resources.

## D2 — The quote takes line items, not a single "base amount" (spec reconciliation)

`POST /discounts/compute` — `ComputeDiscountRequest(customerId?, saleDate*, lineItems*[{itemRef, quantity*,
unitPrice*}])` → `ComputeDiscountResponse(baseTotal, finalPrice, breakdown[{tierCode, origin, baseApplied,
amountRemoved}], flags[])`. **Decision**: the quote form supplies a **customer** (optional reference), a
**saleDate**, and a **list of line items** (reuse the 012 `list` input) — not a single amount. The response's
`breakdown[]` **is** the itemized cascading steps (each `tierCode`/`origin`/`amountRemoved`); `flags[]` carries
notes; VAT and statutory (senior/PWD) steps appear as breakdown lines by `origin`. The UI renders the returned
result verbatim (FR-012) — no client-side pricing.

## D3 — A customer's "tiers" are composed from existing reads (spec reconciliation)

There is no single "GET the tiers applied to a customer" endpoint, and none is needed. **Decision**: US1's
"tiers that apply" is composed from: the customer detail (`type` = INDIVIDUAL/BUSINESS, and the stored
`loyaltyTierId`), the **entitlements** GET (SENIOR/PWD government discounts), the **loyalty-tiers** list (to label
`loyaltyTierId`), the **discount-rules** list (the tier definitions), and — most concretely — the **quote
breakdown**, which shows exactly which `tierCode`s apply to a given sale. The manifest surfaces these as distinct
functions; the id→label helper labels `loyaltyTierId` from the loyalty-tiers list.

## D4 — "Assigning" tiers: rule-driven + evaluate, not a per-customer assign (spec reconciliation)

FR-007 ("assign a discount tier or loyalty status") has no direct backend "assign tier" endpoint. **Decision /
reconciliation**: discount tiers are **global rules** (`discount-rules`) applied at compute time by customer
attributes/entitlements — there is nothing to assign per customer; the customer's **type** (create/update) and
**entitlements** (senior/PWD) drive which apply. **Loyalty** status is set by the **evaluate** POST (from supplied
activity → stored `loyaltyTierId`). So the write surface is: create/update customer, add entitlement, evaluate
loyalty, and (rule authoring) create discount rule / loyalty tier / set policy. No "assign discount tier to
customer" endpoint exists or is added.

## D5 — Role-gating (stub mode → demo session works)

Every crm-service endpoint is role-gated (`CRM_ADMIN` / `SALES`). `CallerContext` runs in **stub mode by default**
(`crm.security.stub=true`) — a caller with no `X-Kita-Roles` header gets **all roles**, so the 011/009 console's
demo session can exercise CRM; a real-role deployment without a role shows a clear **403** (011 error state).

## D6 — Reuse the 012/013 shared inputs + one small result enhancement

**Decision**: 014 adds **no new input types** — customer/loyalty pickers use the 012 `reference` input; the quote
line items use the 012 `list` input; enum selects (CustomerType, EntitlementKind, DiscountOrigin/Computation,
StackingMode) use `select`. The **one** framework touch: extend `DetailView` so an **array-of-objects field**
(e.g. the quote's `breakdown[]`) renders as a nested **sub-table** instead of a JSON blob — a small, generic win
used wherever an object result carries a nested list. See
[contracts/workspace-result-enhancement.md](./contracts/workspace-result-enhancement.md).

## Summary of decisions

1. **No backend change** — all needed reads exist; the quote is a compute POST.
2. Quote = line items → itemized `breakdown[]` + `flags` (VAT/statutory as breakdown lines); rendered verbatim.
3. A customer's tiers = composed reads (type + loyaltyTierId + entitlements + discount-rules + quote breakdown).
4. "Assign tier" is rule-driven; loyalty via evaluate; no assign-tier endpoint.
5. crm-service is role-gated; stub mode (sim default) → demo session gets all roles; else clear 403.
6. Reuse 012/013 shared inputs; add one small generic **detail sub-table** for nested-array result fields.
7. ⚠️ Sync `main` into 014 first (branch predates the 012/013 merges).
