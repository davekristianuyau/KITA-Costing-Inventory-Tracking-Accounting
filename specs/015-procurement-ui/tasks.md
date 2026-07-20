---
description: "Task list for 015-procurement-ui"
---

# Tasks: Procurement Service UI

**Input**: Design documents from `/specs/015-procurement-ui/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — the constitution mandates TDD. Frontend = Vitest + Testing Library (red-first per story).
**No backend code and NO new framework** — every procurement read/write already exists, and PO/receipt `lines[]`
render via the **014 detail sub-table** (already merged).

**Organization**: By user story. **Evolves the 011 `frontend/` reusing the full 012/013/014 shared framework**.
Contract: [procurement-manifest.md](./contracts/procurement-manifest.md). The shared framework must be present —
**sync `main` first (T001)**.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different files, no dependency on incomplete tasks.

---

## Phase 1: Setup

- [X] T001 **Sync `main` into `015-procurement-ui`** (`git merge origin/main`) so the 012/013/014 shared framework
  (`frontend/src/workspace/inputs/{ReferenceInput,ListInput,FieldInput}.tsx`, `workspace/result/idLabels.ts`, the
  `reference`/`list` `InputField` kinds + `resultRefs`, the `bodyInput`/dotted-name body building, and the **014
  detail sub-table** in `FunctionWorkspace`) is present; resolve any conflicts (keep the 015 CLAUDE.md marker);
  confirm `cd frontend && npm test && npm run build` green
- [X] T002 [P] Create `frontend/src/services/manifests/procurement.ts` and point the `procurement` entry in `frontend/src/services/registry.ts` at it (migrate the placeholder `suppliers` function into the module)

---

## Phase 2: Foundational (Shared source for all stories)

**⚠️ Blocks the user stories — the supplier reference source is used across.**

- [X] T003 Define the shared source in `frontend/src/services/manifests/procurement.ts` — `SUPPLIERS_SOURCE` (`/api/procurement/suppliers`, value `id`, label `supplierCode — name`) and a `supplierLabels(columns)` helper for `resultRefs` (relabel `supplierId` columns) per contracts/procurement-manifest.md

**Checkpoint**: the manifest module + shared source exist; functions can be added per story.

---

## Phase 3: User Story 1 - Browse suppliers and purchase orders (Priority: P1) 🎯 MVP

**Goal**: Supplier list/detail/items/history and PO list/detail (lines as a sub-table + status) — read-only, via the edge.

**Independent Test**: Procurement → Suppliers lists suppliers; Supplier detail (pick one) shows attributes;
Purchase orders lists POs (supplier resolved); Purchase order detail (by id) shows its lines + status.

### Frontend (all endpoints already exist — no backend change)

- [X] T004 [P] [US1] Write `frontend/tests/ProcurementManifest.test.tsx` (red): the `suppliers`, `supplier`, `supplier-items`, `supplier-history`, `purchase-orders`, and `purchase-order` functions render and run against a mocked edge; `supplier` uses the reference picker sourced from `/api/procurement/suppliers`; `purchase-order` detail renders its `lines[]` as a sub-table; `purchase-orders` resolves `supplierId` to the supplier label
- [X] T005 [US1] Add the **Suppliers** (`suppliers`, `supplier`, `supplier-items`, `supplier-history`) and **Purchase orders** read functions (`purchase-orders`, `purchase-order`) to `frontend/src/services/manifests/procurement.ts` per contracts/procurement-manifest.md (resultRefs for `supplierId`)
- [X] T006 [US1] Verify US1: `cd frontend && npm test && npm run build` green

**Checkpoint**: MVP — browse suppliers + POs, open a PO and see its lines + status, all through the edge.

---

## Phase 4: User Story 2 - Restock and reorder suggestions (Priority: P2)

**Goal**: The restock/reorder suggestions — items at/below reorder point with suggested quantity + supplier.

**Independent Test**: Reorder suggestions lists items needing restock (suggested quantity + supplier), with a
clear empty state when none.

### Frontend (endpoint already exists — no backend change)

- [X] T007 [P] [US2] Extend `frontend/tests/ProcurementManifest.test.tsx` (red): `reorder-suggestions` renders the suggestions table (resolving `supplierId`) and a clear empty state on `[]`
- [X] T008 [US2] Add the **Reorder** `reorder-suggestions` function to `procurement.ts` (result table; resolve `supplierId`)
- [X] T009 [US2] Verify US2: `cd frontend && npm test && npm run build` green

**Checkpoint**: the purchasing decision input is viewable.

---

## Phase 5: User Story 3 - Create and progress a purchase order (Priority: P3)

**Goal**: The supplier + PO write/lifecycle actions and the suggestion actions — each a validated form.

**Independent Test**: Create a supplier → it appears in Suppliers; create a PO (with lines) → a draft PO with its
lines; approve → status transitions; generate/convert/dismiss suggestions act on them.

### Frontend (writes already exist — no backend change)

- [X] T010 [P] [US3] Extend `frontend/tests/ProcurementManifest.test.tsx` (red): `create-supplier` blocks on missing required inputs then POSTs; `create-po` submits `{poNo?, supplierId, lines:[{itemRef,qtyOrdered,agreedPrice}]}` (list input); `approve-po` runs (mock the edge)
- [X] T011 [US3] Add the **write** functions to `procurement.ts` — `create-supplier`, `update-supplier` (status select), `add-supplier-item`, `create-po` (supplier ref + lines list), `approve-po`, `send-po`, `cancel-po`, `close-po`, `generate-suggestions`, `convert-suggestion`, `dismiss-suggestion` — per contracts/procurement-manifest.md
- [X] T012 [US3] Verify US3: `cd frontend && npm test && npm run build` green; create→list round-trip (suppliers) works against the mocked edge

**Checkpoint**: the supplier + PO write surface + suggestion actions work and are verifiable via the reads.

---

## Phase 6: User Story 4 - Receive against a purchase order (Priority: P3)

**Goal**: Record receiving (full/partial) against an approved PO — the backend posts the goods receipt to
operations; the PO's received quantities + status update.

**Independent Test**: Receive an approved PO (some/all lines) → the goods receipt + updated `orderStatus` render;
PO receipts (by id) lists what was received; operations stock reflects it (observable in the Operations tab).

### Frontend (endpoint already exists — receiving's cross-service posting is backend-side)

- [X] T013 [P] [US4] Extend `frontend/tests/ProcurementManifest.test.tsx` (red): `receive-po` submits `{lines:[{itemRef,qtyReceived}]}` (list input) to `/api/procurement/purchase-orders/{id}/receipts` and renders the goods-receipt result; `po-receipts` (by id) renders the receipts read
- [X] T014 [US4] Add the **Receiving** functions (`receive-po` [id + lines list], `po-receipts` [id]) to `procurement.ts`
- [X] T015 [US4] Verify US4: `cd frontend && npm test && npm run build` green

**Checkpoint**: receiving works end-to-end; stock effects are observable in Operations (feature 012).

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T016 [P] Accessibility/responsive pass: the reused inputs stay keyboard-navigable with visible focus, and Procurement result shapes (PO detail lines sub-table + result tables) scroll (not the page) at the 011 768px floor across `frontend/src/`
- [ ] T017 [P] Docs: add a Procurement note to `frontend/README.md` (the CRM manifest is frontend-only; PO/receipt lines render via the 014 detail sub-table; receiving posts to operations backend-side)
- [ ] T018 [P] Confirm CI covers the frontend stream: the 011 `frontend` job runs the new `ProcurementManifest` suite; **no backend job change** (no backend code) — adjust `.github/workflows/ci.yml` only if a gap exists
- [ ] T019 Full verification: `cd frontend && npm test && npm run build` green; run `quickstart.md` end-to-end (browse → suggestions → create/approve PO → receive) with 0 real cloud

---

## Dependencies & Execution Order

- **Setup (T001–T002)** → **Foundational (T003)** → user stories. **T001 (sync main) is a hard prerequisite** —
  the manifest depends on the 012/013/014 shared framework (incl. the detail sub-table).
- **US1 (T004–T006)**: after Foundational; frontend-only. MVP.
- **US2 (T007–T009)**: after Foundational; frontend-only; independent of US1.
- **US3 (T010–T012)**: after US1 (verifies via the reads); frontend-only.
- **US4 (T013–T015)**: after US1 (a PO to receive against); frontend-only. Receiving's operations posting is
  backend-side; stock effects observable in the Operations tab.
- **Polish (T016–T019)**: after the desired stories.

### Within each story
- The frontend manifest test ([P]) is written first (red), then the functions, then verify.

### Parallel Opportunities
- Setup: T002 after T001.
- Each story's test task ([P]) precedes its function task.
- Polish: T016 ∥ T017 ∥ T018.

---

## Implementation Strategy

### MVP First (US1)
Sync main → Setup → Foundational → US1 → **STOP & VALIDATE**: browse suppliers + POs, open a PO with its lines +
status — the fourth per-service UI, entirely frontend, reusing the full 011–014 framework.

### Incremental Delivery
US1 (suppliers + POs) → US2 (suggestions) → US3 (writes + lifecycle) → US4 (receiving). **No backend code and no
new framework in any story.**

---

## Notes
- **0 backend changes and 0 new framework** — every procurement read/write exists; PO/receipt `lines[]` render via
  the 014 detail sub-table.
- **No new input types** — reuses the 012/013 `reference`/`list` inputs; PO/receipt bodies are plain objects with a
  `lines` array (standard object-body builder).
- Receiving's goods-receipt posting to operations is done by the **backend** (FR-012); the UI triggers + displays.
- procurement-service is role-gated; in stub mode the demo session has all roles (else a clear 403).
- Commit after each story (or logical group); simple messages, no AI attribution.
