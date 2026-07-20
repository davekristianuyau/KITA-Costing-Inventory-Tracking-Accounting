# Feature Specification: HR & Payroll Service UI

**Feature Branch**: `013-hr-ui`
**Created**: 2026-07-20
**Status**: Draft
**Input**: User description: "HR and payroll full UI filling the 011 console workspace manifest for hr-service — employees + effective-dated compensation, time & attendance, leave, payroll runs (state machine), deductions incl. PH statutory, and payslip/register/remittance outputs; read-first then the write actions; evolves the HR manifest + result views, no backend code."

## Overview

The full per-service UI for `hr-service` (spec 004) on the 011 console foundation. It grows the HR tab into the
complete HR & Payroll workspace: employee records and effective-dated compensation, time & attendance, leave,
the payroll run lifecycle (with statutory deductions), and the payslip/register/remittance outputs. Every
function is an entry in the HR **manifest** rendered by the 011 workspace framework and called through the 009
edge. It is **mostly frontend**, with a **bounded backend addition**: `hr-service` (spec 004) exposes most
capabilities already, but **payroll runs** and **leave requests** are currently **write-only** (create/act
returns the object, but there is no way to list or re-fetch them). This feature **adds the missing read (GET)
endpoints** for those two resources so the UI can list/view them — mirroring the decision made for 012. No other
backend behavior changes.

## Clarifications

### Session 2026-07-20

- Q: `hr-service` has no GET to list payroll runs or leave requests, yet FR-005/FR-006 require viewing them —
  under the "no backend changes" constraint, how should 013 handle it? → A: **Add the missing read endpoints now**
  (mirror 012's Q1=C), to keep the per-service UIs consistent and avoid the gap. 013's scope therefore includes a
  bounded set of backend GET endpoints (see FR-015).
- Applied from Phase-0 backend grounding (see [research.md](./research.md), not open questions): a payroll run's
  **per-employee lines already come from the existing** `GET /api/hr/payroll/runs/{id}/register` (gross /
  deductions / net + totals) — so 013 only adds **list + get** for runs (period + state), not a new lines
  endpoint. `hr-service` endpoints are **role-gated** (`HR_ADMIN` / `PAYROLL_OFFICER` / `MANAGER` /
  `EMPLOYEE_SELF`), but in the service's **stub security mode** (the sim default) a caller with no role header is
  treated as `HR_ADMIN`, so the console's demo session can exercise HR; if run with real roles and none granted,
  calls return a clear 403 (FR-012 error handling).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse employees and compensation (Priority: P1) 🎯 MVP

An HR user opens the HR tab and browses the workforce: a list of employees, an employee's detail, and that
employee's effective-dated compensation history (which rate applied when).

**Why this priority**: Employee visibility is the foundation of every other HR task, is read-only, and proves
the per-service pattern end-to-end with no risk of mutating payroll data.

**Independent Test**: Sign in → HR → "Employees" lists employees; selecting one shows detail; "Compensation"
shows the effective-dated rate history for that employee — all read-only via the edge.

**Acceptance Scenarios**:

1. **Given** a signed-in client, **When** they open HR → Employees, **Then** employees render (name, id, status)
   with loading and empty states.
2. **Given** an employee, **When** their detail is opened, **Then** their attributes are shown.
3. **Given** an employee with rate changes, **When** compensation is viewed, **Then** each rate with its
   effective date is shown in date order.

---

### User Story 2 - Time & attendance and leave (Priority: P2)

An HR user reviews an employee's worked time and premiums (overtime, holiday, night differential) derived from
daily time records, and their leave balances and requests.

**Why this priority**: Time and leave feed gross pay; seeing them precedes trusting a payroll run. Read-only.

**Independent Test**: Open HR → Attendance for an employee → worked hours + premium breakdown render; Leave
shows balances and requests.

**Acceptance Scenarios**:

1. **Given** an employee with time records in a period, **When** attendance is viewed, **Then** worked time and
   OT/holiday/night-diff premiums render for that period.
2. **Given** an employee, **When** leave is viewed, **Then** leave balances by type and recent requests render.

---

### User Story 3 - Review a payroll run and its register (Priority: P2)

An HR user views payroll runs and drills into one: its state (draft/computed/finalized), the per-employee
computed lines (gross, deductions, net), and the register that reconciles the run.

**Why this priority**: Reviewing a run is read-only but central; it must be trustworthy before anyone finalizes.

**Independent Test**: Open HR → Payroll runs → a run's detail shows its state and computed lines; the register
view reconciles totals.

**Acceptance Scenarios**:

1. **Given** existing runs, **When** payroll runs are listed, **Then** each run's period and state render.
2. **Given** a computed run, **When** its detail is viewed, **Then** per-employee gross, deductions, and net
   render, and the register totals reconcile.

---

### User Story 4 - Run payroll and record time/leave (Priority: P3)

An HR user performs the write actions: create a payroll run for a period, compute it, finalize it (idempotent),
record a daily time record, and file/approve a leave request — each a validated form with a clear result.

**Why this priority**: These mutate pay and must follow the read views so the user can verify inputs and effects;
finalize especially is high-consequence, so it comes last.

**Independent Test**: Create a run → it appears as draft; compute → lines populate; finalize → state becomes
finalized and re-finalizing is a no-op; record a DTR → attendance reflects it; file leave → it appears as
requested; approve → its status changes.

**Acceptance Scenarios**:

1. **Given** the create-run form, **When** a valid period is submitted, **Then** a draft run is created;
   **When** a required field is missing, **Then** the call is blocked with inline validation.
2. **Given** a draft run, **When** compute is run, **Then** the run becomes computed with per-employee lines.
3. **Given** a computed run, **When** finalize is run twice, **Then** the run finalizes once and the second call
   is a safe no-op (no double posting).
4. **Given** the DTR form, **When** submitted, **Then** the employee's attendance/worked time reflects it.
5. **Given** the leave form, **When** a request is filed and then approved, **Then** its status transitions.

---

### User Story 5 - Payslips, register, and statutory remittance (Priority: P3)

An HR user produces the outputs of a finalized run: an employee's payslip, the payroll register, and the
statutory remittance summaries (e.g. PH SSS/PhilHealth/Pag-IBIG/BIR) for the period.

**Why this priority**: Outputs depend on a finalized run existing; they are the analytical/compliance payoff and
are read-only.

**Independent Test**: For a finalized run, open a payslip for an employee (earnings, deductions, net); open the
register (all employees); open the remittance summary (totals per statutory contribution).

**Acceptance Scenarios**:

1. **Given** a finalized run, **When** an employee's payslip is viewed, **Then** earnings, deductions, and net
   render for that employee and period.
2. **Given** a finalized run, **When** the register is viewed, **Then** all employees' lines and totals render.
3. **Given** a finalized run, **When** the remittance summary is viewed, **Then** per-contribution totals render.

### Edge Cases

- A function returns an empty list (e.g. no runs yet) → a clear empty state.
- Compute/finalize on a run in the wrong state → a clear message explaining the required state, not a crash.
- Finalize called twice → the second call is a safe no-op (idempotent), surfaced clearly.
- Missing required inputs → inline validation blocks the call before the edge.
- A read/write returns 4xx/5xx via the edge → a clear, non-revealing error with the failing action's context.
- Personally identifiable information is shown only within its function's result; nothing sensitive is persisted
  in the browser beyond the session cookie.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The HR tab MUST present its functions grouped by area (Employees, Attendance, Leave, Payroll,
  Outputs) in the 011 left pane.
- **FR-002**: Users MUST be able to list employees and view an employee's detail.
- **FR-003**: Users MUST be able to view an employee's effective-dated compensation history.
- **FR-004**: Users MUST be able to view an employee's worked time and premiums for a period.
- **FR-005**: Users MUST be able to view an employee's leave balances and requests.
- **FR-006**: Users MUST be able to list payroll runs and view a run's state; the run's computed per-employee
  lines are shown via its register (FR-007).
- **FR-007**: Users MUST be able to view a run's register (per-employee gross/deductions/net + reconciling totals).
- **FR-008**: Users MUST be able to create, compute, and finalize a payroll run via validated forms, with
  finalize being idempotent (a repeat call is a safe no-op).
- **FR-009**: Users MUST be able to record a daily time record and file/approve a leave request via validated
  forms.
- **FR-010**: Users MUST be able to view an employee's payslip, the payroll register, and the statutory
  remittance summary for a finalized run.
- **FR-011**: Every function MUST show explicit loading, empty, result, and error states (reusing the 011
  framework), with results in the shape best suited to the data.
- **FR-012**: All calls MUST go through the 009 edge with the signed-in client's session; the UI MUST NOT bypass
  the edge or embed credentials, and MUST NOT persist PII in the browser beyond the session cookie.
- **FR-013**: Write actions MUST block on missing required inputs with inline validation before calling the edge,
  and MUST surface state-machine errors (wrong-state compute/finalize) clearly.
- **FR-014**: The feature MUST NOT change any existing `hr-service` endpoint or its write/business behavior; the
  only backend change permitted is adding the **read-only** endpoints in FR-015.
- **FR-015**: `hr-service` MUST gain read (GET) endpoints so today's write-only resources become
  listable/viewable: **list + get payroll runs** (period + state) and **list + get leave requests**. These
  endpoints MUST be read-only, role-gated consistently with sibling endpoints, tenant-scoped like existing
  endpoints, and covered by tests (unit/contract) per the constitution's TDD requirement.
- **FR-016**: Users MUST be able to list and view payroll runs and leave requests in the UI (enabled by FR-015),
  and the effect of each create/lifecycle action MUST be verifiable via these reads (a run also via its register;
  a leave decision via the request's updated status).

### Key Entities *(include if feature involves data)*

- **Employee**: a worker record — name, id, status, and attributes.
- **Compensation**: an effective-dated pay rate for an employee (rate + effective date).
- **Time Record (DTR)**: raw worked-time for a day feeding worked hours and premiums.
- **Attendance/Premiums**: derived worked time with OT/holiday/night-diff premiums for a period.
- **Leave**: leave balances by type and leave requests with a status lifecycle.
- **Payroll Run**: a period run with a state (draft/computed/finalized) and per-employee lines.
- **Payroll Line**: an employee's gross, deductions, and net within a run.
- **Deduction**: a statutory or voluntary reduction (rule-driven; PH statutory + loans).
- **Register**: the reconciliation of a run's totals.
- **Payslip / Remittance**: per-employee earnings/deductions/net; per-contribution statutory totals.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From the HR tab, a user can find an employee and see their current compensation in **under 30
  seconds**, with no page reloads between functions.
- **SC-002**: **100%** of HR functions show a distinct loading, empty, result, and error state.
- **SC-003**: A user can take a payroll run from create → compute → finalize entirely within the console, and a
  repeated finalize does **not** double-post.
- **SC-004**: For a finalized run, a payslip, the register, and the remittance summary are each reachable in
  **≤ 3 clicks** from the HR tab.
- **SC-005**: **0** service calls bypass the edge or expose credentials/PII in the browser (session-cookie only).
- **SC-006**: The full HR workspace is usable at the 011 responsive floor (down to **768px**) and fully
  keyboard-navigable.

## Assumptions

- `hr-service` (spec 004) exposes most read and all write capabilities above via the edge under `/api/hr`; this
  feature maps them into manifest functions and **adds only the read endpoints in FR-015** (payroll runs +
  leave requests) for the currently write-only resources.
- HR endpoints are role-gated; the sim runs `hr-service` in **stub security mode**, where a caller with no role
  header is treated as `HR_ADMIN`, so the console demo session can exercise HR (real-role deployments show a
  clear 403 when a role is missing).
- Statutory remittance content (PH SSS/PhilHealth/Pag-IBIG/BIR) comes from the existing deduction engine/seed in
  `hr-service`; the UI displays it and does not re-implement the rules.
- Result rendering reuses the 011 shapes (table/json/detail/message); HR-specific views (e.g. a payslip layout)
  may be added where a generic shape is insufficient.
- In the local simulation every client can reach all services (011 assumption A1).

## Dependencies

- **011 service-console-ui** — the console shell, workspace framework, manifest seam, and edge fetch.
- **009 client-login-deploy-sim** — the edge/session the calls route through.
- **004 hr-payroll** — the `hr-service` backend being surfaced.

## Out of Scope

- Backend changes to `hr-service` **beyond** the read-only endpoints in FR-015 (no change to existing endpoints
  or write/business logic; no new write endpoints).
- Other services' UIs — their own specs.
- Real cloud deployment; payroll bank-file/e-filing export; analytics dashboards; role/entitlement management.
