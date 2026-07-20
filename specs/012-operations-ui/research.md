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

## D2 — Reconciling the spec with the real API (write-only resources)

Several spec reads have **no backing GET**. Resolutions (no backend change):

- **"View an item's detail" (FR-002)** → the `GET /items` row already carries the full `ItemResponse` (sku,
  name, type, uom, valuation, perishable, standard cost). The catalog **list is the detail source**; a selected
  row can be shown as a detail panel client-side. No `GET /items/{id}` is needed or exists.
- **"Reservations" (FR-004)** → there is no separate reservations endpoint; `GET /items/{id}/availability`
  returns `reserved` and `available` per location. **Availability IS the reservations view** (available =
  onHand − reserved). The spec's US2 "reservations" maps here.
- **Sales orders, builds, goods receipts, BOMs, locations are write-only** (create/act returns the resource, but
  there is no list/get to re-read them). **Decision**: these functions render their **action response** as the
  result (e.g. the created sales order with its lines + status; a build's status). Verification of effect uses a
  **downstream read where one exists**: after an adjustment/build/receipt, `GET /movements` and
  `GET /items/{id}/availability` reflect the stock change; after `POST /items`, `GET /items` shows the new item.
  A created sales order cannot be re-listed via the API — this is called out as a known gap; a future
  `GET /sales-orders` is **out of scope** (would be a backend spec).

## D3 — Usable inputs: reference picker + enum selects (the one framework enhancement)

The id-taking reads and most writes take **UUIDs** (itemId, locationId, parentItemId, finishedItemId, …) and
some take **enums**. 011's `InputField` only supports static `select` options and free text — users cannot type
UUIDs.

**Decision**: extend the shared workspace framework (not this feature alone) with:

- A new `InputField` kind **`reference`** — a select whose options load from a **list endpoint** via the edge,
  mapping each row to `{ value, label }` (e.g. `GET /api/operations/items` → value = `id`, label = `sku — name`).
  Config on the field: the source path, and which fields are value/label. Loaded once when the function opens;
  its own loading/error state; required-validation like any input.
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

## Summary of decisions

1. Manifest wires only to the five GETs + the POST writes that exist; **no backend code**.
2. Item detail = list row; reservations = availability columns; write-only resources render their action
   response and are verified via downstream reads where one exists (created sales orders can't be re-listed —
   noted gap, out of scope).
3. Add a shared **reference-picker** `InputField` kind (options from a list endpoint) + enum `select`s.
4. Resolve item UUIDs → `SKU — name` in result tables via the items list.
5. Valuation = AVCO|FIFO (FEFO is a lot policy); BOM explosion renders as a flat table; cost/margin as detail.
