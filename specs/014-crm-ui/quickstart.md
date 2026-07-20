# Quickstart — CRM Service UI

Validates the full CRM workspace on top of the 011 console. Run from the repo root on branch `014-crm-ui`.
**0 real cloud**, **0 backend changes**.

> **Before implementing**: sync `main` into `014-crm-ui` so the 012/013 shared inputs (`ReferenceInput`,
> `ListInput`, `FieldInput`, `result/idLabels`, plus `bodyInput`/dotted-name body building) are present. This
> branch predates the 012/013 merges.

## 1. Bring up the environment

```bash
cd frontend && npm run dev        # dev server, proxies /auth + /api to the edge
# — or the whole local stack (floci-aws + 009 backend/edge + console):
bash sim/console/console-up.sh client-a
open http://localhost:8080/login  # company=client-a user=alice password=demo-pass
```

## 2. Read-first (US1 + US3)

**Expect** — the Customers tab shows the left-pane groups (Customers, Quote, Discount rules):

- **Customers** → lists customers (code, type, name, status); **Customer detail** (pick a customer) shows
  attributes incl. its loyalty tier; **Customer entitlements** shows any SENIOR/PWD eligibility.
- **Discount rules** → the cascading tier definitions (code/value/priority/effective date); **Discount policy** →
  the stacking mode; **Loyalty tiers** → the loyalty/repeat tier definitions.

## 3. Price quote (US2)

- **Price quote** → pick a customer, enter a sale date, add one or more line items (item, quantity, unit price) →
  the itemized result renders: `baseTotal`, `finalPrice`, and a **breakdown sub-table** with each cascading
  discount step (tier code + origin + amount removed), plus any statutory (senior/PWD) + VAT steps and `flags`.
  Values match the backend exactly (the UI does not recompute).

## 4. Write actions (US4)

**Expect** — each is a validated run-form; missing required inputs block the call:

- **New customer** → create → then **Customers** shows it.
- **Update customer** → PATCH a customer's fields/status.
- **Mark senior/PWD eligible** → add an entitlement → a subsequent **quote** for that customer applies the
  mandated discount + VAT rule.
- **Evaluate loyalty tier** → supply activity → the assigned tier returns (stored as the customer's loyalty tier).
- **New discount rule / Set discount policy / New loyalty tier** → author the pricing rules.
- A call missing a CRM role (non-stub deployment) shows a clear **403**, not a crash.

## 5. Automated checks

```bash
cd frontend && npm test           # Vitest: CRM manifest render/run + the detail sub-table (reuses 012/013 inputs)
cd frontend && npm run build      # type-check + production build
```

**Expect**: all green; customer reference pickers load from `GET /api/crm/customers`; the quote breakdown renders
as a sub-table; existing 011/012/013 suites stay green.

Details: [contracts/crm-manifest.md](./contracts/crm-manifest.md),
[contracts/workspace-result-enhancement.md](./contracts/workspace-result-enhancement.md),
[data-model.md](./data-model.md), [research.md](./research.md).
