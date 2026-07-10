# Phase 1 Data Model: Sales, Inventory, and Bill-of-Materials Backend Service

**Feature**: 003-sales-inventory-bom | **Date**: 2026-07-08

Persistence model for the operations service (one PostgreSQL schema, Flyway-migrated). Money and
quantities are `NUMERIC` (never float). IDs are UUIDs. `created_at`/`updated_at` on all tables.

## Catalog

### Item
| Field | Type | Rules |
|-------|------|-------|
| id | UUID | PK |
| sku | text | unique, required |
| name | text | required |
| type | enum | `RAW_MATERIAL` \| `COMPONENT` \| `FINISHED_GOOD` \| `KIT` |
| base_uom_id | UUID → UnitOfMeasure | required |
| valuation_method | enum | `AVCO` (default) \| `FIFO` |
| perishable | boolean | default false; if true, lots carry expiry and FEFO applies |
| standard_cost | NUMERIC | optional; current unit cost (AVCO running avg maintained here) |
| active | boolean | default true; deactivation blocked while referenced (FR-019) |

### UnitOfMeasure
| id | UUID | PK |
| code | text | unique (e.g., `kg`, `g`, `pcs`, `tray`, `m`) |
| family | enum | `MASS` \| `COUNT` \| `LENGTH` \| `VOLUME` … |

### UomConversion
| id | UUID | PK |
| from_uom_id / to_uom_id | UUID | same family |
| factor | NUMERIC | 1 from_uom = factor × to_uom |
- Validation: conversions only within a family; item quantities stored in the item's base UoM.

## Inventory

### StockLocation
| id | UUID | PK | | code | text | unique | | name | text | | active | boolean |

### Lot
| id | UUID | PK |
| item_id | UUID → Item | required |
| lot_code | text | unique per item |
| expiry_date | date | nullable; required when item.perishable |
| unit_cost | NUMERIC | lot cost (used by FIFO items) |

### StockLevel  (cached, reconciles to movements — SC-001)
| id | UUID | PK |
| item_id / location_id / lot_id | UUID | (lot_id nullable for non-lot items) — unique together |
| on_hand | NUMERIC | ≥ 0 (FR-004) |
| reserved | NUMERIC | ≥ 0 and ≤ on_hand |
| — available (derived) | NUMERIC | `on_hand − reserved` |
- Concurrency: reserve/consume take `SELECT … FOR UPDATE` on the relevant rows (R2).

### StockMovement  (immutable ledger — R1)
| id | UUID | PK |
| item_id / location_id / lot_id | UUID | |
| type | enum | `RECEIPT` \| `ISSUE` \| `ADJUSTMENT` \| `TRANSFER_OUT` \| `TRANSFER_IN` \| `BUILD_CONSUME` \| `BUILD_PRODUCE` \| `SALE_ISSUE` |
| quantity | NUMERIC | signed (base UoM) |
| unit_cost | NUMERIC | cost applied at movement time (R4) |
| reason | text | |
| source_type / source_id | text/UUID | order, receipt, build, adjustment, transfer |
| occurred_at | timestamptz | |
- Invariant: Σ(quantity) for an item×location×lot = StockLevel.on_hand.

## Bill of Materials

### BillOfMaterials
| id | UUID | PK |
| parent_item_id | UUID → Item | required |
| type | enum | `KIT` (phantom, not stocked) \| `MANUFACTURED` (stocked, built) |
| output_quantity | NUMERIC | units produced per build (default 1) |
| active | boolean | one active BOM per parent (versioning deferred) |

### BomComponent
| id | UUID | PK |
| bom_id | UUID → BillOfMaterials | |
| component_item_id | UUID → Item | required; cycle-checked (FR-011) |
| quantity | NUMERIC | per output unit |
| uom_id | UUID → UnitOfMeasure | component's UoM (converted on use) |
- Explosion: recursive multiply; cycle detection on save.

## Production

### Build
| id | UUID | PK |
| finished_item_id | UUID → Item (MANUFACTURED) | |
| location_id | UUID | |
| quantity | NUMERIC | finished units to produce |
| status | enum | `COMPLETED` \| `FAILED` (atomic; no partial) |
| produced_lot_id | UUID → Lot | optional (finished-good lot) |
- On completion: BUILD_CONSUME movements for exploded components + BUILD_PRODUCE for finished good,
  one transaction; finished-good unit cost = Σ consumed component costs / quantity (R6).

## Sales

### SalesOrder
| id | UUID | PK |
| customer_ref | text/UUID | validated via Party service (FR-014) |
| status | enum | `DRAFT` → `CONFIRMED` → `FULFILLED` → `CLOSED`; `CANCELLED` |
| ordered_at / confirmed_at / fulfilled_at | timestamptz | |

### SalesOrderLine
| id | UUID | PK |
| order_id | UUID → SalesOrder | |
| item_id | UUID → Item | may be a stocked good or a KIT |
| quantity | NUMERIC | in item's UoM |
| unit_price | NUMERIC | money |
| reserved_qty / fulfilled_qty | NUMERIC | track partial fulfillment |
- Confirm: hard-reserve stock (stocked item) or reserve exploded components (KIT). Fulfill:
  issue movements; partial fulfillment allowed (remaining stays reserved). Cancel: release.

### Reservation
| id | UUID | PK |
| order_line_id | UUID | |
| item_id / location_id / lot_id | UUID | the specific reserved stock |
| quantity | NUMERIC | |
- Released on fulfillment or cancellation (FR-007).

## Procurement (inbound)

### GoodsReceipt / ReceiptLine
| GoodsReceipt: id, supplier_ref (validated via Party), location_id, received_at |
| ReceiptLine: id, receipt_id, item_id, lot_id (created/assigned), quantity, unit_cost |
- On post: RECEIPT movements increase on_hand; AVCO items recompute running average; FIFO items
  keep the lot's own cost (R4).

## Costing / valuation (derived, not a table per se)

- **Item cost**: AVCO items → `Item.standard_cost` (running average). FIFO items → per-lot cost.
- **BOM roll-up cost**: `cost(parent) = Σ over components (componentQty × cost(component))`,
  recursive; used for margin and for KIT/finished pricing analysis (US8).
- **Margin**: `profit = sale_price − rolled_up_cost`; `profit_pct = profit / sale_price` (exact).

## Party reference (external)

### PartyRef (not stored in full)
- Only IDs (`customer_ref`, `supplier_ref`) are stored on orders/receipts; validated live against
  the Party service (exists + active). No party profile columns here (Q1=A).

## Cross-entity invariants

- On-hand never negative (FR-004); reserved ≤ on-hand (SC-002 no oversell under `FOR UPDATE`).
- StockLevel reconciles to the movement ledger 100% (SC-001).
- All money/quantity exact decimals (FR-017, SC-007).
- Items referenced by stock/BOM/open orders cannot be deleted/deactivated without handling (FR-019).
- KIT items hold no finished on-hand; their sale consumes components (FR-025).
- Builds and kit sales are atomic — no partial consumption on shortage (FR-026, SC-010).
- Perishable/FIFO consumption is FEFO; expired lots excluded (FR-031).
