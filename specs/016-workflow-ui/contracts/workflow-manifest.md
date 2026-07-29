# Contract — the Workflow manifest

The concrete `ServiceManifest` for the Workflow tab (`frontend/src/services/manifests/workflow.ts`), rendered by the
011 `FunctionWorkspace` and called through the 009 edge. Shape per `frontend/src/services/types.ts` plus the two
016 additions (`group`, `result: "outcome"` — see [research.md](../research.md) D5/D6).

```ts
id: "workflow", label: "Workflow", icon: "Workflow", basePath: "/api/workflow"
```

**Reference sources** (cross-service lists, already served and used by 012/014/015):

| Const | path | valueKey | labelKeys |
|---|---|---|---|
| `ITEMS_SOURCE` | `/api/operations/items` | `id` | `sku`, `name` |
| `CUSTOMERS_SOURCE` | `/api/crm/customers` | `id` | `customerCode`, `name` |
| `SUPPLIERS_SOURCE` | `/api/procurement/suppliers` | `id` | `supplierCode`, `name` |
| `PENDING_SOURCE` | `/api/workflow/pending-reviews` | `pendingId` | `action`, `targetRef` |

`PENDING_SOURCE` reuses the new read (§3 of the read-endpoints contract) as a picker — the checker chooses from the
queue instead of pasting a handle. No new framework code.

---

## Group: Activity log (US1)

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `activity` | Activity log | GET | `/activity?actor={actor}&action={action}&outcome={outcome}&from={from}&to={to}` | `actor` text; `action` select (12 actions); `outcome` select (4 outcomes); `from`/`to` text (ISO instant) — **all optional** | `table` |

Blank filters are stripped from the query string by `buildPath` (the 013 `from`/`to` precedent). Description must
say the log is append-only and newest-first.

## Group: Authorization (US2)

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `authorization` | Authorization rules | GET | `/authorization` | — | `table` |

Columns: `action`, `role`, `kind`. Description: who may perform / make / check each action; view-only.

## Group: Reviews (US2)

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `pending-reviews` | Pending reviews | GET | `/pending-reviews?action={action}` | `action` select (optional) | `table` |

Columns: `pendingId`, `action`, `makerEmployeeId`, `targetRef`, `stage`, `createdAt`. Description must state that
the queue is in-memory and clears on service restart (the maker re-records; no domain effect).

## Group: Actions — sales (US3 maker / US4 checker)

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `take-sales-order` | Take sales order (maker) | POST | `/sales-orders` | `customerId` reference→`CUSTOMERS_SOURCE` req; `lines` list req minRows 1 → `itemId` reference→`ITEMS_SOURCE`, `quantity` number, `unitPrice` number | `outcome` |
| `confirm-sales-payment` | Confirm payment (checker) | POST | `/sales-orders/{id}/confirm-payment` | `id` text req | `outcome` |
| `release-sales-order` | Release order (checker) | POST | `/sales-orders/{id}/release` | `id` text req | `outcome` |
| `complete-sales-order` | Complete order | POST | `/sales-orders/{id}/complete` | `id` text req | `outcome` |
| `cancel-sales-order` | Cancel order | POST | `/sales-orders/{id}/cancel` | `id` text req | `outcome` |

Sales-order ids are text (the 012 precedent — the operations list has no short code); the id is returned by
`take-sales-order` and shown in the pending queue's `targetRef`.

## Group: Actions — purchasing (US3 / US4)

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `raise-purchase-order` | Raise purchase order | POST | `/purchase-orders` | `supplierId` reference→`SUPPLIERS_SOURCE` req; `lines` list req minRows 1 → `itemId` reference→`ITEMS_SOURCE`, `quantity` number, `unitCost` number | `outcome` |
| `approve-purchase-order` | Approve purchase order | POST | `/purchase-orders/{id}/approve` | `id` text req | `outcome` |
| `send-purchase-order` | Send purchase order | POST | `/purchase-orders/{id}/send` | `id` text req | `outcome` |
| `record-receipt` | Record delivery receipt (maker) | POST | `/purchase-orders/{id}/receipts` | `id` text req; `lines` list req minRows 1 → `itemId` reference→`ITEMS_SOURCE`, `quantityReceived` number | `outcome` |
| `confirm-receipt` | Confirm delivery receipt (checker) | POST | `/receipts/{pendingReceiptId}/confirm` | `pendingReceiptId` reference→`PENDING_SOURCE` req | `outcome` |

`raise-purchase-order` returns `total` as a **decimal string** — render verbatim, never parse to a number.

## Group: Actions — production & parties (US3)

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `build-product` | Build product | POST | `/builds` | `itemId` reference→`ITEMS_SOURCE` req; `quantity` number req | `outcome` |
| `create-customer` | New customer | POST | `/customers` | `name` text req; `active` boolean | `outcome` |
| `update-customer` | Update customer | PATCH | `/customers/{id}` | `id` reference→`CUSTOMERS_SOURCE` req; `name` text req; `active` boolean | `outcome` |
| `create-supplier` | New supplier | POST | `/suppliers` | `name` text req; `active` boolean | `outcome` |
| `update-supplier` | Update supplier | PATCH | `/suppliers/{id}` | `id` reference→`SUPPLIERS_SOURCE` req; `name` text req; `active` boolean | `outcome` |
| `set-supplied-items` | Set supplied items | PUT | `/suppliers/{id}/items` | `id` reference→`SUPPLIERS_SOURCE` req; `items` list req minRows 1 → `itemId` reference→`ITEMS_SOURCE`, `unitCost` number | `outcome` |

19 functions, 6 declared groups (the spec's four areas, with Actions split into three readable sections).

---

## The `outcome` result kind (FR-008, SC-004)

`OutcomeView` renders the taxonomy from the HTTP status plus the envelope, and is the only place the four cases are
styled. It never decides an outcome itself — it displays what the backend returned.

| Case | Trigger | Rendering |
|---|---|---|
| Approved | 2xx | success banner + the response body via the existing detail view |
| Rejected — invalid | 422 / `REJECTED_INVALID` | warning banner + `reason` (self-review reads as e.g. "self review not allowed") |
| Not permitted | 403 / `REJECTED_NOT_PERMITTED` | denial banner + `reason` |
| Temporarily unavailable | 503 / `FAILED_UNAVAILABLE` | retry banner + `reason` |
| Other failure | any other non-2xx | the existing generic error banner |

`callEdge` extends its failure parsing to pick up `reason` (and `outcome`) from the `ErrorResponse` envelope, so the
message shown is the backend's own, not "Request failed (403)". Existing manifests are unaffected — they keep
`message`-based extraction as the fallback.

## No actor input — anywhere

No function takes an "acting employee". The edge strips inbound `X-Kita-*` and sets `X-Kita-User` from the session;
the backend resolves roles from HR. A maker/checker demonstration is done by **signing in as a different employee**
(see [quickstart.md](../quickstart.md)). Any task that adds an actor field to this manifest is wrong.
