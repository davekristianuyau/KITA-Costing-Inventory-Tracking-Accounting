# Quickstart — Operations Service UI

Validates the full Operations workspace on top of the 011 console. Run from the repo root on branch
`012-operations-ui`. **0 real cloud**, **0 backend changes**.

## 1. Bring up the environment

```bash
cd frontend && npm run dev        # dev server, proxies /auth + /api to the edge
# — or the whole local stack (floci-aws + 009 backend/edge + console):
bash sim/console/console-up.sh client-a
open http://localhost:8080/login  # company=client-a user=alice password=demo-pass
```

## 2. Read-first (US1–US3)

**Expect** — Operations tab shows the left-pane groups (Catalog, Inventory, BOM, Production, Sales, Costing,
Receiving):

- **Items** → the catalog list renders (sku, name, type, uom, valuation, perishable, standard cost); loading and
  empty states are distinct. The list row is the item's detail.
- **Stock on hand** → pick an item (by SKU/name, not a UUID) → on-hand / reserved / available per location render
  (this is also the reservations view).
- **Movement ledger** → pick an item (+ optional from/to) → movements render in time order; item columns show
  `SKU — name`.
- **BOM explosion** → pick a manufactured item (+ quantity) → the flat component-requirements table renders; a
  cyclic BOM shows a clear "cycle detected" error, never hangs.
- **Cost & margin** → pick an item (+ optional sale price) → the cost/margin detail renders (values shown exactly
  as returned).

## 3. Write actions (US4–US5)

**Expect** — each is a validated run-form; missing required inputs block the call with inline validation:

- **New item** → create → then **Items** shows it.
- **Stock adjustment** → post → then **Stock on hand** / **Movement ledger** for that item reflect it.
- **Production build** → run → build status returns; **Stock on hand** reflects consumed components + output.
- **New sales order** → create with lines → the order + lines + status render (no re-list endpoint — the action
  response is the result). **Confirm / Fulfill / Cancel** by id transition its status.
- **Goods receipt** → post → receipt returns; **Stock on hand** for the received items reflects it.

## 4. Automated checks

```bash
cd frontend && npm test           # Vitest: Operations manifest render/run, reference picker, enum selects,
                                  #         list inputs, id→label resolution (mock the edge)
cd frontend && npm run build      # type-check + production build
```

**Expect**: all green; the reference picker loads options from `GET /api/operations/items`; existing 011 suites
(Login, Console, Workspace) stay green.

Details: [contracts/operations-manifest.md](./contracts/operations-manifest.md),
[contracts/workspace-framework-extensions.md](./contracts/workspace-framework-extensions.md),
[data-model.md](./data-model.md), [research.md](./research.md).
