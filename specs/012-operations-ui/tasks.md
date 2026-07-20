---
description: "Task list for 012-operations-ui"
---

# Tasks: Operations Service UI

**Input**: Design documents from `/specs/012-operations-ui/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — the constitution mandates TDD. Frontend = Vitest + Testing Library (red-first per story);
backend = MockMvc contract tests (red-first) for each new read endpoint (FR-015).

**Organization**: By user story. **Evolves the 011 `frontend/`** (manifest + shared workspace-framework
additions) and makes a **bounded, read-only** addition to `backend/operations-service` (FR-015). Two contracts:
[operations-manifest.md](./contracts/operations-manifest.md) (frontend functions),
[operations-read-api.md](./contracts/operations-read-api.md) (new backend GETs),
[workspace-framework-extensions.md](./contracts/workspace-framework-extensions.md) (record picker / list / id→label).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different files, no dependency on incomplete tasks.

---

## Phase 1: Setup

- [X] T001 [P] Create `frontend/src/services/manifests/operations.ts` and point the `operations` entry in `frontend/src/services/registry.ts` at it — migrate the existing `items` reference function into the new manifest module (registry keeps only wiring)
- [X] T002 [P] Backend baseline sanity: `cd backend && ./gradlew :operations-service:compileJava :operations-service:compileTestJava` green before any change

---

## Phase 2: Foundational (Blocking framework additions used by US1–US5)

**⚠️ Blocks the user stories — the reference picker + id→label are needed as early as US1.**

- [X] T003 [P] Extend `frontend/src/services/types.ts`: add the `"reference"` `InputField` kind (`source: {path, valueKey, labelKeys, labelSep?}`) and the `"list"` kind (`fields: InputField[]`, `minRows?`) per contracts/workspace-framework-extensions.md — backward-compatible with 011's existing fields
- [X] T004 [P] Write `frontend/tests/ReferenceInput.test.tsx` (red): the picker loads options once from a list endpoint (mock the edge), filters client-side (type-ahead), caps rendered options, blocks Run when required + empty, and submits the underlying id
- [X] T005 Implement `frontend/src/workspace/inputs/ReferenceInput.tsx` — searchable picker: load-once via `src/api/edge.ts`, client-side type-ahead filter + render cap (FR-017), own loading/error/empty state, required-validation, submits `valueKey`
- [X] T006 Wire `frontend/src/workspace/FunctionWorkspace.tsx` to render the `reference` input kind and add the id→label result helper (`frontend/src/workspace/result/idLabels.ts`) that resolves item UUID columns → `SKU — name` from the cached items list
- [X] T007 Extend `frontend/tests/Workspace.test.tsx` (red→green): `reference` input renders/validates; result tables show `SKU — name` for item columns; existing 011 assertions still pass

**Checkpoint**: the record picker + id→label work; manifests can now declare reference inputs and readable tables.

---

## Phase 3: User Story 1 - Browse the catalog and current stock (Priority: P1) 🎯 MVP

**Goal**: Items list, item detail, and multi-location stock on hand — read-only, via the edge.

**Independent Test**: Operations → Items lists items; Item detail (pick an item) shows its attributes; Stock on
hand (pick an item) shows onHand/reserved/available per location; loading/empty/error states are distinct.

### Backend read (FR-015)

- [X] T008 [US1] Write `backend/operations-service/src/test/java/.../api/ItemReadContractTest` (MockMvc, red): `GET /items/{id}` returns the `ItemResponse` for a created item and **404** when absent
- [X] T009 [US1] Implement `GET /items/{id}` — add `getItem(id)` to `catalog/CatalogService.java` (`ItemRepository.findById` → existing `toResponse`) and the handler in `api/CatalogController.java` (404 on absent)

### Frontend

- [X] T010 [P] [US1] Write `frontend/tests/OperationsManifest.test.tsx` (red): the `items`, `item`, and `stock` functions render and run against a mocked edge; `stock` renders the availability table (onHand/reserved/available)
- [X] T011 [US1] Add the **Catalog** `items` + `item` (detail, `reference→items`) and **Inventory** `stock` (`reference→items`) functions to `frontend/src/services/manifests/operations.ts` per operations-manifest.md
- [X] T012 [US1] Verify US1: `./gradlew :operations-service:build` (item-detail contract) + `cd frontend && npm test && npm run build` green

**Checkpoint**: MVP — browse items, open an item, see its stock, all through the edge.

---

## Phase 4: User Story 2 - Trace inventory movements and reservations (Priority: P2)

**Goal**: The movement ledger for an item + the reservations view (availability), plus a Locations list (also
used to resolve location ids).

**Independent Test**: Movement ledger (pick an item, optional from/to) renders in time order; Stock on hand shows
reserved/available (reservations); Locations lists the client's locations.

### Backend read (FR-015)

- [X] T013 [US2] Write `backend/operations-service/src/test/java/.../api/LocationReadContractTest` (MockMvc, red): `GET /locations` returns created locations (empty `[]` when none)
- [X] T014 [US2] Implement `GET /locations` — add `listLocations()` to `inventory/InventoryService.java` (`StockLocationRepository.findAll`) and the handler in `api/InventoryController.java` (`LocationResponse[]`)

### Frontend

- [X] T015 [P] [US2] Extend `frontend/tests/OperationsManifest.test.tsx` (red): `movements` renders the ledger table; `locations` renders the locations table
- [X] T016 [US2] Add the **Inventory** `movements` (`reference→items` + optional `from`/`to`) and `locations` functions to the manifest; wire location id→label resolution (from the locations list) into result tables in `frontend/src/workspace/result/idLabels.ts`
- [X] T017 [US2] Verify US2: `:operations-service:build` (locations contract) + `npm test && npm run build` green

**Checkpoint**: stock questions are answerable — ledger, reservations (availability), and locations.

---

## Phase 5: User Story 3 - Explode a bill of materials (Priority: P2)

**Goal**: BOM explosion for a manufactured item as a flat requirements table; cyclic BOM → clear error.

**Independent Test**: BOM explosion (pick a manufactured item, optional quantity) renders the flat component
requirements; a cyclic BOM shows a clear "cycle detected" error and never hangs. (Endpoint already exists.)

### Frontend (no backend change)

- [X] T018 [P] [US3] Extend `frontend/tests/OperationsManifest.test.tsx` (red): `bom-explosion` renders the flat requirements table on success and the error state on a cycle/error response (mock the edge)
- [X] T019 [US3] Add the **BOM** `bom-explosion` function (`reference→items` parent + optional `quantity`, result `table`) to the manifest; item columns resolve via id→label
- [X] T020 [US3] Verify US3: `npm test && npm run build` green

**Checkpoint**: the signature manufacturing view works end-to-end.

---

## Phase 6: User Story 4 - Record operations + list/view orders, builds, receipts (Priority: P3)

**Goal**: The write actions (create item/uom/conversion/location, adjustment, BOM, build, sales order + lifecycle,
goods receipt) and the new **read** endpoints that make sales orders / builds / goods receipts listable/viewable
(FR-015/FR-016). Requires the **list** input for array bodies (BOM components, order/receipt lines).

**Independent Test**: Create an item → it appears in Items; post an adjustment → Stock/Movements reflect it; run a
build → it appears in Builds and stock reflects it; create a sales order → it appears in Sales orders with lines
+ status; confirm/fulfill/cancel transitions status; post a receipt → it appears in Goods receipts.

### Backend reads (FR-015)

- [X] T021 [P] [US4] Write `SalesOrderReadContractTest` (MockMvc, red): `GET /sales-orders` lists created orders; `GET /sales-orders/{id}` returns one or **404**
- [X] T022 [P] [US4] Write `BuildReadContractTest` (MockMvc, red): `GET /builds` lists; `GET /builds/{id}` returns one or **404**
- [X] T023 [P] [US4] Write `GoodsReceiptReadContractTest` (MockMvc, red): `GET /receipts` lists (with `lines`/`receivedAt`); `GET /receipts/{id}` returns one or **404**
- [X] T024 [US4] Implement sales-order reads — `list()`/`get(id)` in `sales/SalesOrderService.java` + `GET /sales-orders` and `/{id}` in `api/SalesOrderController.java` (existing `toResponse`, 404 on absent)
- [X] T025 [US4] Implement build reads — `list()`/`get(id)` in `production/BuildService.java` + `GET /builds` and `/{id}` in `api/BuildController.java`
- [X] T026 [US4] Implement goods-receipt reads — `list()`/`get(id)` in `procurement/GoodsReceiptService.java` + `GET /receipts` and `/{id}` in `api/GoodsReceiptController.java`; **additively** extend `GoodsReceiptResponse` in `api/ReceiptDtos.java` with `lines[]` + `receivedAt` (no change to the create path's other behavior)

### Frontend

- [X] T027 [P] [US4] Write `frontend/tests/ListInput.test.tsx` (red): the repeatable list input adds/removes rows, each row rendering its nested fields (incl. `reference`), submits an array, and a required list blocks Run until it has a valid row
- [X] T028 [US4] Implement `frontend/src/workspace/inputs/ListInput.tsx` and render the `list` kind in `FunctionWorkspace.tsx`
- [X] T029 [P] [US4] Extend `frontend/tests/OperationsManifest.test.tsx` (red): the create-* forms validate required inputs and run; `sales-orders`/`builds`/`receipts` render lists and `*-detail` renders one (mock the edge)
- [X] T030 [US4] Add the **write** functions (`create-item`, `create-uom`, `create-conversion`, `create-location`, `post-adjustment`, `create-bom` [list], `create-build`, `create-sales-order` [list], `confirm/fulfill/cancel-sales-order`, `post-receipt` [list]) and the **read** functions (`sales-orders`, `sales-order`, `builds`, `build`, `receipts`, `receipt`) to `frontend/src/services/manifests/operations.ts` per operations-manifest.md (enum selects for ItemType/UomFamily/ValuationMethod/BomType)
- [X] T031 [US4] Verify US4: `:operations-service:build` (the 3 read contract tests) + `npm test && npm run build` green; create→list round-trips work against the mocked edge

**Checkpoint**: the full write surface + the new reads — every create/lifecycle action is verifiable via a read.

---

## Phase 7: User Story 5 - Costing and margin (Priority: P3)

**Goal**: An item's cost/valuation and margin (optional sale price) as a detail view.

**Independent Test**: Cost & margin (pick an item, optional sale price) renders the cost/margin detail; values are
shown exactly as returned. (Endpoint already exists.)

### Frontend (no backend change)

- [X] T032 [P] [US5] Extend `frontend/tests/OperationsManifest.test.tsx` (red): `cost` renders the cost/margin detail from a mocked edge response
- [X] T033 [US5] Add the **Costing** `cost` function (`reference→items` + optional `salePrice`, result `detail`) to the manifest
- [X] T034 [US5] Verify US5: `npm test && npm run build` green

**Checkpoint**: the analytical payoff — costing + margin — completes the Operations workspace.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T035 [P] Accessibility/responsive pass across the new inputs/results: the reference picker + list input are keyboard-navigable with visible focus, and result tables scroll (not the page) at the 011 768px floor
- [X] T036 [P] Docs: extend `frontend/README.md` (Operations manifest + record picker) and note the FR-015 read endpoints in `backend/operations-service/README.md`
- [X] T037 [P] Confirm CI covers both streams: the 011 `frontend` job runs the new Vitest suites; the backend job runs `:operations-service:build` (the new contract tests) — adjust `.github/workflows/ci.yml` only if a gap exists
- [X] T038 Full verification: `cd backend && ./gradlew :operations-service:build` + `cd frontend && npm test && npm run build` green; run `quickstart.md` end-to-end (reads → writes → list/verify) with 0 real cloud

---

## Dependencies & Execution Order

- **Setup (T001–T002)** → **Foundational (T003–T007)** → user stories.
- **US1 (T008–T012)**: after Foundational (needs the reference picker). MVP. Adds `GET /items/{id}`.
- **US2 (T013–T017)**: after Foundational; independent of US1. Adds `GET /locations`.
- **US3 (T018–T020)**: after Foundational; frontend-only (explosion endpoint exists).
- **US4 (T021–T031)**: after Foundational; adds the list input + the sales-order/build/receipt reads. Largest slice.
- **US5 (T032–T034)**: after Foundational; frontend-only (cost endpoint exists).
- **Polish (T035–T038)**: after the desired stories.

### Within each story
- Backend contract test ([P]) and frontend manifest test ([P]) are written first (red), then the implementation, then verify.

### Parallel Opportunities
- Setup: T001 ∥ T002.
- Foundational: T003 ∥ T004 (then T005→T006→T007).
- US4 backend contract tests T021 ∥ T022 ∥ T023; frontend T027 ∥ T029.
- Polish: T035 ∥ T036 ∥ T037.

---

## Implementation Strategy

### MVP First (US1)
Setup → Foundational → US1 → **STOP & VALIDATE**: browse items, open an item, see multi-location stock — the
first per-service UI proving the manifest + record picker + a new backend read end-to-end.

### Incremental Delivery
US1 (catalog+stock) → US2 (movements/reservations/locations) → US3 (BOM explosion) → US4 (writes + order/build/
receipt reads) → US5 (costing). Each is independently testable; US3 and US5 add no backend code.

---

## Notes
- **Backend change is read-only + additive** (FR-014/FR-015): new GET handlers + `list()/get(id)` service methods
  on existing repositories/DTOs; tenant-scoped by the 008 schema; no existing endpoint or write logic changes.
- **Record picker + list inputs live in the shared framework** — every later per-service UI (013–016) reuses them.
- Monetary/decimal values are displayed exactly as returned; the UI performs no costing/valuation arithmetic.
- Commit after each story (or logical group); simple messages, no AI attribution.
