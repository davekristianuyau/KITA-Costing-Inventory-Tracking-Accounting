---
description: "Task list for 018 — Correct & Secure Service-to-Service Integration"
---

# Tasks: Correct & Secure Service-to-Service Integration

**Input**: Design documents from `/specs/018-secure-service-contracts/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/orchestrated-calls.md, contracts/transport-security.md

**Tests**: Included — Constitution II (TDD) is non-negotiable and US2 *is* the verification story. Per-call
contract tests are written **red first**, then the US1 adapter fix makes them green.

**Organization**: By user story. US1 (P1, correct calls) → US2 (P1, verify) → US3 (P2, mTLS+datastore TLS+
persisted refusals — broadest) → US4 (P3, rotation). MVP = US1 (+US2 guard). Grounded file paths from Phase 0.

## Format: `[ID] [P?] [Story] Description`
- **[P]**: parallelizable (different files, no incomplete-task dependency)
- Backend monorepo root: `backend/`; caller = `backend/workflow-service/`; receivers = `backend/{operations,hr,crm,procurement}-service/`.

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Add `testImplementation` project deps in `backend/workflow-service/build.gradle.kts` on the receiver DTO modules (`:operations-service`, `:procurement-service`, `:crm-service`, `:hr-service`) so contract tests can bind against the real request/response records.
- [X] T002 [P] [→US3] **Not needed after all** — the filter uses `OncePerRequestFilter` (spring-web) and reads the X.509 cert straight off the servlet request, so no `spring-boot-starter-security` dependency (and no auto-config lockdown risk) at all. Original text: Add `spring-boot-starter-security` to each receiving service's `build.gradle.kts` (`operations`, `hr`, `crm`, `procurement`, `workflow`) for the X.509 `ServiceIdentityFilter`. **Deferred to US3**: the dep must land with its `permitAll`/filter config (T031–T037) or Spring Security's default auto-config locks down every endpoint (incl. health) and breaks the running services.
- [X] T003 [P] Create the dev-CA cert-bootstrap script `docker/certs/bootstrap-certs.sh` (generates CA + per-service PKCS12 bundles with SAN=service name + Postgres/Redis server certs into a shared volume) and add `docker/certs/*.p12`, `*.pem`, `*.key` to `.gitignore` (FR-011).
- [X] T004 [P] Create the contract-test source package `backend/workflow-service/src/test/java/com/kita/workflow/contract/` with a `ContractSupport` helper (serialises an adapter body via the app `ObjectMapper`, binds it to a receiver DTO record, runs a Jakarta `Validator`).

**Checkpoint**: build tooling + cert scaffolding + contract-test harness skeleton ready.

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: shared plumbing every story leans on. No user-story work starts until these are done.

- [X] T005 Rewrite the error path in `backend/workflow-service/src/main/java/com/kita/workflow/ports/http/RemoteCall.java` to **parse and surface the receiver's real error reason** (its `ProblemDetail`/message body) on a business 4xx, keep 5xx/TLS/timeout → `TransientDownstreamException` (503), and map a receiver 403 → not-permitted (`ForbiddenException`) — three distinct outcomes (FR-003). Used by every corrected call (US1) and the TLS taxonomy (US3).
- [X] T006 [P] Add a unit test `backend/workflow-service/src/test/java/com/kita/workflow/ports/http/RemoteCallTest.java` proving the taxonomy: business 4xx surfaces the receiver reason; 5xx → transient→unavailable; 403 → not-permitted. **5 tests green** (MockWebServer, no Docker).
- [X] T007 Add the composed/Floci run profile plumbing: set `{OPERATIONS,HR,CRM,PROCUREMENT}_ADAPTER=http` in `docker-compose.yml` workflow-service env so the real adapters are active for the end-to-end proof (kept off for pure unit tests). base-urls flip to `https://` in US3 (T038) once mTLS is wired.

**Checkpoint**: error taxonomy + real-adapter wiring in place — US1/US2 can proceed.

---

## Phase 3: User Story 1 — Governed actions actually reach the owning services (Priority: P1) 🎯 MVP

**Goal**: Every governed back-office action succeeds against the real services with matching identifiers/amounts.

**Independent Test**: perform each governed action end-to-end on the Floci AWS-imitation deployment; the record appears (or changes) in the owning service with the same figures.

### Derived values (shared across US1 calls)
- [X] T008 [US1] Create `backend/workflow-service/src/main/java/com/kita/workflow/ports/http/DerivedValues.java`: item ref→UUID (via operations `GET /items`), default/primary `locationId` (via operations `GET /locations`), and deterministic `supplierCode` (slug of name) — all deterministic so retries stay idempotent (FR-002).
- [X] T009 [P] [US1] Unit test `DerivedValuesTest` asserting determinism (same input → same `supplierCode`) and the lookup/mapping behaviour with a MockWebServer stub.

### Operations — sales order (structural drift: no `/items` endpoint)
- [X] T010 [P] [US1] Contract test `contract/OperationsSalesContractTest.java` — build the create-sales-order body and bind+validate it against `operations` `SalesDtos.SalesOrderCreateRequest` (`customerRef`, `lines[itemId(UUID),quantity,uom,unitPrice]`); assert the response maps from `id`. Write **red**.
- [X] T011 [US1] Change `OperationsPort` in `backend/workflow-service/.../ports/OperationsPort.java`: `createSalesOrder(customerRef, List<SalesLine>)` atomic-with-lines, **remove** `addSalesOrderLine`, `SalesLine.itemId` resolved to UUID; update javadoc.
- [X] T012 [US1] Rewrite `HttpOperationsAdapter.createSalesOrder` (`.../ports/http/HttpOperationsAdapter.java`) to POST `{customerRef, lines[]}` once, resolve item refs→UUID via `DerivedValues`, read `id` from the response (T010 → green).
- [X] T013 [US1] Update `InMemoryOperationsAdapter` (`.../ports/fake/`) and `SalesOrderWorkflow` (+ any caller of the removed `addSalesOrderLine`) to the new create-with-lines port; fix compilation + existing sales unit tests.

### Operations — build (derived locationId)
- [X] T014 [P] [US1] Contract test `contract/OperationsBuildContractTest.java` — bind the build body against `operations` `BuildDtos.BuildRequest` (`finishedItemId(UUID), locationId(UUID), quantity`); response maps from `id`. Red.
- [X] T015 [US1] Rewrite `HttpOperationsAdapter.build` to send `finishedItemId` (ref→UUID) + **derived `locationId`** + `quantity`, read `id` (T014 → green); adjust `OperationsPort.build`/`BuildResult` + fake as needed.

### Procurement — PO, receiving, suppliers
- [X] T016 [P] [US1] Contract test `contract/ProcurementPoContractTest.java` — bind create-PO against `CreatePurchaseOrderRequest` (`supplierId(UUID)`, `lines[itemRef,qtyOrdered,agreedPrice]`, `poNo` omitted); response maps from `id`. Red.
- [X] T017 [US1] Rewrite `HttpProcurementAdapter.createPurchaseOrder` + `ProcurementPort.PoLine` to `itemRef/qtyOrdered/agreedPrice`, `supplierId` UUID, read `id`; update `InMemoryProcurementAdapter` + procurement workflow.
- [X] T018 [P] [US1] Contract test `contract/ProcurementReceiptContractTest.java` — bind receipt body against `RecordReceiptRequest` (`lines[itemRef,qtyReceived]`); response maps real receipt id/PO-status keys. Red.
- [X] T019 [US1] Rewrite `HttpProcurementAdapter.receive` + `ProcurementPort.ReceiptLine` to `itemRef/qtyReceived`, read the real response keys (T018 → green).
- [X] T020 [P] [US1] Contract test `contract/ProcurementSupplierContractTest.java` — bind create-supplier against `CreateSupplierRequest` (derived `supplierCode`, `name`, …), update against `UpdateSupplierRequest`, and supplied-items against `SupplierItemRequest` (POST per item). Red.
- [X] T021 [US1] Rewrite `HttpProcurementAdapter.createSupplier`/`updateSupplier`/`setSuppliedItems`: derive `supplierCode`, read `id`, and change supplied-items to **POST per item** (not PUT list); update `ProcurementPort.SupplierInput`/`SuppliedItem` + fake.

### CRM + HR
- [X] T022 [P] [US1] Contract test `contract/CrmCustomerContractTest.java` — binds create/update customer against `crm` `CreateCustomerRequest`/`UpdateCustomerRequest`; response maps from `id`.
- [X] T023 [US1] Rewrite `HttpCrmAdapter.createCustomer`/`updateCustomer`: **derive `customerCode` + `type`** (receiver requires both, no staff member supplies them), update sends the `status` enum (not an `active` flag), read `id`.
- [X] T024 [P] [US1] Contract test `contract/HrEmployeeContractTest.java` — drives the adapter against JSON serialised from hr's **real `EmployeeResponse`**. **Found the deepest drift**: HR has neither `active` nor `roles` (only a `status` enum, and *no back-office role data at all*), so the old `bodyTo(EmployeeView.class)` made every actor inactive+roleless ⇒ every governed action failed before reaching any owning service. Fixed: `active` ← `status==ACTIVE`; roles ← new configurable `workflow.hr.position-roles` map (`HrPositionRoles`), **fail-closed** when unmapped. ⚠️ See "Open decision" note at the end of this file.

### End-to-end proof
- [X] T025 [US1] Create `specs/018-secure-service-contracts/e2e/run-governed-actions.sh` — performs each governed action against the running stack and asserts the record is visible in the owning service (PO/lines/qty/price in procurement, received qty in operations stock, sales order + lines in operations); an invalid input returns the receiver's actual reason (FR-003).

**Checkpoint**: every governed action works against the real receivers; per-call contract tests green (SC-001/SC-002; unblocks 016 SC-007 once transport lands).

---

## Phase 4: User Story 2 — Integration is verified against the real services (Priority: P1)

**Goal**: drift on either side of any integration fails the build, not a running environment.

**Independent Test**: rename a field on either side → the matching automated check fails and names the mismatch; add an unverified call → the guard reports it.

- [X] T026 [US2] Generalise `ContractSupport` (T004) into the reusable bind-and-validate assertion used by all `contract/*ContractTest` classes (single ObjectMapper, receiver DTO binding, Bean-Validation) and de-duplicate the per-call tests onto it.
- [X] T027 [US2] Add `contract/PortCoverageGuardTest.java` — reflectively enumerate every method on `OperationsPort`/`ProcurementPort`/`CrmPort`/`HrPort` and assert each has a registered contract test; an added-but-unverified call **fails the build** (FR-005 scenario 3).
- [X] T028 [P] [US2] Add `contract/FakeContractParityTest.java` — assert each `InMemory*Adapter` accepts/produces the same shapes as the real contract (FR-006), so a fake cannot silently diverge.
- [X] T029 [US2] CI gate verified: `.github/workflows/ci.yml` already runs `:workflow-service:build` (→ `check` → `test`), so the contract package + coverage guard gate merges. No change needed (SC-003, Constitution VII).
- [X] T030 [P] [US2] Add a "drift bites" note to `backend/workflow-service/README.md` documenting how the contract tests catch a renamed/missing/re-typed field on either side.

**Checkpoint**: the verification gap that caused the drift is closed; SC-003 measurable.

---

## Phase 5: User Story 3 — Internal traffic encrypted + mutually authenticated (Priority: P2)

**Goal**: all internal traffic (every HTTP hop + Postgres/Redis) encrypted; each service verifies its caller and **persists** every refusal.

**Independent Test**: observe internal traffic → no readable business data; present no/untrusted/expired/non-allowlisted identity → refused **and** a `service_call_refusal` row appears; genuine peer behaves unchanged.

### Shared identity + refusal (repeated per receiving service — same pattern, different module)
- [X] T031 [P] [US3] operations-service: add `ServiceIdentityFilter` (`backend/operations-service/.../security/`) verifying the peer X.509 cert vs the service allowlist, refusing (401/403) + persisting a refusal; `ServiceCallRefusal` entity + repository; Flyway `V*__service_call_refusal.sql` in `operations` schema.
- [X] T032 [P] [US3] hr-service: same `ServiceIdentityFilter` + `ServiceCallRefusal` entity/repo + Flyway migration in `hr` schema.
- [X] T033 [P] [US3] crm-service: same filter + entity/repo + Flyway migration in `crm` schema.
- [X] T034 [P] [US3] procurement-service: same filter + entity/repo + Flyway migration in `procurement` schema.
- [X] T035 [P] [US3] workflow-service: same filter + entity/repo + Flyway migration in `workflow` schema (it receives calls from the gateway).
- [X] T036 [P] [US3] Unit test `ServiceIdentityFilterTest` (in one service, shared pattern): refuses NO_CERT / UNTRUSTED_CA / EXPIRED / NOT_ALLOWLISTED, persists the row with the right reason, and lets an allowlisted peer through.

### TLS wiring
- [X] T037 [US3] Add `server.ssl.bundle` + `server.ssl.client-auth=want` + `spring.ssl.bundle.pem/jks.<svc>` (mounted cert paths) to every service `application.yml` (`operations`, `hr`, `crm`, `procurement`, `workflow`) and the gateway.
- [X] T038 [US3] Build the `RestClient.Builder` in `workflow-service` (and the gateway's outbound client) from the SSL bundle so calls present the service identity + trust the CA; point base-urls at `https://`.
- [X] T039 [P] [US3] Postgres TLS: enable server TLS on the Postgres container (dev-CA cert) and set every service's JDBC URL to `sslmode=require` (local) / `verify-full` w/ CA (production-parity) — `docker-compose.yml` + `sim/aws-imitation/` + each `application.yml`.
- [X] T040 [P] [US3] Redis TLS: enable Redis TLS (`--tls-port` + server cert) and Lettuce SSL in `operations-service` (the only Redis client).
- [X] T041 [US3] Compose/Floci: add the `bootstrap-certs` init step (T003) as a dependency of all services, mount the bundle volume, and confirm `docker compose up` / the Floci deploy comes up encrypted with **no manual cert steps** (SC-008, FR-012).

### Verification
- [ ] T042 [P] [US3] Integration test (CI): a caller with no/untrusted cert is refused + recorded; a genuine mTLS caller succeeds — asserts SC-005 and no-readable-data intent (SC-004).
- [ ] T043 [US3] SC-007 regression: run each service's existing behaviour/contract suite with TLS on and confirm no business-outcome/authorization/audit change (FR-010).

**Checkpoint**: internal traffic encrypted + mutually authenticated; refusals persisted; behaviour unchanged.

---

## Phase 6: User Story 4 — Certificates rotate without an outage (Priority: P3)

**Goal**: replace service credentials before expiry with zero failed calls; remaining validity is observable.

**Independent Test**: rotate a service's certs under a steady call loop → 0 failed calls; inspect remaining validity before it lapses.

- [X] T044 [US4] Set `reload-on-update: true` on every `spring.ssl.bundle.*` so replacing the mounted cert files reloads the SSL context in place (no restart).
- [X] T045 [P] [US4] Enable the `management.health.ssl` certificate-expiry health indicator + an info/actuator contributor exposing each bundle's `notAfter`; expose via `management.endpoints` in each `application.yml`.
- [ ] T046 [US4] Rotation integration test (CI): under a steady call loop, swap a service's mounted certs for freshly issued ones and assert **0** failed calls across the swap (SC-006).

**Checkpoint**: certs rotate live; validity discoverable before expiry.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T047 [P] Log-hygiene test/guard: assert no cert, private key, or `X-Kita-User` value is emitted in structured logs (FR-011).
- [ ] T048 [P] Update `backend/workflow-service/README.md` (corrected contracts + derived values) and each receiving service README (mTLS + refusal table note).
- [ ] T049 Run `./gradlew spotlessApply` then `:workflow-service:build` + affected service builds green (Constitution VII); confirm CI is green against the known-red baseline ([[kita-ci-known-red-jobs]]).
- [ ] T050 Run `specs/018-secure-service-contracts/quickstart.md` end-to-end on the Floci AWS-imitation deployment (all of SC-001…SC-008).
- [ ] T051 [P] Capture the implementation context to memory via the `kita-context-capture` skill (contract-drift catalogue, `client-auth=want` decision, per-service refusal pattern) and update `[[spec-016-workflow-ui-progress]]` (SC-007 now unblockable).

---

## Dependencies & Execution Order

- **Setup (P1)** → no deps.
- **Foundational (P2)** → after Setup; blocks all stories (T005 error taxonomy is used by US1 + US3).
- **US1 (P3 phase)** → after Foundational. T008 (DerivedValues) precedes T011–T024. Each contract test (red) precedes its adapter fix (green).
- **US2** → after US1 (generalises the per-call tests + guards them). Independently testable (guard catches drift).
- **US3** → after Foundational; independent of US1/US2 in principle, but sequence US1→US2→US3 for MVP-first delivery. T031–T036 (per-service filter+table) are [P]; T037–T041 wire TLS; T042–T043 verify.
- **US4** → after US3 (rotation needs the bundles to exist).
- **Polish (P7)** → after the desired stories.

### Within a story
- Contract test (red) → adapter/impl (green) → update fakes/workflows/existing tests.
- Models/migrations before the filter that writes them; TLS bundle config before the client that uses it.

### Parallel opportunities
- Setup T002/T003/T004 in parallel.
- US1 contract tests T010/T014/T016/T018/T020/T022/T024 are [P] (different files) — write them all red first.
- US3 per-service filter+table tasks T031–T035 are [P] (different modules); T039/T040 datastore TLS in parallel.

---

## Parallel Example: US1 contract tests (write red first)
```bash
Task: "OperationsSalesContractTest"    # T010
Task: "OperationsBuildContractTest"    # T014
Task: "ProcurementPoContractTest"      # T016
Task: "ProcurementReceiptContractTest" # T018
Task: "ProcurementSupplierContractTest"# T020
Task: "CrmCustomerContractTest"        # T022
Task: "HrEmployeeContractTest"         # T024
```

## Implementation Strategy

### MVP (US1 + US2 guard)
1. Setup → Foundational → US1 (all governed calls corrected + contract-tested) → US2 (coverage guard + CI gate).
2. **STOP & VALIDATE**: contract suite green; e2e actions visible in owning services.

### Incremental delivery
1. US1 → the console's governed actions take real effect (MVP; unblocks 016 once US3 lands).
2. US2 → drift can never silently return.
3. US3 → all internal traffic encrypted + mutually authenticated + refusals persisted (broadest slice).
4. US4 → certs rotate without downtime.
5. Polish → logs scrubbed, docs, quickstart on Floci, memory captured.

## ⚠️ Open decision — where do back-office roles come from? (found during T024)

hr-service holds **no back-office role data**: `Employee` has no roles field, `EmployeeResponse` returns
none, and hr's own `Role` enum (`HR_ADMIN`/`PAYROLL_OFFICER`/`MANAGER`/`EMPLOYEE_SELF`) governs HR's own
API, not workflow roles (`SALES`, `CASHIER`, `PROCUREMENT_APPROVER`, …). But spec 007 locked "roles come
from HR, never the header", and `ActorResolver` requires them. That contradiction is why every governed
action failed at actor resolution against real HR — it was never exercised outside the in-memory fake.

018 does **not** invent roles. It maps what HR actually has (`status` → active) and adds an explicit,
**fail-closed** `workflow.hr.position-roles` map so a deployment states "a Sales Clerk may act as SALES".
Unmapped ⇒ no roles ⇒ action refused (403), never silently granted.

**Needs a decision before the Floci e2e (T025/T050) can pass end-to-end** — pick one:
1. Populate `workflow.hr.position-roles` per deployment (works today; zero code change).
2. Add role storage to hr-service (a real change to 004's data model — outside 018's "caller is corrected" scope).
3. Source roles from **spec 017** (account→employee identity), which is queued and is arguably where this belongs.

## Notes
- [P] = different files/modules, no incomplete-task dependency.
- US1 changes port signatures (drops `addSalesOrderLine`, `SalesLine.itemId`→UUID, PoLine/ReceiptLine field renames) → always update the `InMemory*Adapter` fakes + workflows + existing unit tests in the same task (compilation gate).
- Docker-off locally → contract/unit tests run locally; ITs + the Floci e2e run in CI / on the Floci stack ([[kita-backend-service-conventions]]).
- Commit per task or logical group; simple messages, no AI attribution (PR body may include it).
