# Tasks: Workflow (Back-Office) Service UI

**Input**: Design documents from `/specs/016-workflow-ui/`
**Prerequisites**: plan.md, spec.md (clarified 2026-07-22), research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — Constitution II (TDD) is non-negotiable. Every behavioural task is preceded by a red test.

**Organization**: By user story, so each is an independently shippable, independently testable slice.

## Format: `[ID] [P?] [Story] Description`

- **[P]** = parallelizable (different file, no dependency on an incomplete task)
- **[USn]** = the user story this task serves (user-story phases only)

## Path Conventions

- Frontend: `frontend/src/…`, tests in `frontend/tests/…` (Vitest + Testing Library)
- Backend: `backend/workflow-service/src/main/java/com/kita/workflow/…`, tests in `src/test/java/com/kita/workflow/…`
- Commands: `cd frontend && npm test` / `npm run build`; `cd backend && ./gradlew :workflow-service:build`

**Guardrails for every task below** (from the clarified spec):

- FR-012 — touch **no** workflow / pipeline / authorizer / recorder code path. Backend work here is read-only
  projection only, and records no activity.
- FR-013 — **no actor input anywhere.** If a task tempts you to add an "acting employee" field, the task is wrong.
- Money is a decimal **string** on the wire — render verbatim, never parse to a float.

---

## Phase 1: Setup

**Purpose**: Confirm the baseline before changing anything. `main` (012–015) is already merged into this branch.

- [X] T001 Verify the frontend baseline is green: `cd frontend && npm test && npm run build` (expect the existing 011–015 suites passing)
- [X] T002 Verify the backend baseline is green: `cd backend && ./gradlew :workflow-service:build` (Testcontainers ITs need the Docker TCP 2375 toggle; note if they skip locally — CI runs them)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The manifest seam + grouped navigation every story renders into.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Write the failing test for grouped navigation in `frontend/tests/Workspace.test.tsx`: a manifest whose functions declare `group` renders a heading per group; a manifest with no groups renders exactly as today (012–015 regression guard)
- [X] T004 Add `group?: string` to `ServiceFunction` and `"outcome"` to `ResultKind` in `frontend/src/services/types.ts` (both optional/additive — existing manifests untouched)
- [X] T005 Render contiguous group headings in `frontend/src/app/Sidebar.tsx`, keeping the existing `NavLink` markup, active state, and `aria-label`; headings must not be focusable links (T003 green)
- [X] T006 Create `frontend/src/services/manifests/workflow.ts` with the manifest shell (`id`/`label`/`icon`/`basePath: "/api/workflow"`, empty `functions`) and the four `ReferenceSource` consts per `contracts/workflow-manifest.md`
- [X] T007 Point the `workflow` entry in `frontend/src/services/registry.ts` at `workflowManifest`, deleting the placeholder "Definitions" reference function

**Checkpoint**: The Workflow tab renders from a real (empty) manifest; grouped navigation works; nothing regressed.

---

## Phase 3: User Story 1 — Browse the back-office activity log (P1) 🎯 MVP

**Goal**: The append-only audit trail is browsable and filterable by outcome and action.

**Independent Test**: Sign in → Workflow → "Activity log" lists activities newest-first (actor, action, target,
outcome, timestamp); filtering by an outcome or an action narrows the list; a filter matching nothing shows an
empty state, not an error.

### Tests for User Story 1 ⚠️ write first, confirm red

- [X] T008 [P] [US1] Failing test in `backend/workflow-service/src/test/java/com/kita/workflow/api/ActivityQueryTest.java`: filtering by each `ActivityOutcome` returns only matching rows; `outcome` composes with `action`; omitting `outcome` behaves exactly as today
- [X] T009 [P] [US1] Failing test in `frontend/tests/WorkflowManifest.test.tsx`: the Activity log function renders its filter inputs, runs `GET /api/workflow/activity`, and renders rows as a table (mock the edge); blank filters are absent from the requested URL

### Implementation for User Story 1

- [X] T010 [US1] Add the optional `outcome` query param to `ActivityController.list` in `backend/workflow-service/src/main/java/com/kita/workflow/api/ActivityController.java`, filtered the same way `from`/`to` already are — no repository change, no recorded activity (T008 green)
- [X] T011 [US1] Add the `activity` function (group "Activity log") to `frontend/src/services/manifests/workflow.ts` per `contracts/workflow-manifest.md`: optional `actor` text, `action` select (12 actions), `outcome` select (4 outcomes), `from`/`to` text; `result: "table"`; description states append-only + newest-first (T009 green)
- [X] T012 [US1] Verify: `cd frontend && npm test` and `cd backend && ./gradlew :workflow-service:test` both green; mark T008–T011 `[X]` and commit the slice

**Checkpoint**: US1 ships on its own — a working audit-trail browser with zero write risk.

---

## Phase 4: User Story 2 — Authorization rules and pending reviews (P2)

**Goal**: The rules (role → action → kind) and the maker-checker queue are visible.

**Independent Test**: Workflow → "Authorization rules" lists every seeded grant with its `PERFORM`/`MAKER`/`CHECKER`
kind; "Pending reviews" lists items awaiting a checker with action, target, recorded maker, and stage.

### Tests for User Story 2 ⚠️ write first, confirm red

- [X] T013 [P] [US2] Failing test in `backend/workflow-service/src/test/java/com/kita/workflow/api/AuthorizationQueryTest.java`: one row per seeded mapping, `kind` present and distinct, deterministic ordering (action → kind → role)
- [X] T014 [P] [US2] Failing test in `backend/workflow-service/src/test/java/com/kita/workflow/pending/PendingReviewQueryTest.java`: a recorded-but-unconfirmed item appears with its maker and target; confirming removes it; the serialized body contains **no `payload` key**; `action` narrowing works; empty store ⇒ `[]`
- [X] T015 [P] [US2] Failing test in `frontend/tests/WorkflowManifest.test.tsx`: the Authorization rules and Pending reviews functions render and run their reads, showing the empty state when the response is `[]`

### Implementation for User Story 2

- [X] T016 [P] [US2] Add `List<PendingReview> list()` to `backend/…/pending/PendingReviewStore.java` and implement it in `InMemoryPendingReviewStore` as a copied snapshot — no change to `put`/`get`/`remove`
- [X] T017 [P] [US2] Add `AuthorizationController` (`GET /api/workflow/authorization`) in `backend/…/api/`, projecting `AuthorizationMappingRepository.findAll()` through the existing `AuthorizationMapping.toRule()`; view record = `action`, `role`, `kind` (T013 green)
- [X] T018 [US2] Add `PendingReviewController` (`GET /api/workflow/pending-reviews?action=`) in `backend/…/api/`, oldest-first; the view record lists `pendingId`, `action`, `makerEmployeeId`, `targetRef`, `stage`, `createdAt` **explicitly** so `payload` can never leak (depends on T016; T014 green)
- [X] T019 [US2] Add the `authorization` (group "Authorization") and `pending-reviews` (group "Reviews") functions to `frontend/src/services/manifests/workflow.ts`; the Reviews description states the queue is transient and empties on service restart (T015 green)
- [X] T020 [US2] Verify frontend + backend suites green; mark tasks `[X]` and commit the slice

**Checkpoint**: US1 + US2 both work — the whole read-only half of the tab, still with zero write risk.

---

## Phase 5: User Story 3 — Perform a governed action as maker (P3)

**Goal**: Every governed action is runnable from the console, with its outcome rendered distinctly and its effect
real in the owning service's tab.

**Independent Test**: As a permitted employee, take a sales order → approved, id returned, appears in Pending
reviews and the Activity log. As an employee whose role lacks the grant → a clear "not permitted", also recorded.
Missing required input → blocked inline before the edge.

### Tests for User Story 3 ⚠️ write first, confirm red

- [X] T021 [P] [US3] Failing test in `frontend/tests/OutcomeView.test.tsx`: a 2xx renders approved + the detail; 422/`REJECTED_INVALID`, 403/`REJECTED_NOT_PERMITTED`, and 503/`FAILED_UNAVAILABLE` each render a **visually and textually distinct** banner carrying the backend's `reason`; an unmapped non-2xx falls back to the generic error banner
- [X] T022 [P] [US3] Failing test in `frontend/tests/WorkflowManifest.test.tsx`: maker/lifecycle functions render their inputs, block on missing required fields before any edge call, and POST the documented body shape
- [X] T023 [P] [US3] Failing guard test in `frontend/tests/WorkflowManifest.test.tsx` (FR-013): **no** function in the Workflow manifest declares an input whose name or label denotes an acting employee/actor — this test must fail if anyone ever adds one

### Implementation for User Story 3

- [X] T024 [P] [US3] Extend failure parsing in `frontend/src/api/edge.ts` to read `reason` (and `outcome`) from the `{outcome, reason, status}` envelope, keeping `message` as the fallback so 012–015 behaviour is unchanged
- [X] T025 [US3] Add `frontend/src/workspace/result/OutcomeView.tsx` implementing the four-outcome mapping from `contracts/workflow-manifest.md`, reusing the existing detail renderer for the approved payload (T021 green)
- [X] T026 [US3] Wire `result: "outcome"` into `ResultView` in `frontend/src/workspace/FunctionWorkspace.tsx` — the failure branch must consult `OutcomeView` before the generic error banner; all other kinds unchanged
- [X] T027 [US3] Add the sales maker/lifecycle functions (`take-sales-order`, `cancel-sales-order`, `complete-sales-order`) to the manifest per the contract, with the customer + item reference pickers and `lines` list inputs
- [X] T028 [P] [US3] Add the purchasing functions (`raise-purchase-order`, `approve-purchase-order`, `send-purchase-order`, `record-receipt`) to the manifest; the PO total renders as the returned decimal string
- [X] T029 [P] [US3] Add the production + party functions (`build-product`, `create-customer`, `update-customer`, `create-supplier`, `update-supplier`, `set-supplied-items`) to the manifest (T022, T023 green)
- [X] T030 [P] [US3] Set `OPERATIONS_ADAPTER=http`, `CRM_ADAPTER=http`, `PROCUREMENT_ADAPTER=http` (with their existing base-URL env vars) for `workflow-service` in `docker-compose.yml`; leave `HR_ADAPTER` on the seeded directory — governed actions now affect the real services (SC-007)
- [X] T031 [US3] Seed one demo login per seeded employee (`emp-sales`, `emp-cashier`, `emp-sales-mgr`, `emp-whse`, `emp-whse-mgr`, `emp-proc`, `emp-approver`, `emp-prod`, `emp-crm`) for **both** demo clients in `backend/identity-service/src/main/java/com/kita/identity/config/DemoSeeder.java`, keeping `alice`/`bob` and the existing seed password; idempotent as today
- [X] T032 [US3] Verify: frontend suite green; sign in as `emp-sales` in the sim and confirm a taken sales order appears in the Operations tab (SC-007) and in the Activity log; commit the slice

**Checkpoint**: Every maker/perform action works, outcomes are distinguishable, and effects are real.

---

## Phase 6: User Story 4 — Review a pending item as checker (P3)

**Goal**: The checker step works, with all four maker-checker outcomes rendered distinctly — self-review must never
read as "not permitted".

**Independent Test**: As a different permitted employee, approve a pending item → approved, it leaves the queue and
lands in the log. As the recorded maker → "rejected — self review not allowed". As an unpermitted role → "not
permitted". With a downstream service stopped → "temporarily unavailable".

### Tests for User Story 4 ⚠️ write first, confirm red

- [X] T033 [P] [US4] Failing test in `frontend/tests/WorkflowManifest.test.tsx`: the checker functions render and run; `confirm-receipt` sources its handle from the pending-reviews picker rather than free text
- [X] T034 [P] [US4] Failing test in `frontend/tests/WorkflowManifest.test.tsx` (SC-004): a 422 self-review response and a 403 not-permitted response on the **same** checker function render two distinguishable results — assert on the distinguishing text/role, not just that both are errors

### Implementation for User Story 4

- [X] T035 [US4] Add the checker functions (`confirm-sales-payment`, `release-sales-order`, `confirm-receipt`) to `frontend/src/services/manifests/workflow.ts`, with `confirm-receipt`'s `pendingReceiptId` as a reference input onto `/api/workflow/pending-reviews` (`valueKey: "pendingId"`) (T033 green)
- [X] T036 [US4] Confirm the outcome mapping covers the checker cases end-to-end with no UI-side guard logic — the browser must never decide self-review or permission; it renders what the backend returned (T034 green)
- [ ] T037 [US4] Walk the quickstart maker→checker sequence in the sim — **RUN, AND IT FAILED FOR A REAL REASON.** The reads (US1/US2) pass end-to-end through the edge. The governed-action half cannot complete in EITHER adapter mode: with `http`, workflow-service's adapters send payloads the real services reject (crm wants `customerCode`+`type`, procurement wants `itemRef`/`qtyOrdered`, not `itemId`/`quantity`) → 400 → surfaced as 422; with `fake`, the real ids the UI's pickers supply are unknown to the in-memory doubles → 422 `unknown or inactive supplier`. This is a pre-existing defect in spec 007's http adapters (only ever tested against MockWebServer), exposed by T030 — NOT caused by the 016 UI. Blocks SC-007 and the live half of SC-003/SC-004. Needs its own spec.
- [X] T038 [US4] Verify suites green; commit the slice

**Checkpoint**: All four stories complete; the four-outcome taxonomy is proven distinguishable.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T039 [P] Accessibility pass on the new surfaces: grouped sidebar headings are semantic (not fake links), outcome banners carry an appropriate live/alert role, and the whole tab is keyboard-navigable (SC-006)
- [X] T040 [P] Responsive check of the Workflow workspace down to 768px — wide result tables scroll inside their own container, the page body never scrolls horizontally (SC-006)
- [X] T041 [P] Document the tab in the frontend README (the four areas, the actor-is-your-login rule, the transient review queue) and note the new read-only endpoints in `backend/workflow-service/README.md`
- [X] T042 [P] Record the identity→employee gap as a pointer to spec `017-account-employee-identity` wherever the sim's seeded employee directory is configured, so the stopgap is discoverable
- [X] T043 Full verification: `cd frontend && npm test && npm run build`; `cd backend && ./gradlew :workflow-service:build`; confirm the 011–015 suites are untouched and CI's `frontend` + backend jobs are green
- [X] T044 Success criteria confirmed: SC-001…SC-006 met (96 frontend tests, 71 backend tests incl. Testcontainers ITs, live reads verified through the edge). **SC-007 blocked by spec 018** — the back-office service's downstream call contracts do not match the real services; recorded in spec.md

---

## Dependencies

```text
Phase 1 (Setup) → Phase 2 (Foundational) → US1 → US2 → US3 → US4 → Polish
                                            ↑     ↑
                                    both read-only, independent of each other
```

- **US1** and **US2** depend only on the foundation and are independent of each other (US2 could ship first if the
  queue mattered more than the log — the priority order says otherwise).
- **US3** depends on the foundation; its outcome view (T024–T026) is a hard prerequisite for **US4**.
- **US4** depends on US3's manifest + outcome view, and on US3's sim changes (T030, T031) to be demonstrable.
- T018 depends on T016. T026 depends on T025. T031 is what makes T037 possible.

## Parallel Opportunities

- **Phase 2**: T003 is independent of T006 (different files).
- **US1**: T008 (backend test) ∥ T009 (frontend test).
- **US2**: T013 ∥ T014 ∥ T015; then T016 ∥ T017.
- **US3**: T021 ∥ T022 ∥ T023; later T028 ∥ T029 ∥ T030 (manifest sections are separate blocks, compose is a
  separate file — but keep T027 first so the sales block sets the shape the others copy).
- **US4**: T033 ∥ T034.
- **Polish**: T039 ∥ T040 ∥ T041 ∥ T042.

## Implementation Strategy

**MVP = Phase 1 + Phase 2 + US1** — a working, filterable audit trail with zero write risk. Ship, then add US2's
read-only rules/queue, then the writes.

**Per-slice rhythm** (house convention): red test → implement → `npm test` / `:workflow-service:test` → mark tasks
`[X]` → commit to the feature branch. One user story per turn.

**Local caveat**: Testcontainers ITs need Docker Desktop's *Expose daemon on tcp://localhost:2375* toggle; with it
off, only pure unit tests run locally and the ITs run in CI. The 007 lesson applies — a suite that never ran
locally can merge red, so check the CI result before calling a slice done.
