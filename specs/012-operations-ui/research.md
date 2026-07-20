# Research — Operations Service UI

Phase 0 for 012. The pivotal unknown was **what operations-service actually exposes** — the manifest can only
wire to endpoints that exist (no backend code). Resolved by reading the controllers in
`backend/operations-service/src/main/java/com/kita/operations/api/`.

## D1 — Endpoint inventory (grounded, not assumed)

`operations-service` mounts everything under `/api/operations`. Reachable through the 009 edge as-is.

**Reads (GET) — only these five exist:**

| Function | Method + path | Inputs | Returns |
|---|---|---|---|
| List items | `GET /items` | — | `ItemResponse[]` (id, sku, name, type, baseUom, valuationMethod, perishable, standardCost) |
| Item stock (availability) | `GET /items/{id}/availability` | item id | `AvailabilityResponse[]` (itemId, locationId, lotId, onHand, reserved, available) |
| Movement ledger | `GET /movements?itemId&from?&to?` | item id, optional from/to (ISO datetime) | `MovementResponse[]` (id, itemId, locationId, lotId, type, quantity, unitCost, reason, sourceType, sourceId, occurredAt) |
| BOM explosion | `GET /boms/{parentItemId}/explosion?quantity=1` | parent item id, quantity (default 1) | `ComponentRequirementResponse[]` (componentItemId, requiredQuantity, uom) |
| Item cost & margin | `GET /items/{id}/cost?salePrice?` | item id, optional sale price | `CostMargin` (single object) |

**Writes (POST):** `/items`, `/uoms`, `/uom-conversions`, `/locations`, `/adjustments`, `/boms`, `/builds`,
`/sales-orders` (+ `/{id}/confirm`, `/{id}/fulfill`, `/{id}/cancel`), `/receipts`.

**Decision**: the manifest maps only to the above. **No backend endpoints are added.**

## D2 — Reconciling the spec with the real API (write-only resources) — RESOLVED by adding reads (Q1=C)

Several spec reads had **no backing GET**. Clarification **Q1=C** resolved this by **adding the missing read
endpoints now** (FR-015) rather than working around them. Resolutions:

- **"View an item's detail" (FR-002)** → add **`GET /items/{id}`** (thin: `ItemRepository.findById` → the
  existing `ItemResponse`). The catalog list stays the browse view; detail is a real fetch.
- **"Reservations" (FR-004)** → unchanged: `GET /items/{id}/availability` already returns `reserved`/`available`
  per location. **Availability IS the reservations view** (available = onHand − reserved). No new endpoint.
- **Sales orders, builds, goods receipts, locations were write-only** → **add read endpoints** (D8): list + get
  for sales orders, builds, and goods receipts; a locations list. These make the UI able to list/re-open them and
  make US4/US5 acceptance testable. Stock effects remain additionally verifiable via `GET /movements` and
  `GET /items/{id}/availability`.
- **BOMs** → `GET /boms/{parentItemId}/explosion` already exists; a BOM list is **not required by any user story**
  and is not added (YAGNI).

## D3 — Usable inputs: reference picker + enum selects (the one framework enhancement)

The id-taking reads and most writes take **UUIDs** (itemId, locationId, parentItemId, finishedItemId, …) and
some take **enums**. 011's `InputField` only supports static `select` options and free text — users cannot type
UUIDs.

**Decision**: extend the shared workspace framework (not this feature alone) with:

- A new `InputField` kind **`reference`** — a **searchable picker** whose options load from a **list endpoint**
  via the edge, mapping each row to `{ value, label }` (e.g. `GET /api/operations/items` → value = `id`,
  label = `sku — name`). Config: the source path + which fields are value/label. Per **Q2=A / FR-017**: loads
  **once** when the function opens, filters **client-side (type-ahead)**, and **caps** the rendered options (no
  server-side search endpoint). Its own loading/error state; required-validation like any input.
- Enum inputs use the existing **`select`** with static options from the known backend enums:
  - `ItemType` = RAW_MATERIAL | COMPONENT | FINISHED_GOOD | KIT
  - `UomFamily` = MASS | COUNT | LENGTH | VOLUME | OTHER
  - `ValuationMethod` = AVCO | FIFO
  - `BomType` = (its enum values; read at implementation time)

**Alternative rejected**: raw UUID text inputs — unusable; hardcoded ids — wrong per client/data.

## D4 — Result readability: id→label resolution

Read results carry UUIDs (`itemId`, `locationId`). **Decision**: add a small, reusable helper that resolves item
UUIDs to `SKU — name` by joining against the cached `GET /items` list, applied to Operations result tables
(movements, availability, explosion). Locations have no list endpoint, so location UUIDs stay as-is (or are
shown truncated). This is a thin presentation helper, not new business logic.

## D5 — Valuation methods (correct the spec)

The spec says "AVCO/FIFO/**FEFO**". The backend `ValuationMethod` enum is **AVCO | FIFO** only. FEFO
(first-expiry-first-out) is a **perishable/lot policy**, not a selectable valuation method. **Decision**: the
create-item valuation select offers **AVCO | FIFO**; perishables are flagged by the `perishable` boolean and
handled by lot/expiry in the backend. The manifest and any copy use AVCO|FIFO.

## D6 — BOM explosion is flat, not a tree

`GET /boms/{parentItemId}/explosion` returns a **flat** `ComponentRequirementResponse[]` (the backend already
explodes and sums). **Decision**: render it as a **table** (component, required quantity, uom) — no recursive
tree component is needed. Cycle safety is enforced by the backend; a cyclic BOM surfaces as a clear error from
the call (rendered by 011's error state). This simplifies the spec's "component hierarchy/tree" to a
requirements table.

## D7 — Costing/margin display

`GET /items/{id}/cost?salePrice` returns a single `CostMargin` object. **Decision**: render as a **detail**
view; the optional `salePrice` input lets the user see margin against a price. Monetary values are shown exactly
as returned (decimals) — never recomputed client-side (Constitution III).

## D8 — Backend read endpoints to add (FR-015)

Per Q1=C, add these **read-only** endpoints to operations-service. All are thin and reuse the existing
repositories/DTOs; each is tenant-scoped automatically by the 008 schema-per-service datasource (`findAll`/
`findById` run against the client's schema — no tenancy code, no cross-client exposure).

| Endpoint | Backing | Response |
|---|---|---|
| `GET /items/{id}` | `ItemRepository.findById` | existing `ItemResponse` (404 if absent) |
| `GET /locations` | `StockLocationRepository.findAll` | `LocationResponse[]` (existing shape) |
| `GET /sales-orders` | `SalesOrderRepository.findAll` | `SalesOrderResponse[]` (existing shape: id, customerRef, status, lines) |
| `GET /sales-orders/{id}` | `SalesOrderRepository.findById` | `SalesOrderResponse` (404 if absent) |
| `GET /builds` | `BuildRepository.findAll` | `BuildResponse[]` (existing shape: id, finishedItemId, quantity, status) |
| `GET /builds/{id}` | `BuildRepository.findById` | `BuildResponse` (404 if absent) |
| `GET /receipts` | `GoodsReceiptRepository.findAll` | `GoodsReceiptResponse[]` — **may add `lines` + `receivedAt`** to the existing (id, supplierRef, locationId) shape for a useful list/detail |
| `GET /receipts/{id}` | `GoodsReceiptRepository.findById` | `GoodsReceiptResponse` (404 if absent) |

**Decisions**:
- **Read-only, additive**: new `@GetMapping`s + new `list()/get(id)` service methods; **no existing endpoint,
  entity, or write path is modified** (FR-014).
- **DTOs**: reuse the existing response records; only `GoodsReceiptResponse` may gain `lines`/`receivedAt` so its
  list/detail is meaningful (the create-response was minimal). Additive record change, no wire break.
- **TDD**: each new GET gets a red-first MockMvc contract test (list returns created rows; get returns the row or
  404); ordering is newest-first where a timestamp exists, else insertion/id order.
- **Scope guard**: no BOM list (no story needs it); no new writes; no pagination endpoint (the record picker
  filters client-side per D3/FR-017).

## Summary of decisions

1. Manifest wires to the existing GETs/POSTs **plus** the new read endpoints from D8.
2. Item detail = `GET /items/{id}`; reservations = availability columns; sales orders/builds/receipts become
   **listable/viewable** via the new reads (Q1=C), and stock effects stay verifiable via movements/availability.
3. Add shared **reference-picker** (searchable, load-once/type-ahead — FR-017) + **list** `InputField` kinds +
   enum `select`s.
4. Resolve item UUIDs → `SKU — name` in result tables via the items list.
5. Valuation = AVCO|FIFO (FEFO is a lot policy); BOM explosion renders as a flat table; cost/margin as detail.
6. Backend addition is **read-only, additive, tenant-scoped by schema**, each with a red-first contract test.
