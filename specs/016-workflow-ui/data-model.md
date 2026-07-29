# Data Model — Workflow (Back-Office) Service UI

Client-side the console stores nothing; these are the shapes the Workflow tab **renders**. Field names are exactly
what `workflow-service` returns (see [contracts/workflow-read-endpoints.md](./contracts/workflow-read-endpoints.md)).

## Activity Entry (`GET /api/workflow/activity`)

One append-only record per attempted back-office action. Newest first.

| Field | Type | Notes |
|---|---|---|
| `id` | string (uuid) | record id |
| `actorEmployeeId` | string | e.g. `emp-sales` — the acting employee, from `X-Kita-User` |
| `action` | enum `BackOfficeAction` | one of the 12 actions in [Governed Action](#governed-action) |
| `outcome` | enum `ActivityOutcome` | `SUCCESS` \| `REJECTED_NOT_PERMITTED` \| `REJECTED_INVALID` \| `FAILED_UNAVAILABLE` |
| `reason` | string \| null | why it was rejected/failed; null on success |
| `targetRef` | string \| null | affected record, e.g. `sales-order:<id>`, `po:<id>`, `receipt:<id>` |
| `makerEmployeeId` | string \| null | for checker actions, the recorded maker |
| `retryCount` | number | transient-failure retries before the outcome |
| `at` | instant | recorded time |

Filters: `actor`, `action`, `outcome` *(new — FR-003)*, `from`, `to`. Empty filters are dropped from the query
string by the framework. Append-only: no update or delete is exposed anywhere in the UI.

## Outcome

The taxonomy the UI must render distinctly (FR-008, SC-004). Wire values differ per surface — the UI maps both:

| Meaning | Activity `outcome` | Error envelope `outcome` | HTTP |
|---|---|---|---|
| Approved | `SUCCESS` | — | 2xx |
| Rejected — invalid (incl. **self-review**) | `REJECTED_INVALID` | `REJECTED_INVALID` | 422 |
| Not permitted | `REJECTED_NOT_PERMITTED` | `REJECTED_NOT_PERMITTED` | 403 |
| Temporarily unavailable | `FAILED_UNAVAILABLE` | `FAILED_UNAVAILABLE` | 503 |

Self-review is evaluated **before** role authorization, so a maker checking their own item sees
`REJECTED_INVALID`, never `REJECTED_NOT_PERMITTED`.

## Authorization Rule (`GET /api/workflow/authorization`)

A seeded `authorization_mapping` row, projected via `AuthorizationMapping.toRule()`.

| Field | Type | Notes |
|---|---|---|
| `action` | enum `BackOfficeAction` | the governed action |
| `role` | enum `Role` | `SALES`, `CASHIER`, `SALES_MANAGER`, `WAREHOUSE_STAFF`, `WAREHOUSE_MANAGER`, `PROCUREMENT_STAFF`, `PROCUREMENT_APPROVER`, `PRODUCTION`, `CRM_ADMIN` |
| `kind` | enum `AuthorizationKind` | `PERFORM` \| `MAKER` \| `CHECKER` |

Read-only in this feature — mappings are viewed, never edited (spec Out of Scope).

## Pending Review (`GET /api/workflow/pending-reviews`)

Transient in-flight state awaiting a checker, or a sales order's review position. Held in the in-memory
`PendingReviewStore`; **cleared on service restart by design** (the maker simply re-records — no domain effect).

| Field | Type | Notes |
|---|---|---|
| `pendingId` | string | the handle the maker received (sales orders reuse the operations order id) |
| `action` | enum `BackOfficeAction` | the review-gated action |
| `makerEmployeeId` | string | who recorded it — the identity the self-review guard compares against |
| `targetRef` | string | e.g. `sales-order:<id>`, `po:<id>` |
| `stage` | string \| null | lifecycle position for sales orders (e.g. `PAYMENT_CONFIRMED`); null for a simple pending item |
| `createdAt` | instant | when it entered the queue |

⚠️ The store also holds a captured request `payload` for replay on confirm. It is **deliberately excluded** from the
projection — it is internal, may be unserialisable, and the UI has no use for it.

## Actor

The acting employee. Not an entity the UI submits — it is the signed-in console user, carried as `X-Kita-User` by
the edge and resolved against HR by the backend (active check + role set). In the sim the demo logins are the
seeded employees (`emp-sales`, `emp-cashier`, …), so "act as someone else" means "sign in as them".

## Governed Action

A back-office operation run through the pipeline. Each is one manifest function; the authorization kind determines
which role grant applies and whether maker ≠ checker is enforced.

| Action | Kind | Body / path inputs |
|---|---|---|
| `TAKE_SALES_ORDER` | MAKER | `customerId`, `lines[{itemId, quantity, unitPrice}]` |
| `CONFIRM_SALES_PAYMENT` | CHECKER | sales order id |
| `RELEASE_SALES_ORDER` | CHECKER | sales order id |
| `COMPLETE_SALES_ORDER` | PERFORM | sales order id |
| `TAKE_SALES_ORDER` (cancel) | MAKER | sales order id |
| `RAISE_PURCHASE_ORDER` | PERFORM | `supplierId`, `lines[{itemId, quantity, unitCost}]` |
| `APPROVE_PURCHASE_ORDER` | PERFORM | purchase order id |
| `SEND_PURCHASE_ORDER` | PERFORM | purchase order id |
| `RECORD_DELIVERY_RECEIPT` | MAKER | purchase order id, `lines[{itemId, quantityReceived}]` |
| `CONFIRM_DELIVERY_RECEIPT` | CHECKER | pending receipt id |
| `BUILD_PRODUCT` | PERFORM | `itemId`, `quantity` |
| `MAINTAIN_CUSTOMER` | PERFORM | `name`, `active` (+ customer id on update) |
| `MAINTAIN_SUPPLIER` | PERFORM | `name`, `active` (+ supplier id on update); supplied items `[{itemId, unitCost}]` |

All quantities/costs are decimals; **money comes back as a decimal string** — render verbatim.
