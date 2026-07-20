---
description: "Task list for 013-hr-ui"
---

# Tasks: HR & Payroll Service UI

**Input**: Design documents from `/specs/013-hr-ui/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — the constitution mandates TDD. Frontend = Vitest + Testing Library (red-first per story);
backend = MockMvc contract tests (red-first) for each new read endpoint (FR-015).

**Organization**: By user story. **Evolves the 011 `frontend/` reusing the 012 shared inputs** (no new frontend
framework) and makes a **bounded, read-only** addition to `backend/hr-service` (FR-015). Contracts:
[hr-manifest.md](./contracts/hr-manifest.md) (frontend functions), [hr-read-api.md](./contracts/hr-read-api.md)
(new backend GETs). The 012 shared inputs (`ReferenceInput`/`ListInput`/`idLabels` + `reference`/`list` types)
must be present — **sync `main` first (T001)**.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different files, no dependency on incomplete tasks.

---

## Phase 1: Setup

- [X] T001 **Sync `main` into `013-hr-ui`** (`git merge origin/main`) so the 012 shared inputs
  (`frontend/src/workspace/inputs/ReferenceInput.tsx`, `ListInput.tsx`, `FieldInput.tsx`,
  `workspace/result/idLabels.ts`) and the `reference`/`list` `InputField` kinds + `resultRefs` in
  `frontend/src/services/types.ts` are present; resolve any conflicts; confirm `cd frontend && npm test && npm run build` green
- [X] T002 [P] Create `frontend/src/services/manifests/hr.ts` and point the `hr` entry in `frontend/src/services/registry.ts` at it (migrate the placeholder `employees` function into the module)
- [X] T003 [P] Backend baseline sanity: `cd backend && ./gradlew :hr-service:compileJava :hr-service:compileTestJava` green before any change

---

## Phase 2: Foundational (Shared manifest wiring for US1–US5)

**⚠️ Blocks the user stories — the reference sources are used by every area.**

- [X] T004 Define the shared manifest sources in `frontend/src/services/manifests/hr.ts` — `EMPLOYEES_SOURCE` (`/api/hr/employees`, value `id`, label `employeeNo — firstName lastName`), `LEAVE_TYPES_SOURCE` (`/api/hr/leave/types`), and an `employeeLabels(columns)` helper for `resultRefs` (relabel `employeeId` columns) per contracts/hr-manifest.md

**Checkpoint**: the manifest module + shared sources exist; functions can now be added per story.

---

## Phase 3: User Story 1 - Browse employees and compensation (Priority: P1) 🎯 MVP

**Goal**: Employee list, employee detail, effective-dated compensation, and status history — read-only, via the edge.

**Independent Test**: HR → Employees lists employees; Employee detail (pick an employee) shows attributes
(statutory ids masked); Compensation history shows effective-dated rates in date order.

### Frontend (all endpoints already exist — no backend change)

- [X] T005 [P] [US1] Write `frontend/tests/HrManifest.test.tsx` (red): the `employees`, `employee`, `compensation`, and `status-history` functions render and run against a mocked edge; `employee` uses the reference picker sourced from `/api/hr/employees`
- [X] T006 [US1] Add the **Employees** read functions (`employees`, `employee`, `compensation`, `status-history`) to `frontend/src/services/manifests/hr.ts` per contracts/hr-manifest.md
- [X] T007 [US1] Verify US1: `cd frontend && npm test && npm run build` green

**Checkpoint**: MVP — browse employees, open one, see compensation + status history, all through the edge.

---

## Phase 4: User Story 2 - Time & attendance and leave (Priority: P2)

**Goal**: Worked time & premiums, leave balances, and leave requests (list/detail — the new read).

**Independent Test**: Worked time (pick employee + start/end) renders hours + OT/holiday/night-diff premiums;
Leave balances lists per-type balances; Leave requests lists requests with status; a request opens in detail.

### Backend read (FR-015)

- [X] T008 [US2] Write `backend/hr-service/src/test/java/com/kita/hr/api/LeaveRequestReadContractTest` (MockMvc, red): `GET /leave/requests` lists created requests, the `employeeId`/`status` query filters narrow it, and `GET /leave/requests/{id}` returns one or **404** (stub security → HR_ADMIN)
- [X] T009 [US2] Implement leave-request reads — add `listRequests(employeeId, status)` + `getRequest(id)` to `leave/LeaveService.java` (reuse `LeaveRequestRepository` + `LeaveRequestResponse.from`), and `GET /leave/requests` (optional `employeeId`/`status`) + `GET /leave/requests/{id}` in `api/LeaveController.java`, role-gated (HR_ADMIN/MANAGER/PAYROLL_OFFICER/EMPLOYEE_SELF), 404 on absent

### Frontend

- [X] T010 [P] [US2] Extend `frontend/tests/HrManifest.test.tsx` (red): `worked-time` (detail), `leave-balances` (table), `leave-requests` (table), and `leave-request` (detail) render + run against a mocked edge
- [X] T011 [US2] Add the **Attendance** `worked-time` and **Leave** read functions (`leave-balances`, `leave-requests`, `leave-request`, `leave-types`, `deduction-rules`) to `hr.ts`; resolve `employeeId` result columns via `employeeLabels`
- [X] T012 [US2] Verify US2: `./gradlew :hr-service:build` (leave-request contract) + `cd frontend && npm test && npm run build` green

**Checkpoint**: time + leave are viewable, including the now-listable leave requests.

---

## Phase 5: User Story 3 - Review a payroll run and its register (Priority: P2)

**Goal**: List payroll runs (the new read), open a run, and view its register (per-employee lines + totals).

**Independent Test**: Payroll runs lists runs (period + state); Payroll run detail opens one; Register shows
per-employee gross/deductions/net and reconciling totals.

### Backend read (FR-015)

- [X] T013 [US3] Write `backend/hr-service/src/test/java/com/kita/hr/api/PayrollRunReadContractTest` (MockMvc, red): `GET /payroll/runs` lists created runs; `GET /payroll/runs/{id}` returns one or **404** (stub security → HR_ADMIN)
- [X] T014 [US3] Implement payroll-run reads — add `list()` + `get(id)` to `payroll/PayrollRunService.java` (reuse `PayrollRunRepository` + `PayrollRunResponse.from`), and `GET /payroll/runs` + `GET /payroll/runs/{id}` in `api/PayrollController.java`, role-gated (HR_ADMIN/PAYROLL_OFFICER), 404 on absent

### Frontend

- [X] T015 [P] [US3] Extend `frontend/tests/HrManifest.test.tsx` (red): `payroll-runs` (table), `payroll-run` (detail), and `register` (detail) render + run against a mocked edge
- [X] T016 [US3] Add the **Payroll** read functions (`payroll-runs`, `payroll-run`, `register`) to `hr.ts` (register renders per-employee payslips + totals)
- [X] T017 [US3] Verify US3: `./gradlew :hr-service:build` (payroll-run contract) + `cd frontend && npm test && npm run build` green

**Checkpoint**: runs are listable/reviewable end-to-end; a run's lines come from its register.

---

## Phase 6: User Story 4 - Run payroll and record time/leave (Priority: P3)

**Goal**: The write actions — create/compute/finalize/cancel a payroll run, ingest DTRs (list body), file/decide
leave, and the employee/deduction writes — each a validated form, verifiable via the US2/US3 reads.

**Independent Test**: Create a run → it appears in Payroll runs as draft; compute → register populates; finalize
twice → finalizes once (no double-post); ingest DTRs → worked time reflects it; file leave → it appears in Leave
requests; decide → its status transitions.

### Frontend (writes already exist — no backend change)

- [X] T018 [P] [US4] Extend `frontend/tests/HrManifest.test.tsx` (red): `create-run` blocks on missing required inputs then POSTs; `ingest-dtr` submits a **list** body (array of DTR rows); `file-leave` + `decide-leave` run; `create-employee` validates + POSTs (mock the edge)
- [X] T019 [US4] Add the **write** functions to `hr.ts` — `create-employee`, `update-employee`, `add-compensation`, `add-loan`, `ingest-dtr` (list), `create-leave-type`, `file-leave`, `decide-leave`, `accrue-leave`, `create-deduction-rule`, `create-run`, `compute-run`, `finalize-run`, `cancel-run` — with enum selects (EmploymentType/RunType/PayTreatment/LeaveStatus) and employee/leave-type reference pickers per contracts/hr-manifest.md
- [X] T020 [US4] Verify US4: `cd frontend && npm test && npm run build` green; create→list round-trips (runs, leave requests) work against the mocked edge

**Checkpoint**: the full write surface works and is verifiable via the FR-015 reads.

---

## Phase 7: User Story 5 - Payslips, register, and statutory remittance (Priority: P3)

**Goal**: The outputs of a finalized run — payslips, the register (from US3), and per-contribution statutory
remittances.

**Independent Test**: For a finalized run, Payslips lists per-employee earnings/deductions/net; Remittances shows
per-contribution totals; the register (US3) shows all employees + totals.

### Frontend (endpoints already exist — no backend change)

- [X] T021 [P] [US5] Extend `frontend/tests/HrManifest.test.tsx` (red): `payslips` (table, optional runId/employeeId) and `remittances` (detail) render + run against a mocked edge
- [X] T022 [US5] Add the **Outputs** functions (`payslips`, `remittances`) to `hr.ts` (register is already present from US3)
- [X] T023 [US5] Verify US5: `cd frontend && npm test && npm run build` green

**Checkpoint**: the compliance outputs complete the HR workspace.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T024 [P] Accessibility/responsive pass: the reused 012 inputs stay keyboard-navigable with visible focus, and HR result shapes (register/payslip/remittance detail + tables) scroll (not the page) at the 011 768px floor across `frontend/src/`
- [X] T025 [P] Docs: add an HR note to `frontend/README.md` (the HR manifest reuses the 012 inputs) and note the FR-015 read endpoints in `backend/hr-service/README.md`
- [X] T026 [P] Confirm CI covers both streams: the 011 `frontend` job runs the new `HrManifest` suite; the backend job runs `:hr-service:build` (the new contract tests) — adjust `.github/workflows/ci.yml` only if a gap exists
- [X] T027 Full verification: `cd backend && ./gradlew :hr-service:build` + `cd frontend && npm test && npm run build` green; run `quickstart.md` end-to-end (reads → writes → list/verify) with 0 real cloud

---

## Dependencies & Execution Order

- **Setup (T001–T003)** → **Foundational (T004)** → user stories. **T001 (sync main) is a hard prerequisite** —
  the manifest depends on the 012 shared inputs.
- **US1 (T005–T007)**: after Foundational; frontend-only (all employee reads exist). MVP.
- **US2 (T008–T012)**: after Foundational; adds `GET /leave/requests` (+`/{id}`).
- **US3 (T013–T017)**: after Foundational; adds `GET /payroll/runs` (+`/{id}`); independent of US2.
- **US4 (T018–T020)**: after US2 + US3 (verifies via their reads); frontend-only.
- **US5 (T021–T023)**: after Foundational; frontend-only.
- **Polish (T024–T027)**: after the desired stories.

### Within each story
- Backend contract test ([P]) and frontend manifest test ([P]) are written first (red), then the implementation, then verify.

### Parallel Opportunities
- Setup: T002 ∥ T003 (after T001).
- US2/US3: the backend contract test precedes its impl; frontend test ([P]) alongside.
- Polish: T024 ∥ T025 ∥ T026.

---

## Implementation Strategy

### MVP First (US1)
Sync main → Setup → Foundational → US1 → **STOP & VALIDATE**: browse employees, open one, see compensation +
status history — the second per-service UI proving HR reuses the 012 framework end-to-end (frontend-only).

### Incremental Delivery
US1 (employees) → US2 (attendance/leave + leave-request read) → US3 (payroll runs review + run read) → US4
(writes) → US5 (outputs). US1/US4/US5 add **no backend code**; US2/US3 each add one bounded read pair.

---

## Notes
- **Backend change is read-only + additive** (FR-014/FR-015): new GET handlers + `list()/get(id)` service methods
  on existing repositories/DTOs; **role-gated** like siblings; tenant-scoped; no existing endpoint or write logic
  changes.
- **No new frontend framework** — HR authors `manifests/hr.ts` against the 012 shared inputs.
- **PII** (statutory ids) is already masked server-side; the UI displays as-is and persists nothing sensitive.
- Monetary/decimal values are displayed exactly as returned; the UI performs no payroll arithmetic.
- Commit after each story (or logical group); simple messages, no AI attribution.
