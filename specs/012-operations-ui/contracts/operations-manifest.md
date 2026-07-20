# Contract — Operations service manifest

The concrete manifest that fills the 011 Operations tab. Every function maps to a **real** operations-service
endpoint (Phase 0 D1) under `basePath: "/api/operations"`, rendered by the 011 `FunctionWorkspace` and called
via the generic authenticated edge fetch. `{param}` tokens are filled from inputs. Grouping = left-pane order.

## Reads

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `items` | Items | GET | `/items` | — | table (Catalog list = item detail source) |
| `stock` | Stock on hand | GET | `/items/{id}/availability` | `id`: reference→items | table (onHand/reserved/available per location; **the reservations view**) |
| `movements` | Movement ledger | GET | `/movements?itemId={itemId}&from={from}&to={to}` | `itemId`: reference→items; `from?`,`to?`: text (ISO datetime) | table |
| `bom-explosion` | BOM explosion | GET | `/boms/{parentItemId}/explosion?quantity={quantity}` | `parentItemId`: reference→items; `quantity?`: number (default 1) | table (flat requirements; cyclic → clear error) |
| `cost` | Cost & margin | GET | `/items/{id}/cost?salePrice={salePrice}` | `id`: reference→items; `salePrice?`: number | detail |

## Writes

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `create-item` | New item | POST | `/items` | `sku*`,`name*` text; `type*` select(ItemType); `baseUom*` text; `valuationMethod?` select(AVCO/FIFO); `perishable?` boolean | detail (created item) |
| `create-uom` | New unit of measure | POST | `/uoms` | `code*` text; `family*` select(UomFamily) | detail |
| `create-conversion` | New UoM conversion | POST | `/uom-conversions` | `fromUom*`,`toUom*` text; `factor*` number | message |
| `create-location` | New location | POST | `/locations` | `code*`,`name*` text | detail |
| `post-adjustment` | Stock adjustment | POST | `/adjustments` | `itemId*` ref→items; `locationId*` text; `lotId?` text; `quantity*` number; `uom?` text; `reason*` text | detail (movement) |
| `create-bom` | New bill of materials | POST | `/boms` | `parentItemId*` ref→items; `type*` select(BomType); `outputQuantity*` number; `components*` list of { `componentItemId*` ref→items, `quantity*` number, `uom?` text } | detail |
| `create-build` | Production build | POST | `/builds` | `finishedItemId*` ref→items; `locationId*` text; `quantity*` number | detail (build + status) |
| `create-sales-order` | New sales order | POST | `/sales-orders` | `customerRef*` text; `lines*` list of { `itemId*` ref→items, `quantity*` number, `unitPrice*` number, `uom?` text } | detail (order + lines + status) |
| `confirm-sales-order` | Confirm sales order | POST | `/sales-orders/{id}/confirm` | `id*` text | detail |
| `fulfill-sales-order` | Fulfill sales order | POST | `/sales-orders/{id}/fulfill` | `id*` text | detail |
| `cancel-sales-order` | Cancel sales order | POST | `/sales-orders/{id}/cancel` | `id*` text | detail |
| `post-receipt` | Goods receipt | POST | `/receipts` | `supplierRef*` text; `locationId*` text; `lines*` list of { `itemId*` ref→items, `quantity*` number, `unitCost*` number, `lotCode?`,`expiryDate?`,`uom?` text } | detail |

## Left-pane grouping

- **Catalog**: Items, New item, New unit of measure, New UoM conversion
- **Inventory**: Stock on hand, Movement ledger, New location, Stock adjustment
- **BOM**: BOM explosion, New bill of materials
- **Production**: Production build
- **Sales**: New sales order, Confirm / Fulfill / Cancel sales order
- **Costing**: Cost & margin
- **Receiving**: Goods receipt

## Rules

- `reference→items` inputs load options from `GET /api/operations/items` (value=`id`, label=`sku — name`); see
  [workspace-framework-extensions.md](./workspace-framework-extensions.md).
- Required (`*`) inputs block the call with inline validation (011 behavior).
- Result tables resolve `itemId`/`componentItemId` → `SKU — name` from the items list; unknown/location UUIDs
  render as-is.
- Sales-order/build/receipt/bom/location have no GET; their function renders the **action response**. Effects on
  stock are observable via **Stock on hand** and **Movement ledger** (which do have GETs).
- A cyclic BOM or any domain error surfaces through 011's error state with the backend's message.

## Acceptance

- Opening Operations shows all groups above; each read returns real data (or a clear empty/error) via the edge.
- `stock` shows onHand/reserved/available (reservations); `movements` lists the ledger; `bom-explosion` lists
  component requirements or a cycle error; `cost` shows the cost/margin detail.
- Each write validates required inputs, calls the correct endpoint, and renders the response; a following read
  (Items / Stock / Movements) reflects the effect where a GET exists.
