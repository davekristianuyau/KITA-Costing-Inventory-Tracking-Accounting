# Contract: Corrected Orchestrated Calls (018)

The authoritative request/response shape for every `workflow-service` outbound call, taken from the
**receivers' real controllers/DTOs** (the receiver defines its own data — spec Assumption). Each row is
what the consumer-contract test (Decision 2) binds against; drift on either side fails the build.

Legend: **derived** = caller supplies it deterministically (FR-002); **UUID** = the receiver types it as
a UUID, not the workflow's string ref.

## operations-service (`OPERATIONS_BASE_URL`, `/api/operations`)

### Create sales order — `POST /sales-orders`
- **Request** `{ customerRef: string, lines: [ { itemId: UUID, quantity: decimal, uom: string?, unitPrice: decimal } ] }`
- Lines are **atomic at creation** — there is **no** `/{id}/items` endpoint. `OperationsPort.addSalesOrderLine`
  and the create-empty-then-add flow are removed; the workflow assembles all lines then creates once.
- `itemId` is **derived** (item ref → UUID via `GET /items`).
- **Response** `201 { id: UUID, customerRef, status, lines:[{itemId,quantity,unitPrice,reservedQty,fulfilledQty}] }` — read **`id`** (not `salesOrderId`).

### Lifecycle — `POST /sales-orders/{id}/{confirm|fulfill|cancel}`
- No body; response is the `SalesOrderResponse`. Paths already correct.

### Availability — `GET /items/{id}/availability`
- Already correct. Response `{ itemId, onHand, available }` (verify field names in the harness).

### Build — `POST /builds`
- **Request** `{ finishedItemId: UUID, locationId: UUID (derived), quantity: decimal }`
- `finishedItemId` **derived** (ref→UUID); `locationId` **derived** (default/primary location via `GET /locations`).
- **Response** `{ id, finishedItemId, quantity, status }` — map from **`id`**.

## procurement-service (`PROCUREMENT_BASE_URL`, `/api/procurement`)

### Supplier active — `GET /suppliers/{id}`
- 2xx → active; 404 → unknown/inactive; 5xx → transient. Path correct.

### Create PO — `POST /purchase-orders`
- **Request** `{ poNo: string? (omit — receiver derives), supplierId: UUID, origin: enum?, lines: [ { itemRef: string, qtyOrdered: decimal>0, agreedPrice: decimal>=0 } ] }`
- `supplierId` is a **UUID**; line fields are `itemRef / qtyOrdered / agreedPrice` (not `itemId/quantity/unitCost`).
- **Response** `{ id, … }` — read **`id`** (not `purchaseOrderId`).

### Approve / Send — `POST /purchase-orders/{id}/{approve|send}`
- No body. Paths correct.

### Receive — `POST /purchase-orders/{id}/receipts`
- **Request** `{ lines: [ { itemRef: string, qtyReceived: decimal>0 } ] }` (not `itemId/quantityReceived`).
- **Response** receipt DTO — map real id + PO status keys (verify in the harness; adapter currently reads `receiptId/poStatus`).
- Posts the goods receipt to operations **inside procurement-service** (unchanged).

### Create supplier — `POST /suppliers`
- **Request** `{ supplierCode: string (derived), name: string, email?, phone?, … }`
- `supplierCode` is **derived** (deterministic slug of name) — no human supplies it.
- **Response** `SupplierResponse { id, … }` — read **`id`** (not `supplierId`).

### Update supplier — `PATCH /suppliers/{id}`
- **Request** `UpdateSupplierRequest { name?, email?, phone?, address?, … }`.

### Set supplied items — `POST /suppliers/{id}/items` (per item)
- **`POST`, one call per item** (not a single `PUT` with a `{items:[…]}` wrapper).
- **Request** `SupplierItemRequest { itemRef: string, supplierPrice: decimal>=0, leadTimeDays: int?, minOrderQty: decimal? }`.

## crm-service (`CRM_BASE_URL`, `/api/crm`)

### Customer active — `GET /customers/{id}` — path correct (2xx active / 404 unknown / 5xx transient).

### Create customer — `POST /customers`
- **Request** `CreateCustomerRequest { … }` (bind against the real record in the harness).
- **Response** `CustomerResponse { id, … }` — read **`id`** (not `customerId`).

### Update customer — `PATCH /customers/{id}` — `UpdateCustomerRequest`.

## hr-service (`HR_BASE_URL`, `/api/hr`)

### Get employee — `GET /employees/{id}` (roles/active) — used by `HrPort`; verify the response field
names the resolver reads (roles, active) against `EmployeeController`'s real response in the harness.

## Cross-cutting request rules
- Every write carries `X-Idempotency-Key` (stable per action) + forwards `X-Kita-User` (unchanged).
- Bodies serialise money as decimal **strings**, never JSON floats (house rule from workflow 007 fix).
- A receiver 4xx business rejection MUST surface the receiver's own reason; a 403 is a permission
  refusal; 5xx/TLS/timeout is transient — three distinct outcomes (FR-003).
