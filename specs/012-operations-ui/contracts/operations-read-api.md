# Contract — operations-service read endpoints (FR-015)

The bounded, **read-only** backend addition for 012 (clarification Q1=C). New `@GetMapping`s on existing
controllers under `/api/operations`, backed by `findAll`/`findById` on existing repositories and reusing existing
response DTOs. **No existing endpoint, entity, or write path changes** (FR-014). Every read is tenant-scoped
automatically by the 008 schema-per-service datasource (queries run against the caller's client schema).

## Endpoints

| Method + path | Backing | Response (200) | Not found |
|---|---|---|---|
| `GET /items/{id}` | `ItemRepository.findById` | `ItemResponse` | 404 |
| `GET /locations` | `StockLocationRepository.findAll` | `LocationResponse[]` | — (empty `[]`) |
| `GET /sales-orders` | `SalesOrderRepository.findAll` | `SalesOrderResponse[]` | — (empty `[]`) |
| `GET /sales-orders/{id}` | `SalesOrderRepository.findById` | `SalesOrderResponse` | 404 |
| `GET /builds` | `BuildRepository.findAll` | `BuildResponse[]` | — (empty `[]`) |
| `GET /builds/{id}` | `BuildRepository.findById` | `BuildResponse` | 404 |
| `GET /receipts` | `GoodsReceiptRepository.findAll` | `GoodsReceiptResponse[]` | — (empty `[]`) |
| `GET /receipts/{id}` | `GoodsReceiptRepository.findById` | `GoodsReceiptResponse` | 404 |

## Response shapes

- `ItemResponse`, `LocationResponse`, `SalesOrderResponse` (id, customerRef, status, lines[]), `BuildResponse`
  (id, finishedItemId, quantity, status) — **reuse existing** records unchanged.
- `GoodsReceiptResponse` — the existing create-response is minimal (id, supplierRef, locationId). **Additively**
  extend it with `lines[]` (itemId, lotCode?, expiryDate?, quantity, uom?, unitCost) and `receivedAt` so the
  list/detail is meaningful. Additive fields only — no wire break for the existing create path.

## Rules

- **Read-only & additive**: only new GET handlers + new `list()/get(id)` service methods (thin pass-throughs to
  the repositories, mapping entities to the response records via the existing `to*` mappers). No change to any
  existing controller method, service write path, entity, or migration.
- **Tenant isolation**: relies on the existing schema-per-service datasource (008) — no explicit client filter is
  added; `findAll` returns only the caller's client rows. A test asserts no cross-client leakage is introduced.
- **Ordering**: list endpoints return newest-first where the entity has a creation timestamp; otherwise a stable
  order (id/insertion). Documented per endpoint in the tests.
- **Errors**: `{id}` reads return **404** when absent (consistent with the service's existing not-found
  handling); lists return `200` with `[]` when empty.

## Tests (red-first, per constitution)

- A **MockMvc contract test per endpoint**: list returns previously created rows with the documented shape and
  order; `{id}` returns the row or 404. Reuse the service's existing test fixtures/seed helpers.
- These run in `:operations-service:build`; heavier Testcontainers ITs stay CI-only (local Docker caveat).

## Acceptance

- Each new GET returns the documented shape; `{id}` 404s when absent; lists are empty-safe.
- No existing operations-service test regresses; no existing endpoint/behavior changes.
- After a `POST /sales-orders` (or build/receipt), the matching list/detail GET returns that record — making the
  spec's US4/US5 acceptance verifiable through the API.
