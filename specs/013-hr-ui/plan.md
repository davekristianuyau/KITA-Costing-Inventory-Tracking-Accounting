# Implementation Plan: HR & Payroll Service UI

**Branch**: `013-hr-ui` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/013-hr-ui/spec.md`

## Summary

Fill the 011 console's HR tab with the **full hr-service manifest** — employees + effective-dated compensation,
time & attendance, leave, the payroll-run lifecycle, and payslip/register/remittance outputs — each a manifest
function rendered by the 011 `FunctionWorkspace` and called through the 009 edge, **reusing the shared
workspace-framework inputs built in 012** (reference picker, list input, id→label). Two work streams:

1. **Frontend (bulk)**: the HR manifest (`frontend/src/services/manifests/hr.ts`) + registry wiring. No new
   framework code — 012's `ReferenceInput` / `ListInput` / `resultRefs` cover HR's needs (employee pickers,
   DTR/leave list bodies, id→label).
2. **Backend (bounded, per clarification)**: add the **read-only** endpoints the write-only resources lack —
   **list + get payroll runs** and **list + get leave requests** (FR-015). A run's per-employee lines already
   come from the existing `GET …/register`, so no lines endpoint is added.

Phase 0 read the hr-service controllers to ground both streams and surfaced the **role-gating** model (stub mode
→ demo user is `HR_ADMIN`, so HR works in the sim). See [research.md](./research.md).

## Technical Context

**Language/Version**: **Frontend** — TypeScript 5.5 / React 18 / Vite 5 (evolve `frontend/`, on 011 + the 012
shared inputs). **Backend** — Java 17 / Spring Boot 3.5 / Gradle (`backend/hr-service`, port 8085, spec 004
conventions; see [[kita-backend-service-conventions]]).
**Primary Dependencies**: the 011 design system + workspace framework, the **012 shared inputs**
(`src/workspace/inputs/ReferenceInput`, `ListInput`, `FieldInput`, `result/idLabels`), the generic edge fetch
(`src/api/edge.ts`). Backend — Spring Web + Spring Data JPA (existing repositories/DTOs).
**Storage**: Postgres, schema-in-public via Flyway (hr-service). The new reads are `findAll`/`findById` on
existing repositories — no new tables/migrations.
**Testing**: Frontend — **Vitest + Testing Library** (HR manifest render/run; reuse the 012 input tests). Backend
— **JUnit + MockMvc contract tests** for each new GET (red-first); Testcontainers ITs are CI-only (local Docker
caveat, [[hr-service-build-jdk-requirement]]).
**Target Platform**: modern browsers, responsive to 768px.
**Project Type**: Web app — frontend + a bounded backend read addition.
**Performance Goals**: workspace interactions feel instant; pickers load once per function open; new reads are
simple indexed queries.
**Constraints**: every UI call via the 009 edge with the client session; **backend change limited to read-only
endpoints** (FR-014/FR-015) — no change to existing endpoints or write/business logic; **PII** shown only within
a function result and never persisted in the browser beyond the session cookie (hr-service already masks
statutory ids to a last-four hint); WCAG-AA + keyboard (011).
**Scale/Scope**: ~20 manifest functions across 5 areas (Employees, Attendance, Leave, Payroll, Outputs) + **4 new
read endpoints** (payroll runs list+get, leave requests list+get) with contract tests. No new frontend framework.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Spec-Driven Development | ✅ spec clarified (add-reads decision recorded); this plan precedes code; P1–P3 stories independently testable. |
| II. Test-Driven Development | ✅ Frontend: red-first Vitest for the HR manifest render/run (reusing 012's input coverage). Backend: red-first MockMvc contract tests for each new GET. No payroll math added — compute/deductions stay server-side; the UI only displays. |
| III. Security & Data Integrity | ✅ Reuses 009's httpOnly session + edge tenancy; new endpoints are **read-only**, **role-gated** like siblings, tenant-scoped; **PII** is server-masked and never persisted client-side; no writes added, no existing write/business logic touched. |
| IV. Environment Isolation | ✅ Runs against the 011 local env; schema isolation preserved; 0 real cloud. |
| V. Observability & Debuggability | ✅ UI keeps 011's loading/empty/result/error states; state-machine + 403 errors surface clearly; new endpoints follow the service's logging/health. |
| VI. Simplicity & YAGNI | ⚠️ One tracked item: the **bounded backend read endpoints** (FR-015). No new frontend framework (reuses 012). Justified below. |
| VII. Automated Quality Gates | ✅ Frontend CI (011 `frontend` job) + backend CI (`:hr-service:build` runs the new contract tests). |

**Result**: PASS with one tracked complexity item.

## Project Structure

### Documentation (this feature)

```text
specs/013-hr-ui/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/
│   ├── hr-manifest.md        # the concrete HR manifest (function → method/path/inputs/result)
│   └── hr-read-api.md        # the backend read endpoints added (FR-015) + response shapes
└── checklists/requirements.md
```

### Source Code (repository root)

```text
frontend/                                 # EVOLVE — reuses the 011 + 012 shared framework (NO new framework code)
├── src/services/
│   ├── registry.ts                       # point the `hr` entry at the manifest below
│   └── manifests/hr.ts                    # NEW: the full HR manifest (Employees/Attendance/Leave/Payroll/Outputs)
└── tests/HrManifest.test.tsx             # NEW: HR manifest renders + runs each area (mock the edge)

backend/hr-service/                        # BOUNDED read-only addition (FR-015) — no existing behavior changes
├── src/main/java/com/kita/hr/
│   ├── api/PayrollController.java         # ADD: GET /payroll/runs, GET /payroll/runs/{id}
│   ├── api/LeaveController.java           # ADD: GET /leave/requests?employeeId=&status=, GET /leave/requests/{id}
│   ├── payroll/PayrollRunService.java     # ADD: list()/get(id) (reuse PayrollRunResponse)
│   └── leave/LeaveService.java            # ADD: listRequests()/getRequest(id) (reuse LeaveRequestResponse)
└── src/test/java/com/kita/hr/api/         # ADD: MockMvc contract tests per new GET (red-first)
```

**Structure Decision**: Evolve `frontend/` reusing the 011 + **012** shared inputs (no new framework), plus a
**bounded read-only** addition to `backend/hr-service`. ⚠️ **This branch predates the 012 merge** — before
implementing, sync `main` into `013-hr-ui` so the 012 shared inputs (`ReferenceInput`/`ListInput`/`idLabels` +
the `reference`/`list` manifest types) are present.

## Complexity Tracking

| Violation (Principle VI) | Why needed | Simpler alternative rejected because |
|---|---|---|
| Backend read endpoints (FR-015) | Payroll runs + leave requests are write-only; without reads the UI can't list/re-open them and FR-005/006 are untestable. The user chose to add them (mirroring 012's Q1=C). | Deferring (action-response-only) leaves the UI unable to list runs/requests; the user explicitly chose to add the reads for cross-service consistency. |

## Phase 0 — Research (see research.md)

Grounded by reading hr-service's controllers/DTOs/repositories: the exact endpoint inventory; the **role-gating**
model (stub mode → `HR_ADMIN`); that a run's per-employee lines come from the existing **register** (so only
runs list/get are added); the **bounded read endpoints** to add (thin `findAll`/`findById` reusing existing
DTOs); and that HR reuses 012's shared inputs (employee reference pickers, DTR/leave list bodies, id→label).

## Phase 1 — Design & Contracts (see data-model.md, contracts/, quickstart.md)

- **data-model.md**: the surfaced HR entities (Employee, Compensation, WorkedTime, LeaveType/Balance/Request,
  PayrollRun, Register/Payslip, Remittance, DeductionRule) with the fields their DTOs return, plus the two
  now-listable resources.
- **contracts/**: the concrete HR **manifest** (every function → method + edge path + inputs + result) and the
  **hr-read-api** contract (the new GET endpoints + response shapes + read-only/role-gated/tenant rules).
- **quickstart.md**: `:hr-service:build` (new reads green) → bring up the 011 env → sign in → exercise each HR
  area, including listing payroll runs + leave requests → `npm test` + `npm run build`.

**Post-design constitution re-check**: PASS — the backend addition stays read-only, role-gated, and tenant-scoped
with contract tests; the frontend reuses 009 security + the 011/012 framework; the one complexity item is tracked.
