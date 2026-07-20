# operations-service

KITA's operations bounded context (feature 003): inventory, bill of materials, production,
sales, and operational costing. Spring Boot + JPA + Flyway on PostgreSQL, behind the gateway at
`/api/operations`.

## Modules
`catalog` (items, UoM), `inventory` (locations, lots, stock levels, movement ledger,
reservations, FEFO consumption), `bom` (kit/manufactured BOMs, explosion), `production`
(atomic builds), `sales` (orders, reservations), `costing` (AVCO + FIFO valuation, roll-up,
margin), `procurement` (goods receipts), `party` (customer/supplier validation port).

## Data contract for the Accounting feature
The Accounting service consumes this service's read APIs; it does not touch the schema directly.

- `GET /api/operations/items/{id}/availability` — on-hand / reserved / available per
  item × location × lot.
- `GET /api/operations/movements?itemId=&from=&to=` — the immutable stock-movement ledger for a
  period. Each movement carries a signed `quantity` (base UoM) and the `unitCost` applied, which
  is sufficient to value inventory and cost of goods for any period.
- `GET /api/operations/items/{id}/cost?salePrice=` — rolled-up cost and margin.

On-hand always reconciles to the sum of an item's movements; `available = on_hand − reserved`.

## Read endpoints added for the console UI (feature 012, FR-015)

Read-only, additive endpoints so the service console can list/view resources that were previously
write-only (no existing endpoint or write/business logic changed). Tenant-scoped by the per-service
schema; each covered by a MockMvc contract test.

- `GET /api/operations/items/{id}` — a single catalog item.
- `GET /api/operations/locations` — the client's stock locations.
- `GET /api/operations/sales-orders` and `/{id}` — sales orders with lines + status.
- `GET /api/operations/builds` and `/{id}` — production builds with status.
- `GET /api/operations/receipts` and `/{id}` — goods receipts (with `lines` + `receivedAt`, added
  additively to `GoodsReceiptResponse`).
