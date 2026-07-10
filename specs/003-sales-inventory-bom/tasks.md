---

description: "Task list for the Sales/Inventory/BOM operations service"
---

# Tasks: Sales, Inventory, and Bill-of-Materials Backend Service

**Input**: Design documents from `/specs/003-sales-inventory-bom/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: INCLUDED (constitution Principle II — TDD, NON-NEGOTIABLE; plan Testing). Contract
tests (OpenAPI), Testcontainers integration/migration tests, a concurrency no-oversell test, and
unit tests are written first and must fail before implementation.

**Module**: one Gradle module `backend/operations-service` (Java 21 / Spring Boot 3.3 / JPA /
Flyway / PostgreSQL), behind the gateway at `/api/operations`. Money/quantities are exact decimal.

**Story order (by dependency, priority in labels)**: US1(P1) → US2(P1) → US3(P2) → US6(P1) →
US4(P2) → US7(P2) → US8(P2) → US5(P3). US6/US7/US8 depend on US3 (BOM), so US3 precedes them.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable (different files, no dependency on incomplete tasks)

---

## Phase 1: Setup

- [X] T001 Register module: add `include(":operations-service")` to `backend/settings.gradle.kts` and create `backend/operations-service/` structure per plan.md
- [X] T002 Create `backend/operations-service/build.gradle.kts` with Spring Web, Data JPA, Flyway, Validation, Actuator, PostgreSQL driver, and test deps (Spring Boot Test, Testcontainers postgresql)
- [X] T003 [P] Create `@SpringBootApplication` main class + `src/main/resources/application.yml` (datasource, Flyway, Actuator health/info, env-overridable) and package skeleton (`catalog/ inventory/ bom/ production/ sales/ costing/ party/ common/ api/`)
- [X] T004 [P] Create `backend/operations-service/Dockerfile` (multi-stage JVM build, `EXPOSE 8083`, actuator healthcheck)
- [X] T005 [P] Copy `specs/003-sales-inventory-bom/contracts/operations-openapi.yaml` to `contracts/operations-openapi.yaml` and into the module resources for contract tests
- [X] T006 Add `operations-service` to `docker-compose.yml` (env: DATABASE_*, PARTY_SERVICE_URL; private) and a `/api/operations/**` route in `backend/gateway/src/main/resources/application.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: complete before user stories.

- [X] T007 Implement `common/` money & quantity decimal support (BigDecimal helpers, scales/rounding) and a global RFC-9457 `Problem` exception handler
- [ ] T008 [P] Configure structured JSON logging (Logback) and expose Actuator `/health` + `/info` (version)
- [ ] T009 [P] Define `party/PartyClient` port (`validateCustomer`/`validateSupplier`) + HTTP adapter (`PARTY_SERVICE_URL`) + an in-memory fake for tests, per contracts/party-integration.md
- [X] T010 [P] Create Testcontainers PostgreSQL base test class in `src/test/java/.../support/`
- [X] T011 [P] Create an OpenAPI contract-test harness that validates responses against `operations-openapi.yaml`

**Checkpoint**: module builds, DB/test harness + party port ready.

---

## Phase 3: User Story 1 - Manage inventory items and stock (Priority: P1) 🎯 MVP

**Goal**: item/UoM catalog + stock tracked by item×location×lot via an auditable movement ledger;
availability queryable; no negative stock.

**Independent Test**: create items/UoM, adjust/seed stock, query availability and movements;
on-hand reconciles to Σ movements; issuing below zero is rejected.

### Tests (write first, must FAIL) ⚠️

- [X] T012 [P] [US1] Contract test for items/availability/movements/adjustments endpoints vs OpenAPI in `src/test/.../contract/`
- [X] T013 [P] [US1] Testcontainers integration test: migrations apply; on-hand = Σ movements (reconciliation, SC-001)
- [X] T014 [P] [US1] Unit test: UoM conversions (kg↔g, tray↔pcs, m↔cm)
- [X] T015 [P] [US1] Integration test: issue/adjustment driving on-hand < 0 is rejected (FR-004)

### Implementation

- [X] T016 [US1] Flyway `V1__catalog_inventory.sql`: `unit_of_measure`, `uom_conversion`, `item`, `stock_location`, `lot`, `stock_level`, `stock_movement` (NUMERIC money/qty; indexes on item/location/lot/occurred_at)
- [X] T017 [P] [US1] JPA entities + repositories for catalog: `Item`, `UnitOfMeasure`, `UomConversion`
- [X] T018 [P] [US1] JPA entities + repositories for inventory: `StockLocation`, `Lot`, `StockLevel`, `StockMovement`
- [X] T019 [US1] `UomConversionService` (convert to item base UoM; reject cross-family)
- [X] T020 [US1] `StockLedgerService`: record a movement and update the cached `StockLevel` atomically in one transaction; enforce non-negative on-hand
- [X] T021 [US1] Catalog + inventory controllers in `api/`: create/list items & UoM, get availability, list movements (period filter), post adjustment — conforming to OpenAPI (makes T012 pass)
- [ ] T022 [US1] Wire structured audit logging for stock changes

**Checkpoint**: inventory ledger works standalone. **MVP reached.**

---

## Phase 4: User Story 2 - Sales orders that consume inventory (Priority: P1)

**Goal**: sales orders reserve available stock on confirm, decrement on fulfill, never oversell;
customer validated via Party.

**Independent Test**: confirm reserves (available drops), fulfill decrements on-hand, cancel
releases; concurrent confirms for the last unit → exactly one wins; oversell rejected.

### Tests (write first, must FAIL) ⚠️

- [X] T023 [P] [US2] Contract test for sales-order endpoints (create/confirm/fulfill/cancel) vs OpenAPI
- [X] T024 [P] [US2] Integration: confirm reserves & drops available; fulfill decrements on-hand; cancel releases (FR-006/007)
- [X] T025 [P] [US2] Concurrency test: N parallel confirms for the last available unit → exactly one succeeds (SC-002)
- [X] T026 [P] [US2] Integration: confirm exceeding available is rejected (no oversell, FR-008); invalid/inactive customer rejected (FR-014, SC-005)

### Implementation

- [X] T027 [US2] Flyway `V2__sales.sql`: `sales_order`, `sales_order_line`, `reservation`
- [X] T028 [P] [US2] JPA entities + repositories: `SalesOrder`, `SalesOrderLine`, `Reservation`
- [X] T029 [US2] `ReservationService`: `SELECT … FOR UPDATE` reserve/release per contracts/reservation-model.md
- [X] T030 [US2] `SalesOrderService`: lifecycle (draft→confirmed→fulfilled→closed / cancelled) with partial fulfillment; validate customer via `PartyClient`
- [X] T031 [US2] Sales-order controller + lifecycle action endpoints (makes T023 pass)

**Checkpoint**: stocked-goods sales with safe reservations.

---

## Phase 5: User Story 3 - Define bills of materials (Priority: P2)

**Goal**: KIT/phantom and MANUFACTURED BOMs, multi-level explosion, cycle detection.

**Independent Test**: define a multi-level BOM; explode for a quantity → correct component totals;
a cycle is rejected.

### Tests (write first, must FAIL) ⚠️

- [X] T032 [P] [US3] Contract test for BOM endpoints (create, explosion) vs OpenAPI
- [X] T033 [P] [US3] Unit test: multi-level explosion multiplies quantities correctly (FR-012)
- [X] T034 [P] [US3] Unit test: cycle detection rejects an item that contains itself (FR-011)

### Implementation

- [X] T035 [US3] Flyway `V3__bom.sql`: `bill_of_materials` (type KIT/MANUFACTURED, output_quantity), `bom_component`
- [X] T036 [P] [US3] JPA entities + repositories: `BillOfMaterials`, `BomComponent`
- [X] T037 [US3] `BomService`: create with cycle check; recursive explosion with UoM conversion
- [X] T038 [US3] BOM controller (create, explosion) (makes T032 pass)

**Checkpoint**: BOM structures defined and explodable.

---

## Phase 6: User Story 6 - Sell a kit/recipe that deducts components (Priority: P1)

**Goal**: selling a KIT item consumes its exploded components (UoM-converted), not a finished-good
count.

**Independent Test**: electrical-set and tapsilog kits — selling 1 deducts exact component
quantities; component shortage rejects.

### Tests (write first, must FAIL) ⚠️

- [X] T039 [P] [US6] Integration: selling 1 electrical set deducts enclosure + 4 breakers; no finished stock (SC-009)
- [X] T040 [P] [US6] Integration: tapsilog order deducts 250g rice, 200g tapa, 1 egg (UoM conversions applied)
- [X] T041 [P] [US6] Integration: kit sale with a short component is rejected naming it (FR-025)

### Implementation

- [X] T042 [US6] Extend `SalesOrderService`: for KIT lines, explode to components and reserve/consume components (FEFO for perishable) instead of a finished-good count

**Checkpoint**: kit/recipe selling consumes raw materials correctly.

---

## Phase 7: User Story 4 - Replenish inventory from suppliers (Priority: P2)

**Goal**: goods receipts increase on-hand, record RECEIPT movements with cost, validate supplier,
and update AVCO average cost.

**Independent Test**: post a receipt → on-hand up, movement recorded with unit cost, supplier
validated; AVCO running average recomputed.

### Tests (write first, must FAIL) ⚠️

- [X] T043 [P] [US4] Contract test for receipts endpoint vs OpenAPI
- [X] T044 [P] [US4] Integration: receipt increases on-hand + RECEIPT movement; invalid supplier rejected (FR-013/014)
- [X] T045 [P] [US4] Unit test: AVCO running-average recompute on successive receipts (contracts/costing-model.md)

### Implementation

- [X] T046 [US4] Flyway `V4__receipts.sql`: `goods_receipt`, `receipt_line`
- [X] T047 [P] [US4] JPA entities + repositories: `GoodsReceipt`, `ReceiptLine`
- [X] T048 [US4] `ValuationService` (AVCO): recompute item average on receipt; stamp movement unit cost
- [X] T049 [US4] `GoodsReceiptService` + controller; validate supplier via `PartyClient` (makes T043 pass)

**Checkpoint**: inbound stock + weighted-average costing.

---

## Phase 8: User Story 7 - Produce/assemble finished goods (Priority: P2)

**Goal**: an atomic build consumes a MANUFACTURED BOM's components and produces finished stock.

**Independent Test**: build 5 dresses → 8.5m cloth + thread consumed, 5 dresses added, atomically;
short component → whole build fails (no partial).

### Tests (write first, must FAIL) ⚠️

- [X] T050 [P] [US7] Integration: build consumes exploded components and increases finished on-hand atomically (SC-010, dresses example)
- [X] T051 [P] [US7] Integration: build with a short component fails wholesale, no partial consumption (FR-026)

### Implementation

- [X] T052 [US7] Flyway `V5__build.sql`: `build`
- [X] T053 [P] [US7] JPA entity + repository: `Build`
- [X] T054 [US7] `BuildService`: atomic consume (BUILD_CONSUME) + produce (BUILD_PRODUCE); finished unit cost = Σ consumed component costs / qty; controller endpoint

**Checkpoint**: make-to-stock production works.

---

## Phase 9: User Story 8 - Roll up cost and margin (Priority: P2)

**Goal**: per-item valuation (AVCO default; FIFO+FEFO for perishables), BOM cost roll-up, and
margin.

**Independent Test**: rolled-up cost = Σ component costs (multi-level); margin correct; FIFO items
consume/cost FEFO across lots; expired excluded.

### Tests (write first, must FAIL) ⚠️

- [X] T055 [P] [US8] Unit test: multi-level BOM cost roll-up equals hand calculation (SC-011)
- [X] T056 [P] [US8] Unit test: margin = (price − cost) and profit% exact (FR-029, SC-007)
- [X] T057 [P] [US8] Integration: FIFO item consumed FEFO across lots, each costed at its lot cost; expired lot excluded (FR-031)

### Implementation

- [X] T058 [US8] Extend `ValuationService`: FIFO lot costing + FEFO lot selection (skip expired)
- [X] T059 [US8] `CostingService`: recursive rolled-up `cost(item)` from components; margin computation
- [X] T060 [US8] Cost/margin controller endpoint `/items/{id}/cost` (makes contract pass)
- [X] T061 [US8] Apply FEFO consumption in reservation/consume paths for perishable items (integrate with US2/US6)

**Checkpoint**: costing and margin from raw materials.

---

## Phase 10: User Story 5 - Availability & movement data for other domains (Priority: P3)

**Goal**: expose availability and period movement data for the Accounting/reporting consumers.

**Independent Test**: availability reconciles (on-hand/reserved/available); movement period query
returns the data needed to value inventory.

### Tests (write first, must FAIL) ⚠️

- [X] T062 [P] [US5] Integration: availability reconciles with movements + reservations for every item (SC-006)
- [X] T063 [P] [US5] Integration: movement period query returns valuation-ready data (SC-008)

### Implementation

- [X] T064 [US5] Ensure availability endpoint returns on-hand/reserved/available per item×location; add movement period query params
- [X] T065 [US5] Document the data contract the Accounting feature consumes (in contracts/costing-model.md reference)

**Checkpoint**: downstream domains can consume the data.

---

## Phase 11: Polish & Cross-Cutting Concerns

- [ ] T066 [P] Add DB indexes/constraints review migration for hot paths (stock_movement by item+occurred_at; unique stock_level by item×location×lot)
- [ ] T067 [P] Wire `operations-service` into `make test`/`make lint` and the CI workflow (Spotless/Checkstyle + tests)
- [ ] T068 Run quickstart.md end-to-end (catalog → receipt → kit sale → build → costing → availability) and record results
- [ ] T069 [P] Add a secret-leak check and confirm no secrets in config/logs (SC/constitution)
- [ ] T070 [P] Map Success Criteria SC-001..SC-011 to their covering tests in `src/test/README.md`

---

## Dependencies & Execution Order

- **Setup (1)** → **Foundational (2)** → user stories.
- **US1 (P1)** first — the catalog + ledger everything builds on.
- **US2 (P1)** needs US1 + Party port. **US3 (P2)** needs US1.
- **US6 (P1)** needs US1 + US2 + US3 (kit sale = sales flow over BOM explosion).
- **US4 (P2)** needs US1 (adds receipts + AVCO). **US7 (P2)** needs US1 + US3.
- **US8 (P2)** needs US1 (movement cost), US3 (roll-up), US4 (AVCO); integrates FEFO into US2/US6.
- **US5 (P3)** needs data from US1–US4.
- **Polish (11)** last.

### Within a story

Tests written and FAILING before implementation (TDD). Migration + entities before services;
services before controllers; reservation/consumption logic before higher-level flows.

### Parallel Opportunities

- Setup: T003–T005 · Foundational: T007–T011
- Per story, the `[P]` test tasks run together; entity tasks (e.g., T017/T018, T028) run together
- US1 tests T012–T015; US2 tests T023–T026; US8 tests T055–T057 are parallel batches

---

## Implementation Strategy

### MVP First (US1)

Setup → Foundational → US1: a working inventory ledger (items, stock, movements, availability,
no negative stock). Validate independently, then layer sales (US2), BOM (US3), kits (US6),
receipts+AVCO (US4), production (US7), costing (US8), and query surface (US5).

### Notes

- [P] = different files, no dependencies.
- Verify each test fails before implementing; commit after each task/logical group and push per
  project workflow.
- Data-integrity tasks (reservations, no-negative-stock, exact decimals, atomic builds) are the
  highest-risk — keep their tests rigorous (constitution: Security & Data Integrity First).
