# Quickstart — Procurement Service UI

Validates the full Procurement workspace on top of the 011 console. Run from the repo root on branch
`015-procurement-ui`. **0 real cloud**, **0 backend changes**.

> **Before implementing**: sync `main` into `015-procurement-ui` so the 012/013/014 shared framework
> (`ReferenceInput`, `ListInput`, `FieldInput`, `result/idLabels`, `bodyInput`/dotted-name bodies, and the
> **detail sub-table**) is present. This branch predates those merges.

## 1. Bring up the environment

```bash
cd frontend && npm run dev        # dev server, proxies /auth + /api to the edge
# — or the whole local stack (floci-aws + 009 backend/edge + console):
bash sim/console/console-up.sh client-a
open http://localhost:8080/login  # company=client-a user=alice password=demo-pass
```

## 2. Read-first (US1 + US2)

**Expect** — the Procurement tab shows the left-pane groups (Suppliers, Purchase orders, Reorder, Receiving):

- **Suppliers** → lists suppliers (code, name, status); **Supplier detail** (pick one) shows attributes;
  **Supplier items** / **Supplier history** show its catalog + change log.
- **Purchase orders** → lists POs (supplier resolved to a label, status, total); **Purchase order detail** (by id)
  shows its **lines as a sub-table** (item / qtyOrdered / qtyReceived / price) + status.
- **Reorder suggestions** → items at/below reorder point with suggested quantity + supplier (empty state when
  none).

## 3. Write + progress (US3)

**Expect** — each is a validated run-form; missing required inputs block the call:

- **New supplier** → create → then **Suppliers** shows it.
- **New purchase order** → pick a supplier + add line items → a draft PO with its lines; **Approve / Send /
  Cancel / Close** by id transition the status.
- **Generate reorder suggestions** → produces suggestions; **Convert suggestion to PO** / **Dismiss suggestion**
  act on one.

## 4. Receiving (US4)

**Expect**:

- **Receive against PO** → enter the PO id + received lines → the goods receipt + updated `orderStatus` render;
  the backend posts the goods receipt to operations, so the received quantity is reflected in the **Operations**
  tab (Stock on hand / Movement ledger). **PO receipts** (by id) lists what's been received.
- An over-receipt or wrong-state receive shows a clear error, not an inconsistent state.

## 5. Automated checks

```bash
cd frontend && npm test           # Vitest: Procurement manifest render/run (reuses the 012/013/014 framework)
cd frontend && npm run build      # type-check + production build
```

**Expect**: all green; supplier reference pickers load from `GET /api/procurement/suppliers`; PO detail lines
render as a sub-table; existing 011–014 suites stay green.

Details: [contracts/procurement-manifest.md](./contracts/procurement-manifest.md),
[data-model.md](./data-model.md), [research.md](./research.md).
