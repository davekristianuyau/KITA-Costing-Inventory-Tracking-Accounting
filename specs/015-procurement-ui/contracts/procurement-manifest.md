# Contract — Procurement service manifest

The concrete manifest that fills the 011 Procurement tab. Every function maps to a **real** procurement-service
endpoint (Phase 0 D1) under `basePath: "/api/procurement"`, rendered by the 011 `FunctionWorkspace` reusing the
012/013/014 shared framework. `{param}` tokens fill from inputs; empty optional query params are dropped. The
**supplier reference source** = `GET /api/procurement/suppliers` (value `id`, label `supplierCode — name`);
`resultRefs` label `supplierId` from it. PO/receipt `lines[]` render via the **014 detail sub-table**. **No
backend changes.**

## Reads

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `suppliers` | Suppliers | GET | `/suppliers` | — | table |
| `supplier` | Supplier detail | GET | `/suppliers/{id}` | id: reference→suppliers | detail |
| `supplier-items` | Supplier items | GET | `/suppliers/{id}/items` | id: reference→suppliers | table |
| `supplier-history` | Supplier history | GET | `/suppliers/{id}/history` | id: reference→suppliers | table |
| `purchase-orders` | Purchase orders | GET | `/purchase-orders` | — | table (resolve `supplierId`) |
| `purchase-order` | Purchase order detail | GET | `/purchase-orders/{id}` | id: text | detail (lines[] sub-table, resolve `supplierId`) |
| `po-receipts` | PO receipts | GET | `/purchase-orders/{id}/receipts` | id: text | table |
| `reorder-suggestions` | Reorder suggestions | GET | `/restock/suggestions` | — | table (resolve `supplierId`) |

## Writes

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `create-supplier` | New supplier | POST | `/suppliers` | `supplierCode*`,`name*` text; `email?`,`phone?`,`address?`,`paymentTerms?`,`deliveryTerms?` text | detail |
| `update-supplier` | Update supplier | PATCH | `/suppliers/{id}` | `id*` reference→suppliers; `name?`,`email?`,`phone?`,`address?`,`paymentTerms?`,`deliveryTerms?` text; `status?` select(ACTIVE/INACTIVE) | detail |
| `add-supplier-item` | Add supplier item | POST | `/suppliers/{id}/items` | `id*` reference→suppliers; `itemRef*` text; `supplierPrice*` number; `leadTimeDays?` number; `minOrderQty?` number | detail |
| `create-po` | New purchase order | POST | `/purchase-orders` | `poNo?` text; `supplierId*` reference→suppliers; `lines*` list of { `itemRef*` text, `qtyOrdered*` number, `agreedPrice?` number } | detail |
| `approve-po` | Approve PO | POST | `/purchase-orders/{id}/approve` | `id*` text | detail |
| `send-po` | Send PO | POST | `/purchase-orders/{id}/send` | `id*` text | detail |
| `cancel-po` | Cancel PO | POST | `/purchase-orders/{id}/cancel` | `id*` text | detail |
| `close-po` | Close PO | POST | `/purchase-orders/{id}/close` | `id*` text | detail |
| `receive-po` | Receive against PO | POST | `/purchase-orders/{id}/receipts` | `id*` text; `lines*` list of { `itemRef*` text, `qtyReceived*` number } | detail (goods receipt + updated orderStatus) |
| `generate-suggestions` | Generate reorder suggestions | POST | `/restock/suggestions` | — | table |
| `convert-suggestion` | Convert suggestion to PO | POST | `/restock/suggestions/{id}/convert` | `id*` text | detail |
| `dismiss-suggestion` | Dismiss suggestion | POST | `/restock/suggestions/{id}/dismiss` | `id*` text | detail |

## Left-pane grouping

- **Suppliers**: Suppliers, Supplier detail, Supplier items, Supplier history, New supplier, Update supplier,
  Add supplier item
- **Purchase orders**: Purchase orders, Purchase order detail, New purchase order, Approve / Send / Cancel /
  Close PO
- **Reorder**: Reorder suggestions, Generate reorder suggestions, Convert suggestion to PO, Dismiss suggestion
- **Receiving**: PO receipts, Receive against PO

## Rules

- `reference→suppliers` inputs load from `GET /api/procurement/suppliers` (value `id`, label
  `supplierCode — name`).
- Required (`*`) inputs block the call with inline validation; PO/receipt lines use the 012 `list` input
  (producing `{lines:[…]}` bodies via the standard object-body builder).
- Result tables resolve `supplierId` → the supplier label via `resultRefs`; PO detail + receipt `lines[]` render
  as a nested sub-table (014).
- **Receiving** posts the goods receipt to operations **in the backend** (FR-012); the UI renders the result and
  the updated `orderStatus`. Over-receipt / wrong-state / cross-service errors surface via 011's error state.
- Role/validation errors surface through 011's error state; in stub mode the demo session has all roles.

## Acceptance

- Opening Procurement shows all groups; each read returns real data (or a clear empty/error) via the edge.
- A PO detail shows its lines (item/qtyOrdered/qtyReceived/price) as a sub-table + status; suggestions list with
  suggested quantity + supplier.
- Writes validate required inputs and render the response; after `create-supplier` the Suppliers list shows it;
  `create-po` → `approve-po` progresses status; `receive-po` updates received quantities (and operations stock,
  observable in the Operations tab).
