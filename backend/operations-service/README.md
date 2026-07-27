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

## Internal transport security (018)

This service is called by `workflow-service` and the gateway over **mutual TLS** when the `mtls` profile
is active (`docker-compose.mtls.yml` / Floci); it is absent by default so local runs are plaintext.

- `server.ssl.client-auth` is **`want`, not `need`** — deliberately. `need` drops an unverifiable caller
  during the TLS handshake, before any application code runs, so the refusal could never be *recorded*.
- `ServiceIdentityFilter` (in `security/`) verifies the peer certificate CN against
  `kita.mtls.allowed-cns`, returns **401**, and persists a row in **`service_call_refusal`**
  (`NO_CERT | UNTRUSTED_CA | EXPIRED | NOT_ALLOWLISTED`) so an intrusion attempt is auditable afterwards.
- `/actuator/**` is exempt, otherwise container health checks (which present no client certificate) fail.
- Certificates are generated at run time by `docker/certs/bootstrap-certs.sh`; nothing secret is
  committed. Rotation is in-place (`reload-on-update`), with remaining validity on `/actuator/health`.

```sql
SELECT occurred_at, peer_address, attempted_cn, reason, request_path FROM service_call_refusal
ORDER BY occurred_at DESC;
```
