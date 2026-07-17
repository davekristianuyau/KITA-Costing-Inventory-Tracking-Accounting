# Contract — `workflow-service` public API (behind the gateway)

Versioned, documented API this service **exposes** (FR-019). Base path `/api/workflow`, port 8088,
reached only through Spring Cloud Gateway. Identity comes from gateway headers; **roles are resolved
from HR**, not the client. Every outcome is written to `back_office_activity`.

## Conventions

- **Identity**: gateway sets `X-Kita-User` = acting employee id. `X-Kita-Roles` is **ignored for
  authorization** here — roles are read from the HR record (FR-002). Dev stub: a missing `X-Kita-User`
  resolves to a stub employee with all roles.
- **Idempotency**: clients MAY send `X-Idempotency-Key` on POSTs; the service generates one if absent and
  forwards it downstream (retry-safe, SC-010).
- **Money/quantity**: exact `BigDecimal` (never float).
- **Error envelope** (non-2xx): `{ "outcome": "...", "reason": "...", "code": "..." }`,
  `outcome ∈ {REJECTED_NOT_PERMITTED, REJECTED_INVALID, FAILED_UNAVAILABLE}`.

## Status-code taxonomy (FR-018, SC-004, SC-006, SC-009)

| HTTP | Meaning | Trigger |
|---|---|---|
| `200/201` | success | applied; logged `SUCCESS` |
| `403` | not permitted | HR roles don't grant the action/kind (`REJECTED_NOT_PERMITTED`) |
| `422` | invalid | unknown/inactive party, oversell, over-receipt, short components, separated actor, **self-review (maker == checker)** (`REJECTED_INVALID`) |
| `503` | temporarily unavailable | downstream unreachable/5xx **after bounded retries** (`FAILED_UNAVAILABLE`) — retryable |

Actor validation (FR-001) + HR role resolution (FR-002) run first on every endpoint; unknown/inactive/
separated → `422`, no side effect.

---

## US2 — Sales order lifecycle (DRAFT → PAYMENT-CONFIRMED → RELEASED → COMPLETED)

### `POST /api/workflow/sales-orders`  — create DRAFT (reserves stock)
Auth: `TAKE_SALES_ORDER` (SALES). Records the **maker**.
```json
{ "customerId": "uuid",
  "lines": [ { "itemId": "uuid", "quantity": "10", "unitPrice": "125.00" } ] }
```
- `201` → `{ "salesOrderId": "uuid", "state": "DRAFT", "reservedAll": true }`
- `422` unknown/inactive customer • line exceeds stock (oversell prevented, no order)

### `POST /api/workflow/sales-orders/{id}/confirm-payment`
Auth: `CONFIRM_SALES_PAYMENT` (CASHIER/SALES_MANAGER), **caller ≠ the order's maker**.
- `200` → `{ "state": "PAYMENT_CONFIRMED" }` • `422` self-review • `403` wrong role

### `POST /api/workflow/sales-orders/{id}/release`  — after packed check (operations fulfill)
Auth: `RELEASE_SALES_ORDER` (WAREHOUSE_STAFF/SALES_MANAGER).
- `200` → `{ "state": "RELEASED" }` • `422` not payment-confirmed / stock issue • `503` operations down

### `POST /api/workflow/sales-orders/{id}/complete`  — handed to customer
Auth: `COMPLETE_SALES_ORDER` (SALES/CASHIER). `200` → `{ "state": "COMPLETED" }`.

### `POST /api/workflow/sales-orders/{id}/cancel`  — compensation / abort
Cancels the operations order and clears transient position. `200`.

---

## US3 — Purchase orders

### `POST /api/workflow/purchase-orders` — Auth `RAISE_PURCHASE_ORDER` (PROCUREMENT_STAFF)
```json
{ "supplierId": "uuid", "lines": [ { "itemId": "uuid", "quantity": "100", "unitCost": "12.3400" } ] }
```
- `201` → `{ "purchaseOrderId": "uuid", "status": "DRAFT", "total": "1234.00" }` • `422` unknown/inactive supplier
### `POST /api/workflow/purchase-orders/{id}/approve` — Auth `APPROVE_PURCHASE_ORDER` (PROCUREMENT_APPROVER)
- `200` `{ "status": "APPROVED" }` • threshold enforced by procurement (surfaced 422/403)
### `POST /api/workflow/purchase-orders/{id}/send` — Auth `SEND_PURCHASE_ORDER` (PROCUREMENT_STAFF)
- `200` `{ "status": "SENT" }`

---

## US4 — Receiving (maker–checker)

### `POST /api/workflow/purchase-orders/{id}/receipts`  — record (maker)
Auth: `RECORD_DELIVERY_RECEIPT` (WAREHOUSE_STAFF). Captures a **pending** receipt (transient; no
downstream write yet).
```json
{ "lines": [ { "itemId": "uuid", "quantityReceived": "40" } ] }
```
- `201` → `{ "pendingReceiptId": "uuid", "state": "PENDING_REVIEW" }`

### `POST /api/workflow/receipts/{pendingReceiptId}/confirm`  — confirm (checker)
Auth: `CONFIRM_DELIVERY_RECEIPT` (WAREHOUSE_MANAGER), **caller ≠ maker**. Commits via
`ProcurementPort.receive` (advances PO + posts goods receipt to operations, atomic, idempotent).
- `201` → `{ "receiptId": "uuid", "poStatus": "PARTIALLY_RECEIVED" | "FULLY_RECEIVED" }`
- `422` self-review • over-receipt refused (nothing changes) • `503` inventory update unavailable after
  retries (whole receipt rejected, PO unchanged — FR-016/US4 AC4)

---

## US5 — Production builds

### `POST /api/workflow/builds` — Auth `BUILD_PRODUCT` (PRODUCTION)
```json
{ "itemId": "uuid", "quantity": "5" }
```
- `201` → `{ "buildId": "uuid", "produced": "5" }` • `422` insufficient components (nothing consumed)

---

## US6 — Party maintenance

### `POST /api/workflow/customers` • `PATCH /api/workflow/customers/{id}` — Auth `MAINTAIN_CUSTOMER`
### `POST /api/workflow/suppliers` • `PATCH /api/workflow/suppliers/{id}` — Auth `MAINTAIN_SUPPLIER`
### `PUT /api/workflow/suppliers/{id}/items` — Auth `MAINTAIN_SUPPLIER` — set the items a supplier supplies (FR-015)
```json
{ "items": [ { "itemId": "uuid", "unitCost": "12.3400" } ] }
```
- `201/200` → `{ "customerId" | "supplierId": "uuid" }`; immediately usable next call (SC-008).

---

## Activity log (read)

### `GET /api/workflow/activity?actor=&action=&from=&to=`
- `200` → list of `{ id, actorEmployeeId, action, outcome, reason, targetRef, makerEmployeeId, retryCount, at }`, newest first.

## Health
### `GET /actuator/health` → `200 {"status":"UP"}`
