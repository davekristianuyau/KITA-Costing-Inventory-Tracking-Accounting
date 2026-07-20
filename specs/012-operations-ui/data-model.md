# Data Model — Operations Service UI

The UI adds no persistence. This captures (a) the **operations entities as surfaced** by the existing DTOs (the
shapes the manifest renders) and (b) the **manifest-model additions** the framework needs. Field names/types
mirror `operations-service`'s `api/*Dtos.java` exactly so result rendering matches the wire.

## Surfaced operations entities (read/response shapes)

- **Item** (`ItemResponse`): `id` (uuid), `sku`, `name`, `type` (RAW_MATERIAL|COMPONENT|FINISHED_GOOD|KIT),
  `baseUom` (code), `valuationMethod` (AVCO|FIFO), `perishable` (bool), `standardCost` (decimal).
- **Unit of Measure** (`UomResponse`): `id`, `code`, `family` (MASS|COUNT|LENGTH|VOLUME|OTHER).
- **Location** (`LocationResponse`): `id`, `code`, `name`. *(No list endpoint — created via POST only.)*
- **Availability / Stock Level** (`AvailabilityResponse`): `itemId`, `locationId`, `lotId?`, `onHand` (dec),
  `reserved` (dec), `available` (dec = onHand − reserved). *One row per location (per lot). This is also the
  reservations view.*
- **Movement** (`MovementResponse`): `id`, `itemId`, `locationId`, `lotId?`, `type`, `quantity` (dec),
  `unitCost` (dec), `reason`, `sourceType`, `sourceId`, `occurredAt` (instant).
- **Component Requirement** (`ComponentRequirementResponse`, from explosion): `componentItemId`,
  `requiredQuantity` (dec), `uom`. *Flat list — already exploded/summed by the backend.*
- **BOM** (`BomResponse`, from create): `id`, `parentItemId`, `type` (BomType), `outputQuantity` (dec).
- **Build** (`BuildResponse`, from create): `id`, `finishedItemId`, `quantity` (dec), `status`.
- **Sales Order** (`SalesOrderResponse`, from create/lifecycle): `id`, `customerRef`, `status`,
  `lines[]` = **Sales Line** (`itemId`, `quantity`, `unitPrice`, `reservedQty`, `fulfilledQty`).
- **Goods Receipt** (`GoodsReceiptResponse`, from create): `id`, `supplierRef`, `locationId`.
- **Cost & Margin** (`CostMargin`, from `GET /items/{id}/cost`): the item's cost breakdown + margin vs. an
  optional sale price (single object; fields rendered as returned).

## Request shapes (write forms)

- **Create Item** (`ItemCreateRequest`): `sku*`, `name*`, `type*` (enum), `baseUom*` (uom code),
  `valuationMethod?` (enum), `perishable?` (bool).
- **Create UoM** (`UomCreateRequest`): `code*`, `family*` (enum).
- **Create Conversion** (`ConversionCreateRequest`): `fromUom*`, `toUom*`, `factor*` (dec).
- **Create Location** (`LocationCreateRequest`): `code*`, `name*`.
- **Post Adjustment** (`AdjustmentRequest`): `itemId*` (ref), `locationId*` (ref/id), `lotId?`, `quantity*` (dec),
  `uom?`, `reason*`.
- **Create BOM** (`BomCreateRequest`): `parentItemId*` (ref), `type*` (enum), `outputQuantity*` (dec),
  `components[]` = { `componentItemId*` (ref), `quantity*` (dec), `uom?` }.
- **Create Build** (`BuildRequest`): `finishedItemId*` (ref), `locationId*`, `quantity*` (dec).
- **Create Sales Order** (`SalesOrderCreateRequest`): `customerRef*`, `lines[]` = { `itemId*` (ref),
  `quantity*` (dec), `uom?`, `unitPrice*` (dec) }.
- **Post Goods Receipt** (`GoodsReceiptRequest`): `supplierRef*`, `locationId*`,
  `lines[]` = { `itemId*` (ref), `lotCode?`, `expiryDate?`, `quantity*` (dec), `uom?`, `unitCost*` (dec) }.

*Validation shown here is the required (`*`) set derived from the DTO's `@NotBlank`/`@NotNull`; the UI blocks on
these before calling the edge. Deeper domain validation stays server-side and is surfaced as an error result.*

## Manifest-model additions (frontend types)

Extends 011's `src/services/types.ts` `InputField` (contracts/service-manifest.md), backward-compatible:

- **`type: "reference"`** — a select whose options load from a list endpoint. New optional fields on `InputField`:
  - `source: { path: string; valueKey: string; labelKeys: string[]; labelSep?: string }`
    e.g. `{ path: "/api/operations/items", valueKey: "id", labelKeys: ["sku","name"], labelSep: " — " }`.
- **`type: "select"`** — unchanged; used for enums with static `options`.
- **List forms** (`components[]`, `lines[]`): an input may be a **repeatable group** of fields
  (`type: "list"` with a nested `fields: InputField[]`), rendered as add/remove rows. *(Minimal: enough for BOM
  components and sales-order/receipt lines.)*

These additions are part of the shared framework (they benefit every per-service UI), specified in
[contracts/workspace-framework-extensions.md](./contracts/workspace-framework-extensions.md).

## Notes

- All ids are UUIDs; the reference picker hides them behind SKU/name labels, and result tables resolve item
  UUIDs → `SKU — name` from the items list (locations have no list, so they stay as ids).
- Monetary/decimal values are displayed exactly as returned; the UI performs no costing/valuation arithmetic.
