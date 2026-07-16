---
description: "Task list for Supplier Records & Purchasing Service (procurement-service)"
---

# Tasks: Supplier Records & Purchasing Service

**Input**: Design documents from `/specs/006-supplier-purchasing/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: INCLUDED (TDD per Constitution II). Contract tests (OpenAPI), Testcontainers integration +
migration tests, PO state-machine unit tests (legal/illegal transitions), receiving/over-receipt tests,
a concurrency test (no double-approve/double-receive), and restock tests are written before the code.

**Module**: new Gradle module `backend/procurement-service`, package root `com.kita.procurement`,
schema `procurement`. Integrates with `operations-service` via an `OperationsPort` (fake in tests).

## Format: `[ID] [P?] [Story] Description`
- **[P]**: parallelizable (different files, no dependency)

---

## Phase 1: Setup

- [X] T001 Add `:procurement-service` to `backend/settings.gradle.kts` and create `backend/procurement-service/build.gradle.kts` mirroring `operations-service` (Spring Web, Data JPA, Validation, Actuator, Flyway, PostgreSQL, Testcontainers; Spotless/Checkstyle; Windows Testcontainers workaround)
- [X] T002 Create package skeleton under `backend/procurement-service/src/main/java/com/kita/procurement/{supplier,purchaseorder,receiving,restock,operations,common,api}` and `ProcurementServiceApplication`
- [X] T003 [P] Add `.../resources/application.yml` (datasource via env, JPA, Flyway, Actuator, JSON logging; operations-service base URL via env) — no secrets
- [X] T004 [P] Flyway baseline `.../db/migration/V1__init_procurement_schema.sql` (schema `procurement`, extensions, common columns)
- [X] T005 [P] Add the `procurement-service` build to CI `.github/workflows/ci.yml`

---

## Phase 2: Foundational (Blocking)

- [X] T006 [P] `common/Money.java` + rounding policy (half-up to minor unit) + `BigDecimal` helpers, with unit tests
- [X] T007 [P] `common/AuditEvent` entity + repository + append-only writer
- [X] T008 [P] `common/` global exception handler + validation error DTO for the API boundary
- [X] T009 `operations/OperationsPort` interface (getReorderSignals, postGoodsReceipt) + a `FakeOperationsAdapter` for tests (idempotent post)
- [X] T010 Role guards + API security scaffolding; Testcontainers singleton base test (`AbstractProcurementIT`) with per-test TRUNCATE

**Checkpoint**: build compiles; migrations apply; port + fake adapter + base test harness ready.

---

## Phase 3: User Story 1 - Maintain supplier records (Priority: P1)

**Goal**: create/update/retrieve suppliers + supplied items with history; serve as supplier Party master.
**Independent Test**: create a supplier with two supplied items, edit a price, deactivate, and read back
the record + history; fetch by id (operations-service party validation path).

### Tests (write first, must FAIL) ⚠️
- [X] T011 [P] [US1] Contract test for `/suppliers`, `/suppliers/{id}`, `/suppliers/{id}/items`
- [X] T012 [P] [US1] Integration test: supplier + supplied-item persistence, price history retention

### Implementation
- [X] T013 [P] [US1] Flyway `V2__supplier.sql` (supplier, supplier_item, supplier_change_history)
- [X] T014 [P] [US1] `supplier/Supplier` + `supplier/SupplierItem` entities + repositories
- [X] T015 [US1] `supplier/SupplierService` (create/update, status, supplied items, history)
- [X] T016 [US1] `api/SupplierController` + DTOs; validation (unique supplier_code); party lookup endpoint

**Checkpoint**: supplier directory + party lookup works and is independently testable.

---

## Phase 4: User Story 2 - Create & manage purchase orders (Priority: P1) 🎯 MVP

**Goal**: create → approve (threshold-gated) → send; enforced state machine; totals reconcile; lines
lock on send; cancel has no inventory effect.
**Independent Test**: create a 2-line PO, verify totals, approve under/over threshold, send (lines lock),
attempt to receive a draft (rejected), cancel before receipt.

### Tests (write first, must FAIL) ⚠️
- [X] T017 [P] [US2] Contract test for `/purchase-orders`, `/approve`, `/send`, `/cancel`
- [X] T018 [P] [US2] Unit tests for the PO state machine per `contracts/po-lifecycle.md` (legal + illegal transitions; sent-line lock; totals rounding SC-002)
- [X] T019 [P] [US2] Concurrency integration test: no double-approve; threshold gating (SC-002)

### Implementation
- [X] T020 [P] [US2] Flyway `V3__purchase_order.sql` (purchase_order, purchase_order_line)
- [X] T021 [P] [US2] `purchaseorder/PurchaseOrder` + `PurchaseOrderLine` entities + repositories
- [X] T022 [US2] `purchaseorder/PurchaseOrderStateMachine` (guarded transitions, sent-line lock, cancel)
- [X] T023 [US2] `purchaseorder/PurchaseOrderService` (create with computed totals, approve with threshold, send, cancel) transactional + locking
- [X] T024 [US2] `api/PurchaseOrderController` + DTOs (create/get/approve/send/cancel)

**Checkpoint**: PO lifecycle to "sent" works and is enforced. **(MVP with US1)**

---

## Phase 5: User Story 3 - Receive against a PO (Priority: P2)

**Goal**: record partial/full receipts; reconcile; post goods-receipt to operations-service exactly once.
**Independent Test**: partial then full receipt moves PARTIALLY→FULLY_RECEIVED→CLOSED; over-receipt
prevented/flagged; exactly one goods-receipt event per receipt (via fake adapter).

### Tests (write first, must FAIL) ⚠️
- [X] T025 [P] [US3] Contract test for `/purchase-orders/{id}/receipts`
- [X] T026 [P] [US3] Integration tests: partial→full→closed; over-receipt guard (FR-010); exactly-once idempotent post to OperationsPort (SC-003/004)
- [X] T027 [P] [US3] Concurrency test: no double-receive

### Implementation
- [X] T028 [P] [US3] Flyway `V4__goods_receipt.sql` (goods_receipt + lines, idempotency key)
- [X] T029 [P] [US3] `receiving/GoodsReceipt` entity + repository
- [X] T030 [US3] `receiving/ReceivingService` — record receipt, reconcile outstanding, advance PO state, prevent over-receipt
- [X] T031 [US3] Emit goods-receipt via `OperationsPort` (idempotent on receipt id); `api` receipts endpoint

**Checkpoint**: receiving closes POs and posts stock/cost updates exactly once.

---

## Phase 6: User Story 4 - Restock / reorder suggestions (Priority: P2)

**Goal**: turn low-stock signals into supplier-grouped suggestions; convert to POs; auto-submit off by default.
**Independent Test**: items below reorder point → suggestions sized to target (min-order respected),
consolidated per supplier; convert one to a draft PO; auto-submit disabled by default.

### Tests (write first, must FAIL) ⚠️
- [X] T032 [P] [US4] Contract test for `/restock/suggestions` (+ generate/convert)
- [X] T033 [P] [US4] Unit/integration tests: suggestion sizing (target + min order), per-supplier consolidation, auto-submit gated off (FR-012/013/014, SC-005)

### Implementation
- [X] T034 [P] [US4] Flyway `V5__restock.sql` (restock_suggestion + lines)
- [X] T035 [P] [US4] `restock/RestockSuggestion` entity + repository
- [X] T036 [US4] `restock/RestockService` — read reorder signals via `OperationsPort`, size + consolidate suggestions, convert to draft PO; opt-in per-item auto-submit (default off)
- [X] T037 [US4] `api/RestockController` (generate/list/convert)

**Checkpoint**: replenishment suggestions generated and convertible to POs.

---

## Phase 7: Polish & Cross-Cutting

- [X] T038 [P] Structured JSON logging (no secrets) across the service
- [X] T039 [P] OpenAPI contract wired as source of truth; contract tests green against `contracts/procurement-openapi.yaml`
- [X] T040 [P] `backend/procurement-service/README.md` (module purpose, run/test, OperationsPort, endpoints)
- [X] T041 Real HTTP `OperationsAdapter` to operations-service (behind the port; fake used in tests) with retry-safe idempotent posting
- [X] T042 Full `:procurement-service:build` green (Spotless/Checkstyle/tests) in CI; fail-fast gate

---

## Dependencies & Execution Order
- Setup (P1) → Foundational (P2, incl. OperationsPort + fake) → US1 → US2 → US3 → US4 → Polish.
- US2 depends on US1 (supplier + items); US3 depends on US2 (a sent PO); US4 depends on US2 (creates POs)
  and the OperationsPort (signals).
- Within a phase, `[P]` tasks (distinct files) run in parallel; tests precede implementation (TDD).

## Implementation Strategy
MVP = Setup + Foundational + US1 + US2 (supplier master + PO lifecycle to "sent"). Then US3 (receiving +
exactly-once goods-receipt posting) and US4 (restock) complete purchasing.

## Notes
- Highest-risk: state-machine integrity + sent-line lock (T022/T018), exactly-once idempotent goods
  receipt (T031/T026), over-receipt guard (T030), restock sizing (T036) — keep those tests rigorous.
- Money exact decimal; totals reconcile to the cent. This service never mutates inventory directly —
  it posts via `OperationsPort`.
- Commit after each task/group and push per project workflow.

---

## Phase 8: Coverage Gaps (added 2026-07-15 after `/speckit-analyze`)

FR-016's audit trail was written but never verified — 005 had an equivalent task (T039), 006 did not.

- [X] T043 Expose `AuditEvent.getActor()/getAt()/getDetail()` so attribution is assertable (SC-006)
- [X] T044 `common/AuditTrailIT` — PO approve/send/receipt/supplier changes attributable to user and timestamp (FR-016/SC-006)
- [X] T045 Assert a cancelled PO posts nothing to operations-service (FR-008)
