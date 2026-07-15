# procurement-service

KITA's purchasing bounded context (feature 006): supplier master data, the purchase-order lifecycle,
receiving, and restock suggestions. Spring Boot + JPA + Flyway on PostgreSQL (schema `procurement`),
behind the gateway at `/api/procurement`.

It is also the **supplier Party master** that `operations-service` validates against.

## Modules

`supplier` (master data, supplied items, append-only change history), `purchaseorder` (lifecycle +
state machine), `receiving` (goods receipts), `restock` (reorder suggestions), `operations` (the
integration boundary), `common` (money/rounding, audit, security, error handling), `api` (REST
controllers).

## This service never touches inventory

Stock balances and costing live in **operations-service**. procurement-service reads reorder signals
and posts goods receipts through `OperationsPort`, and operations-service updates on-hand and average
cost (AVCO/FIFO per its own costing model).

The port has two implementations, chosen by `procurement.operations.adapter`:

| value | adapter | use |
|---|---|---|
| `fake` (default) | `FakeOperationsAdapter` | in-memory; lets the service be built and tested in isolation |
| `http` | `HttpOperationsAdapter` | the real HTTP client to operations-service |

The fake **genuinely enforces idempotency** rather than merely recording calls, so a retry test
proves exactly-once behaviour instead of proving the fake is lenient.

## Purchase-order lifecycle

```
DRAFT ──approve──▶ APPROVED ──send──▶ SENT ──receive──▶ PARTIALLY_RECEIVED ──▶ FULLY_RECEIVED ──▶ CLOSED
DRAFT | APPROVED | SENT(pre-receipt) ──cancel──▶ CANCELLED
```

`CLOSED` and `CANCELLED` are terminal. Illegal transitions — receiving a DRAFT, editing a SENT line,
cancelling an order that already has stock against it — are rejected.

- **Approve** is gated by `procurement.approval.threshold` (default 50000.00). At or below it any
  buyer may approve; above it an `APPROVER` is required (FR-006).
- **Send** locks the lines: the agreed price and quantities are immutable afterwards (FR-007).
- **Cancel** is allowed only before any receipt, and has no inventory effect — nothing was ever
  posted (FR-008).

**Every transition takes a row lock and re-checks its guard inside the transaction**, so concurrent
callers serialise and exactly one wins. Without it, two approvals would both read `DRAFT` and both
proceed.

`agreed_price` is frozen onto the line when the order is raised, so a later catalog change never
re-prices an existing order. `line_total = qty × agreed_price`; `order_total = Σ line_total`, exact
decimal, rounded to the cent.

## Receiving

A delivery is validated **in full before any of it is applied**, so one bad line cannot half-apply
the rest. Over-receipt is rejected, never silently absorbed (FR-010) — including cumulative
over-receipt across several deliveries.

Each receipt posts to operations-service **exactly once**, valued at the PO's agreed price rather
than today's catalog price. The receipt carries its own idempotency key; the port refuses a replay,
and the HTTP adapter treats a `409` as *already applied* rather than an error, which is what makes a
retry after a timeout safe (FR-011).

## Restock

`POST /restock/suggestions` reads the current reorder signals and produces **one suggestion per
supplier**, covering every item they are the preferred source for. An item with no preferred supplier
is skipped — there is nobody to order it from.

```
suggested = max(target − onHand, 0), rounded UP to a whole multiple of the supplier's minOrderQty
```

Rounding *up to a multiple* (not merely raising to the minimum) means a supplier who sells in cases
of 12 gets whole cases: a shortfall of 13 orders 24, not 13.

Converting a suggestion raises a **DRAFT** PO. It is only auto-sent when **every** item on it has
`auto_submit` enabled — off by default, so replenishment cannot quietly spend money (FR-014).

## Security

The gateway authenticates and forwards `X-Kita-Roles` (and `X-Kita-User`); this service only
interprets them. `PROCUREMENT_ADMIN` manages suppliers and raises POs; `APPROVER` is the authorized
approver an over-threshold order requires; `RECEIVER` records goods receipts.

`procurement.security.stub=true` (dev/test default) treats a caller with no role header as
`PROCUREMENT_ADMIN` so the service is usable before the gateway is wired up. **Set
`PROCUREMENT_SECURITY_STUB=false` in any real environment.**

## Endpoints

| Area | Endpoint |
|---|---|
| Suppliers | `POST/GET /api/procurement/suppliers`, `GET/PATCH /api/procurement/suppliers/{id}`, `POST/GET /api/procurement/suppliers/{id}/items`, `GET /api/procurement/suppliers/{id}/history` |
| Purchase orders | `POST/GET /api/procurement/purchase-orders`, `GET .../{id}`, `POST .../{id}/approve`, `.../send`, `.../cancel` |
| Receiving | `POST/GET /api/procurement/purchase-orders/{id}/receipts`, `POST .../{id}/close` |
| Restock | `POST/GET /api/procurement/restock/suggestions`, `POST .../{id}/convert`, `.../{id}/dismiss` |
| Health | `GET /actuator/health` |

`specs/006-supplier-purchasing/contracts/procurement-openapi.yaml` is the contract source of truth,
enforced bidirectionally by `OpenApiContractTest` — an undocumented or unimplemented operation fails
the build.

## Run & test

Requires **JDK 17** and, for tests, a running **Docker** daemon (Testcontainers PostgreSQL 16).

```bash
cd backend
./gradlew :procurement-service:spotlessApply      # google-java-format; run before build
./gradlew :procurement-service:build              # compile + Spotless + Checkstyle + tests
./gradlew :procurement-service:test --tests "*PurchaseOrderStateMachineTest*"
```

On Windows + Docker Desktop the tests reach the daemon over `tcp://127.0.0.1:2375`, so enable
*Settings → General → Expose daemon on tcp://localhost:2375 without TLS*. The workaround is applied
in `build.gradle.kts` and is a no-op on Linux/CI.

`AbstractProcurementIT` resets the fake adapter between tests as well as truncating — the fake is a
singleton bean, so posted receipts would otherwise leak across tests.

> **Naming note:** the `supplier` package defines a `Supplier` entity, which shadows
> `java.util.function.Supplier`. Nothing here needs the functional interface — `SupplierService`
> passes values rather than getters — so keep it that way rather than reintroducing the clash.

Configuration is environment-driven (`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`,
`PROCUREMENT_SECURITY_STUB`, `PROCUREMENT_APPROVAL_THRESHOLD`, `OPERATIONS_ADAPTER`,
`OPERATIONS_BASE_URL`); no secrets live in the repo.
