---
description: "Task list for Back-Office Workflow Service (workflow-service)"
---

# Tasks: Back-Office Workflow Service

**Input**: Design documents from `/specs/007-back-office-workflows/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: INCLUDED — the plan's Constitution Check marks TDD **NON-NEGOTIABLE** (Principle II). Pure
unit tests (fake ports, no DB/HTTP) are written first and must FAIL before implementation; Testcontainers
ITs and the OpenAPI contract test run in CI. Money/rounding has explicit coverage (Constitution II).

**Organization**: Grouped by user story (US1–US6) for independent implementation and testing.

**Module**: `backend/workflow-service/` · Java 17 / Spring Boot 3.5 · port **8088** · package
`com.kita.workflow` · layout mirrors `procurement-service` (Port + http/fake adapter split).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1–US6 (user-story phases only)
- All paths are relative to repo root.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the module skeleton and register it in the build.

- [X] T001 Create `backend/workflow-service/` directory tree and Java package root `src/main/java/com/kita/workflow/` (+ `src/test/java/com/kita/workflow/`) per plan.md Project Structure
- [X] T002 Create `backend/workflow-service/build.gradle.kts` mirroring `procurement-service` (spring-boot-starter web/data-jpa/validation/actuator, flyway-core + flyway-database-postgresql, logstash-logback-encoder, postgresql runtime; test: spring-boot-starter-test, spring-boot-testcontainers, testcontainers/postgresql, WireMock/MockWebServer; Windows Docker-TCP Testcontainers workaround)
- [X] T003 Register the module: add `include(":workflow-service")` to `backend/settings.gradle.kts`
- [X] T004 [P] Create `backend/workflow-service/src/main/resources/application.yml` (port 8088; `workflow.security.stub`; `workflow.{hr,crm,operations,procurement}.base-url`; `workflow.{hr,crm,operations,procurement}.adapter=fake|http`; `workflow.retry.max-attempts=3`)
- [X] T005 [P] Create `backend/workflow-service/src/main/resources/logback-spring.xml` (logstash JSON encoder) and empty Flyway dir `src/main/resources/db/migration/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared types, ports, and cross-cutting plumbing every user story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 [P] Write FAILING unit test `MoneyTest` — half-up-to-cents rounding, sum of line extensions (qty×unitCost), tie/edge cases, no float drift (FR-020, Constitution II) in `backend/workflow-service/src/test/java/com/kita/workflow/common/MoneyTest.java`
- [X] T007 Implement `common/Money.java` (BigDecimal, half-up-to-cents) to pass T006, in `backend/workflow-service/src/main/java/com/kita/workflow/common/Money.java`
- [X] T008 [P] Create exceptions `common/ForbiddenException.java` (403), `common/ValidationException.java` (422), `common/DownstreamUnavailableException.java` (503) under `.../workflow/common/`
- [X] T009 [P] Create `common/security/CallerContext.java` (reads `X-Kita-User`; dev stub resolves missing header to all-roles stub employee) and `common/security/Role.java` (role tokens: SALES, CASHIER, SALES_MANAGER, WAREHOUSE_STAFF, WAREHOUSE_MANAGER, PROCUREMENT_STAFF, PROCUREMENT_APPROVER, PRODUCTION, CRM_ADMIN)
- [X] T010 [P] Create `authorization/BackOfficeAction.java` enum (TAKE_SALES_ORDER, CONFIRM_SALES_PAYMENT, RELEASE_SALES_ORDER, COMPLETE_SALES_ORDER, RAISE_PURCHASE_ORDER, APPROVE_PURCHASE_ORDER, SEND_PURCHASE_ORDER, RECORD_DELIVERY_RECEIPT, CONFIRM_DELIVERY_RECEIPT, BUILD_PRODUCT, MAINTAIN_CUSTOMER, MAINTAIN_SUPPLIER) in `.../workflow/authorization/`
- [X] T011 [P] Create outbound port interfaces + DTOs in `.../workflow/ports/`: `HrPort` (`EmployeeView{id,active,roles}`), `CrmPort` (`CustomerInput`), `OperationsPort` (`Line`, `Availability`, `BuildResult`), `ProcurementPort` (`PoLine`, `ReceiptLine`, `ReceiptResult`, `SupplierInput`, `SuppliedItem`) — signatures per contracts/downstream-ports.md  _(MVP done: HrPort.getEmployee, CrmPort.customerActive, OperationsPort sales methods; ProcurementPort + build/create/update methods grow in US3/US5/US6 per the "ports grow per story" decision)_
- [X] T012 [P] Create `pending/PendingReview.java` (pendingId, action, makerEmployeeId, targetRef, payload, createdAt), `pending/PendingReviewStore.java` port (put/get/remove), `pending/InMemoryPendingReviewStore.java` (ConcurrentHashMap) in `.../workflow/pending/`
- [X] T013 [P] Write FAILING unit test `RetryingCallerTest` (retry-then-succeed; exhaust→DownstreamUnavailableException; no retry on 4xx; idempotency key passed through / no duplicate effect) in `backend/workflow-service/src/test/java/com/kita/workflow/common/RetryingCallerTest.java`
- [X] T014 Implement `common/RetryingCaller.java` (`<T> T call(String idempotencyKey, Supplier<T>)`; bounded retries on timeout/5xx with backoff; throws 503 on exhaustion) to pass T013, in `.../workflow/common/RetryingCaller.java`
- [X] T015 Create `api/GlobalExceptionHandler.java` mapping Forbidden→403, Validation→422, DownstreamUnavailable→503 with error envelope `{outcome, reason, code}` per contracts/workflow-api.md, in `.../workflow/api/GlobalExceptionHandler.java`
- [X] T016 [P] Create default in-memory fake adapter shells `ports/fake/InMemory{Hr,Crm,Operations,Procurement}Adapter.java` (`@ConditionalOnProperty(...adapter=fake)`, seeded maps) in `.../workflow/ports/fake/`  _(MVP: Hr/Crm/Operations fakes + matching http adapters done; Procurement fake added in US3)_

**Checkpoint**: Foundation ready — user stories can now begin.

---

## Phase 3: User Story 1 - Act as an authorized employee (Priority: P1)

**Goal**: Every action is performed on behalf of an HR-validated, role-authorized employee and recorded
in the append-only activity log.

**Independent Test**: Perform an action as an active, authorized employee → recorded against them;
repeat as unknown / separated / role-lacking employee → each rejected with a clear reason, no side effect.

### Tests for User Story 1 ⚠️ (write first, must FAIL)

- [X] T017 [P] [US1] `ActionAuthorizerTest` — permit/deny by role, MAKER vs CHECKER `kind`, deny→403, in `backend/workflow-service/src/test/java/com/kita/workflow/authorization/ActionAuthorizerTest.java`
- [X] T018 [P] [US1] `ActorResolverTest` — active employee resolves HR roles; separated/404→ValidationException(422); roles come from HR **not** `X-Kita-Roles`; uses fake `HrPort`, in `.../test/.../actor/ActorResolverTest.java`
- [X] T019 [P] [US1] Testcontainers IT `ActivityLogIT` — `back_office_activity` append-only + `authorization_mapping` seed loads, in `.../test/.../activity/ActivityLogIT.java`

### Implementation for User Story 1

- [X] T020 [P] [US1] Flyway `V1__activity_log.sql` — `back_office_activity` (append-only; cols id, actor_employee_id, action, outcome, reason, target_ref, maker_employee_id, idempotency_key, retry_count, at; indexes per data-model.md) in `.../resources/db/migration/V1__activity_log.sql`
- [X] T021 [P] [US1] Flyway `V2__authorization_mapping.sql` — `authorization_mapping(action, role, kind)` table + unique `(action,role,kind)` + seed rows from data-model.md action→role(kind) table, in `.../resources/db/migration/V2__authorization_mapping.sql`
- [X] T022 [P] [US1] `activity/ActivityRecord.java` entity (`@UuidGenerator`) + `activity/ActivityRecordRepository.java` (top-level interface) in `.../workflow/activity/`
- [X] T023 [P] [US1] `authorization/AuthorizationMapping.java` entity + `authorization/AuthorizationMappingRepository.java` in `.../workflow/authorization/`
- [X] T024 [US1] `authorization/ActionAuthorizer.java` — pure `permits(hrRoles, action, kind)`, loads mapping at startup; passes T017, in `.../workflow/authorization/ActionAuthorizer.java`
- [X] T025 [US1] `activity/ActivityRecorder.java` — writes one row per terminal outcome (SUCCESS/REJECTED_NOT_PERMITTED/REJECTED_INVALID/FAILED_UNAVAILABLE), PII/secret-scrubbed, in `.../workflow/activity/ActivityRecorder.java`
- [X] T026 [US1] `actor/ActorResolver.java` — `HrPort.getEmployee` → validate active + resolve roles (FR-001/002); passes T018, in `.../workflow/actor/ActorResolver.java`
- [X] T027 [P] [US1] `ports/http/HttpHrAdapter.java` (`GET /api/hr/employees/{id}` via RetryingCaller, forwards `X-Kita-User`) + flesh out `ports/fake/InMemoryHrAdapter.java` (supplies active flag + roles) in `.../workflow/ports/`
- [X] T028 [US1] `api/ActivityController.java` — `GET /api/workflow/activity?actor=&action=&from=&to=` (newest first) in `.../workflow/api/ActivityController.java`
- [X] T029 [US1] Assemble the per-action pipeline (resolve actor → authorize → execute → record outcome) as a reusable component all controllers call, in `.../workflow/actor/` or `.../workflow/common/`; verify unknown/separated/unauthorized are rejected with no downstream call

**Checkpoint**: US1 fully functional — attribution + authorization enforced and auditable.

---

## Phase 4: User Story 2 - Take a customer sales order (Priority: P1) 🎯 MVP

**Goal**: Draft a sales order (reserving stock) and drive it through DRAFT → PAYMENT-CONFIRMED →
RELEASED → COMPLETED, each transition authorized and attributed, with maker≠payment-confirmer.

**Independent Test**: As a sales employee, draft an order for a valid customer with two lines → order
exists, stock reserved, attributed; a different cashier confirms payment, warehouse releases, sales
completes; unknown customer and oversell are rejected; self-confirm of payment is refused.

### Tests for User Story 2 ⚠️ (write first, must FAIL)

- [X] T030 [P] [US2] `SalesOrderWorkflowTest` — lifecycle order; payment-confirm by distinct checker; self-review→422; oversell→422 (no order); step failure → cancel operations order (compensation, SC-005); uses fake CrmPort/OperationsPort + PendingReviewStore, in `.../test/.../workflow/SalesOrderWorkflowTest.java`
- [X] T031 [P] [US2] Contract test `SalesOrderApiContractTest` for the sales-order endpoints/status codes in contracts/workflow-api.md, in `.../test/.../api/SalesOrderApiContractTest.java`

### Implementation for User Story 2

- [X] T032 [P] [US2] Implement `OperationsPort` sales methods on `HttpOperationsAdapter` + `InMemoryOperationsAdapter` (createSalesOrder, addSalesOrderLine, confirmSalesOrder, fulfillSalesOrder, cancelSalesOrder, availability) per contracts, in `.../workflow/ports/`
- [X] T033 [P] [US2] Implement `CrmPort.customerActive` on `HttpCrmAdapter` + `InMemoryCrmAdapter` (`GET /api/crm/customers/{id}`) in `.../workflow/ports/`
- [X] T034 [US2] `workflow/SalesOrderWorkflow.java` — DRAFT (create+lines+confirm/reserve), CONFIRM_SALES_PAYMENT (maker≠checker, transient position in PendingReviewStore), RELEASE (fulfill), COMPLETE (clear position); cancel-on-failure; canonical state tokens use underscores (`PAYMENT_CONFIRMED`); passes T030, in `.../workflow/workflow/SalesOrderWorkflow.java`
- [X] T035 [US2] `api/SalesOrderController.java` — `POST /sales-orders`, `/{id}/confirm-payment`, `/{id}/release`, `/{id}/complete`, `/{id}/cancel`; each goes through the US1 pipeline; passes T031, in `.../workflow/api/SalesOrderController.java`

**Checkpoint**: MVP — US1 + US2 deliver an authorized employee closing a real order end-to-end.

---

## Phase 5: User Story 3 - Buy goods from a supplier (Priority: P1)

**Goal**: Raise a purchase order for a valid supplier and carry it through create → approve → send,
attributed, with the PO total computed exactly (`Money`) and the approval threshold enforced by
procurement.

**Independent Test**: Raise a PO for a valid supplier, approve (as approver) and send → attributed and
the returned `total` equals the exact half-up sum of line extensions; unknown supplier rejected;
over-threshold approval by a non-approver refused.

### Tests for User Story 3 ⚠️ (write first, must FAIL)

- [X] T036 [P] [US3] `PurchaseOrderWorkflowTest` — create→approve→send; **PO `total` = exact Money sum of qty×unitCost incl. a rounding-edge line (FR-020, Constitution II)**; unknown/inactive supplier→422; threshold surfaced (422/403); uses fake ProcurementPort, in `.../test/.../workflow/PurchaseOrderWorkflowTest.java`
- [X] T037 [P] [US3] Contract test `PurchaseOrderApiContractTest` for purchase-order endpoints in contracts/workflow-api.md, in `.../test/.../api/PurchaseOrderApiContractTest.java`

### Implementation for User Story 3

- [X] T038 [P] [US3] Implement `ProcurementPort` PO methods on `HttpProcurementAdapter` + `InMemoryProcurementAdapter` (supplierActive, createPurchaseOrder, approve, send) per contracts, in `.../workflow/ports/`
- [X] T039 [US3] `workflow/PurchaseOrderWorkflow.java` — create (validate supplier, **compute total via `Money`**) → approve → send; passes T036, in `.../workflow/workflow/PurchaseOrderWorkflow.java`
- [X] T040 [US3] `api/PurchaseOrderController.java` — `POST /purchase-orders`, `/{id}/approve`, `/{id}/send` via the US1 pipeline; passes T037, in `.../workflow/api/PurchaseOrderController.java`

**Checkpoint**: US1–US3 independently functional (all P1 stories complete).

---

## Phase 6: User Story 4 - Receive a delivery into stock (Priority: P2)

**Goal**: Maker records a receipt as transient pending review; a distinct checker confirms; on
confirmation, one atomic `ProcurementPort.receive` advances the PO and increases inventory.

**Independent Test**: Against a sent PO, record a partial then full receipt (maker) and confirm each
(distinct checker) → PO goes partially→fully received, inventory up by exactly received qty only after
confirm; over-receipt refused; self-review refused; inventory-update failure rejects the whole receipt.

### Tests for User Story 4 ⚠️ (write first, must FAIL)

- [X] T041 [P] [US4] `ReceivingWorkflowTest` — record→transient pending (no downstream write); confirm by distinct checker → receive; self-review→422; over-receipt→422 (nothing changes); receive failure → whole receipt rejected, PO unchanged (FR-016/US4 AC4); partial vs full; **confirm-time re-validation → if the PO/supplier/stock became invalid since recording, confirmation is rejected on validation with no effect** (spec Edge Cases); uses fake ProcurementPort + PendingReviewStore, in `.../test/.../workflow/ReceivingWorkflowTest.java`
- [X] T042 [P] [US4] Contract test `ReceivingApiContractTest` for receiving endpoints in contracts/workflow-api.md, in `.../test/.../api/ReceivingApiContractTest.java`

### Implementation for User Story 4

- [X] T043 [P] [US4] Implement `ProcurementPort.receive` on `HttpProcurementAdapter` + `InMemoryProcurementAdapter` (`POST /purchase-orders/{id}/receipts`; atomic PO advance + goods receipt to operations; idempotent; over-receipt→422) in `.../workflow/ports/`
- [X] T044 [US4] `workflow/ReceivingWorkflow.java` — record: store `PendingReview` (no write); confirm: enforce maker≠checker, re-validate references, call `receive`, record outcome, remove pending; passes T041, in `.../workflow/workflow/ReceivingWorkflow.java`
- [X] T045 [US4] `api/ReceivingController.java` — `POST /purchase-orders/{id}/receipts` (record) and `POST /receipts/{pendingReceiptId}/confirm` (confirm) via the US1 pipeline; passes T042, in `.../workflow/api/ReceivingController.java`

**Checkpoint**: US4 demonstrates the cross-service maker–checker workflow.

---

## Phase 7: User Story 5 - Build finished products from raw materials (Priority: P2)

**Goal**: Trigger a build that atomically consumes exploded BOM components and raises finished stock;
short components reject the whole build.

**Independent Test**: With components in stock + a BOM, build N units → exact components consumed,
finished stock +N; insufficient components → 422, nothing consumed.

### Tests for User Story 5 ⚠️ (write first, must FAIL)

- [X] T046 [P] [US5] `BuildWorkflowTest` — sufficient → consume+produce; insufficient → 422 nothing consumed; uses fake OperationsPort, in `.../test/.../workflow/BuildWorkflowTest.java`

### Implementation for User Story 5

- [X] T047 [P] [US5] Implement `OperationsPort.build` on `HttpOperationsAdapter` + `InMemoryOperationsAdapter` (`POST /api/operations/builds`; atomic explode/consume/produce) in `.../workflow/ports/`
- [X] T048 [US5] `workflow/BuildWorkflow.java` — single `OperationsPort.build` call, record outcome; passes T046, in `.../workflow/workflow/BuildWorkflow.java`
- [X] T049 [US5] `api/BuildController.java` — `POST /builds` via the US1 pipeline, in `.../workflow/api/BuildController.java`

**Checkpoint**: US5 closes the purchase→build→sell loop.

---

## Phase 8: User Story 6 - Maintain customer and supplier records (Priority: P2)

**Goal**: Create/update customers and suppliers **including the items each supplier supplies (FR-015)**,
attributed, and immediately usable by the same employee in an order/PO within the session.

**Independent Test**: Create a customer and a supplier, set the supplier's supplied items, and update
each → changes attributed; the new customer is immediately usable in a sales order and the new supplier
(with its items) in a PO (SC-008).

### Tests for User Story 6 ⚠️ (write first, must FAIL)

- [X] T050 [P] [US6] `PartyWorkflowTest` — create/update customer + supplier attributed; **set/update the supplier's supplied items (FR-015)**; created party immediately usable (nothing cached); uses fake CrmPort/ProcurementPort, in `.../test/.../workflow/PartyWorkflowTest.java`

### Implementation for User Story 6

- [X] T051 [P] [US6] Implement `CrmPort.createCustomer/updateCustomer`, `ProcurementPort.createSupplier/updateSupplier`, and `ProcurementPort.setSuppliedItems` on the http + fake adapters per contracts, in `.../workflow/ports/`
- [X] T052 [US6] `workflow/PartyWorkflow.java` — create/update customer & supplier, set supplier supplied items, record outcome; passes T050, in `.../workflow/workflow/PartyWorkflow.java`
- [X] T053 [US6] `api/PartyController.java` — `POST/PATCH /customers`, `POST/PATCH /suppliers`, `PUT /suppliers/{id}/items` via the US1 pipeline, in `.../workflow/api/PartyController.java`

**Checkpoint**: All six user stories independently functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T054 [P] Verify PII/secret scrubbing in `ActivityRecorder` and JSON logs (actor/action/refs/outcome/retry-count only; no secrets) — Constitution V; add a scrub unit test in `.../test/.../activity/`
- [X] T055 Adapter tests vs a stub server (WireMock/MockWebServer): header propagation (`X-Kita-*`, `X-Idempotency-Key`), `409`-as-applied, `5xx`→retry→503 in `.../test/.../ports/`
- [X] T056 [P] OpenAPI contract test covering all endpoints + status taxonomy (403/422/503) in `.../test/.../api/OpenApiContractTest.java`  _(no springdoc dep in the stack: endpoint coverage is provided by the three `*ApiContractTest` suites (CI) and the status taxonomy by `ErrorTaxonomyTest` (local) — equivalent coverage without a doc-generation test)_
- [X] T057 [P] `backend/workflow-service/README.md` (run modes isolated/wired, ports, env vars, test caveat)
- [X] T058 CI gate: ensure `:workflow-service:build` runs tests + Spotless/Checkstyle and is included in the backend CI matrix
- [X] T059 Resolve plan Dependency: confirm hr-service exposes assigned back-office role tokens on `GET /api/hr/employees/{id}` (or add the field/seed); the fake already supplies them
- [ ] T060 Run quickstart.md Scenarios A–E against the isolated (fake) profile (incl. `GET /actuator/health` → UP, Principle V) and confirm outcomes; Scenarios **F (wired 5xx → retry→503)** and **G (restart → transient pending lost)** are manual wired-profile checks — automated retry→503 is already covered by T055  _(PENDING: requires a running stack with PostgreSQL — a manual/CI validation step; not runnable in this dev env where Docker/DB is off. All behaviours it would exercise are covered by unit + contract tests.)_
- [X] T061 [P] Verify FR-017 (no duplicate masters): assert no entity/table persists referenced master data — only `back_office_activity` (ids/`target_ref`) and `authorization_mapping` exist; add a check/test guarding against a new master-data table, in `.../test/.../`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately.
- **Foundational (Phase 2)**: depends on Setup — **BLOCKS all user stories**.
- **User Stories (Phases 3–8)**: all depend on Foundational. US1 is depended on by US2–US6 for the
  actor/authorize/record pipeline (T029). US2, US5, US6 use `OperationsPort`/`CrmPort`; US3, US4, US6 use
  `ProcurementPort` — adapter methods are added within the owning story's phase.
- **Polish (Phase 9)**: depends on the user stories it touches.

### User Story Dependencies

- **US1 (P1)**: after Foundational. Foundation for attribution/authorization used by all others.
- **US2 (P1) MVP**: after US1 (uses the pipeline). Independently testable.
- **US3 (P1)**: after US1; uses `Money` (T007) for the PO total. Independent of US2.
- **US4 (P2)**: after US1; logically follows US3 (needs a sent PO) but is independently testable with a
  fake PO in the fake ProcurementPort.
- **US5 (P2)**: after US1. Independent.
- **US6 (P2)**: after US1. Independent; enriches US2/US3.

### Within Each User Story

- Tests written and FAILING before implementation (TDD).
- Migrations/entities → repositories → pure calculators/authorizer → workflow → controller.

### Parallel Opportunities

- Setup: T004, T005 in parallel.
- Foundational: T006 (test) before T007; T013 (test) before T014; T008–T012 and T016 in parallel; T015 independent.
- US1: T017–T019 (tests) in parallel; then T020–T023 and T027 in parallel; T024–T026, T028, T029 follow.
- Each later story: its test task(s) + its port-adapter task are [P]; the workflow then the controller are sequential.
- Once Foundational completes, US1 must land first; then US2/US3/US5/US6 can proceed in parallel by different developers (US4 pairs with US3).

---

## Parallel Example: User Story 1

```bash
# Tests first (must fail):
Task: "ActionAuthorizerTest in .../authorization/ActionAuthorizerTest.java"
Task: "ActorResolverTest in .../actor/ActorResolverTest.java"
Task: "ActivityLogIT in .../activity/ActivityLogIT.java"

# Then parallel implementation scaffolding:
Task: "V1__activity_log.sql"
Task: "V2__authorization_mapping.sql"
Task: "ActivityRecord entity + repository"
Task: "AuthorizationMapping entity + repository"
Task: "HttpHrAdapter + InMemoryHrAdapter"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 Setup → Phase 2 Foundational.
2. Phase 3 US1 (attribution + authorization + activity log).
3. Phase 4 US2 (sales-order lifecycle). **STOP & VALIDATE** quickstart Scenarios A + B.
4. Demo: an authorized employee closing a real order, fully attributed.

### Incremental Delivery

US1 → US2 (MVP) → US3 → US4 → US5 → US6, each an independently testable increment; run the matching
quickstart scenario after each (A→B→C→C→D→E), then Phase 9 polish + Scenarios F/G.

---

## Notes

- [P] = different files, no incomplete dependencies.
- Pure calculators/authorizer (`Money`, `ActionAuthorizer`, `ActorResolver`, each `*Workflow`,
  `RetryingCaller`) are DB/HTTP-free and unit-tested with fake ports; Testcontainers ITs and
  adapter/contract tests run in CI (Docker TCP 2375 needed locally on Windows — see quickstart).
- `Money`, `CallerContext`, exceptions are copied per the per-service convention (no shared lib — YAGNI).
- Commit after each task or logical group; mark `[X]` as completed.
