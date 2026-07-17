# Contract — ports `workflow-service` depends on

Outbound port interfaces + the transient pending-store + the retry contract. Each downstream port has an
HTTP adapter (`@ConditionalOnProperty(workflow.<x>.adapter=http)`) and a default in-memory fake,
mirroring `procurement-service`'s `OperationsPort`/`HttpOperationsAdapter`. All HTTP adapters go through
`RetryingCaller`, propagate `X-Kita-User`/`X-Kita-Roles`, and send `X-Idempotency-Key` on posts.
Failure mapping: transient (timeout/5xx) → retried up to N, then `DownstreamUnavailableException` (503);
downstream 4xx business "no" → `ValidationException` (422); `409` on an idempotent post → already-applied
(success). The endpoints exist in the domain services (verified from their controllers).

---

## `HrPort` → hr-service (`:8085`) — FR-001, FR-002 (roles authoritative)

| Method | Downstream call | Purpose |
|---|---|---|
| `EmployeeView getEmployee(UUID id)` | `GET /api/hr/employees/{id}` | active/separated status **and the employee's assigned roles** |

`EmployeeView { UUID id; boolean active; Set<String> roles }` — `active=false`/404 → `ValidationException`
"employee not active". Roles feed `ActionAuthorizer` (NOT the gateway header).
**Dependency**: hr-service must expose the assigned back-office role tokens; the fake supplies them for
isolated tests. (Flagged in plan Dependencies.)

## `CrmPort` → crm-service (`:8086`) — FR-005, FR-014, SC-008

| Method | Downstream call |
|---|---|
| `boolean customerActive(UUID id)` | `GET /api/crm/customers/{id}` |
| `UUID createCustomer(CustomerInput)` | `POST /api/crm/customers` |
| `void updateCustomer(UUID id, CustomerInput)` | `PATCH /api/crm/customers/{id}` |

## `OperationsPort` → operations-service (`:8083`) — FR-004, FR-006, FR-012, FR-013

| Method | Downstream call | Used by |
|---|---|---|
| `UUID createSalesOrder(UUID customerId)` | `POST /api/operations/sales-orders` | US2 DRAFT |
| `void addSalesOrderLine(UUID soId, Line)` | `POST /api/operations/sales-orders/{id}/items` | US2 DRAFT |
| `void confirmSalesOrder(UUID soId)` | `POST /api/operations/sales-orders/{id}/confirm` | US2 DRAFT (reserve) |
| `void fulfillSalesOrder(UUID soId)` | `POST /api/operations/sales-orders/{id}/fulfill` | US2 RELEASED (commit stock) |
| `void cancelSalesOrder(UUID soId)` | `POST /api/operations/sales-orders/{id}/cancel` | US2 compensation/cancel |
| `Availability availability(UUID itemId)` | `GET /api/operations/items/{id}/availability` | optional pre-check |
| `BuildResult build(UUID itemId, BigDecimal qty)` | `POST /api/operations/builds` | US5 |

`Line { UUID itemId; BigDecimal quantity; BigDecimal unitPrice }` (exact decimals).

## `ProcurementPort` → procurement-service (`:8087`) — FR-007..FR-011, FR-015

| Method | Downstream call | Used by |
|---|---|---|
| `boolean supplierActive(UUID id)` | `GET /api/procurement/suppliers/{id}` | US3 |
| `UUID createPurchaseOrder(UUID supplierId, List<PoLine>)` | `POST /api/procurement/purchase-orders` | US3 |
| `void approve(UUID poId)` | `POST /api/procurement/purchase-orders/{id}/approve` | US3 |
| `void send(UUID poId)` | `POST /api/procurement/purchase-orders/{id}/send` | US3 |
| `ReceiptResult receive(UUID poId, List<ReceiptLine>)` | `POST /api/procurement/purchase-orders/{id}/receipts` | US4 **confirm** — atomically advances PO + posts goods receipt to operations (idempotent); over-receipt → 422 |
| `UUID createSupplier / updateSupplier(SupplierInput)` | `POST/PATCH /api/procurement/suppliers` | US6 |
| `void setSuppliedItems(UUID supplierId, List<SuppliedItem>)` | `PUT /api/procurement/suppliers/{id}/items` | US6 — the items each supplier supplies (FR-015) |

`PoLine { UUID itemId; BigDecimal quantity; BigDecimal unitCost }`.
`ReceiptResult { UUID receiptId; String poStatus }` (`PARTIALLY_RECEIVED` | `FULLY_RECEIVED`).
`SupplierInput { String name; boolean active; … }`; `SuppliedItem { UUID itemId; BigDecimal unitCost }`.

---

## `PendingReviewStore` (internal port) — transient in-flight state (Clarify Q5)

| Method | Purpose |
|---|---|
| `UUID put(PendingReview p)` | store a pending review-gated action (receipt / sales position) |
| `Optional<PendingReview> get(UUID pendingId)` | fetch for confirmation |
| `void remove(UUID pendingId)` | clear after the durable downstream write |

- Default adapter: `InMemoryPendingReviewStore` (`ConcurrentHashMap`). No durability by design — loss ⇒
  the maker re-records, no domain effect. A cache/queue adapter may replace it with **no caller change**.

## `RetryingCaller` (internal helper) — FR-018, SC-010

- `<T> T call(String idempotencyKey, Supplier<T> downstreamCall)` — retries transient failures up to
  `workflow.retry.max-attempts` (default 3) with short backoff; passes the idempotency key through; on
  exhaustion throws `DownstreamUnavailableException`. Does **not** retry 4xx business rejections.
- **Guarantee**: with the downstream idempotency contract (repeat key / `409` = already-applied), retries
  never cause a duplicate side effect (SC-010).
