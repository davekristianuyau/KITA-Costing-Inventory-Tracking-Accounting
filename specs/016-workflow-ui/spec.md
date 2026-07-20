# Feature Specification: Workflow (Back-Office) Service UI

**Feature Branch**: `016-workflow-ui`
**Created**: 2026-07-20
**Status**: Draft
**Input**: User description: "Workflow full UI filling the 011 console workspace manifest for workflow-service — the back-office activity log (audit trail), authorization rules, pending maker-checker reviews, and performing/reviewing governed actions with the outcome taxonomy; read-first then the write actions; evolves the Workflow manifest + result views, no backend code."

## Overview

The full per-service UI for `workflow-service` (spec 007, back-office workflows) on the 011 console foundation.
`workflow-service` is a **thin orchestration** layer that runs governed back-office actions with **maker-checker**
controls, records every action in an **append-only activity log**, and authorizes actions from **role mappings**
(with roles resolved from HR). This feature grows the Workflow tab into the workspace for that: browse the
activity/audit trail, see the authorization rules and what is awaiting review, and perform or review governed
actions — each surfacing the service's outcome taxonomy (approved / rejected-invalid / not-permitted /
unavailable). Every function is an entry in the Workflow **manifest** rendered by the 011 workspace framework and
called through the 009 edge. No backend changes — `workflow-service` already provides these capabilities.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse the back-office activity log (Priority: P1) 🎯 MVP

An operator or auditor opens the Workflow tab and browses the append-only activity log: every back-office action
with its actor (employee), action, target, outcome, and time — filterable by outcome and action.

**Why this priority**: The audit trail is the service's core artifact, is read-only, and proves the per-service
pattern end-to-end with zero risk of triggering any governed action.

**Independent Test**: Sign in → Workflow → "Activity log" lists recorded activities; filtering by outcome or
action narrows the list — all read-only via the edge.

**Acceptance Scenarios**:

1. **Given** a signed-in client, **When** they open Workflow → Activity log, **Then** activities render (actor,
   action, target, outcome, timestamp) newest-first, with loading and empty states.
2. **Given** activities of different outcomes, **When** the user filters by an outcome (e.g. not-permitted),
   **Then** only matching activities render.
3. **Given** a specific action type, **When** the user filters by it, **Then** only that action's activities render.

---

### User Story 2 - See authorization rules and pending reviews (Priority: P2)

A user reviews who is allowed to do what — the authorization mapping (role → action → kind: perform / maker /
checker) — and what is currently awaiting a checker (pending maker-checker reviews).

**Why this priority**: Understanding the rules and the review queue precedes performing or reviewing any action;
read-only.

**Independent Test**: Open Workflow → Authorization rules → the role/action/kind mappings render; Pending reviews
→ items awaiting a checker render with their maker and stage.

**Acceptance Scenarios**:

1. **Given** configured mappings, **When** authorization rules are viewed, **Then** each role → action → kind
   (perform/maker/checker) entry renders.
2. **Given** items awaiting review, **When** pending reviews are viewed, **Then** each item's action, target,
   recorded maker, and stage render.

---

### User Story 3 - Perform a governed action (maker) (Priority: P3)

A user performs a governed back-office action as the **maker** (e.g. raise a sales order, record a receiving)
through the workflow, which authorizes it against their role and records the outcome in the activity log.

**Why this priority**: Making an action is a write with real effects; it follows the read views so the actor and
rules are understood first.

**Independent Test**: Perform a maker action → on success it is recorded as approved in the activity log; if the
actor's role is not permitted, a clear not-permitted result is shown and recorded.

**Acceptance Scenarios**:

1. **Given** a permitted maker action, **When** it is submitted with valid inputs, **Then** it succeeds and
   appears in the activity log as approved; **When** a required input is missing, **Then** the call is blocked
   with inline validation.
2. **Given** an actor whose role does not permit the action, **When** it is submitted, **Then** a clear
   "not permitted" result is shown and the attempt is recorded.

---

### User Story 4 - Review a pending item (checker) (Priority: P3)

A user acts as the **checker** on a pending item (e.g. confirm a sales order's payment, approve a receiving),
with the maker-checker guards enforced: a self-review is rejected as invalid, an unpermitted role is rejected as
not-permitted, and a downstream outage surfaces as unavailable.

**Why this priority**: The checker step is the highest-consequence, control-critical action; it comes last, after
the queue and rules are visible.

**Independent Test**: As a different, permitted employee, approve a pending item → it is recorded as approved and
leaves the pending queue; as the original maker, attempt to check the same item → a clear "rejected — self review
not allowed" result; as an unpermitted role → "not permitted"; on a downstream outage → "temporarily
unavailable".

**Acceptance Scenarios**:

1. **Given** a pending item and a permitted, different checker, **When** they approve it, **Then** the outcome is
   approved, it leaves the pending queue, and the activity log records it.
2. **Given** the recorded maker attempts to check their own item, **When** submitted, **Then** the result is
   "rejected — self review not allowed" (invalid), distinct from a not-permitted result.
3. **Given** an actor whose role lacks the checker permission, **When** submitted, **Then** the result is
   "not permitted".
4. **Given** a required downstream service is unavailable, **When** the action is submitted, **Then** the result
   is "temporarily unavailable" and the attempt is recorded.

### Edge Cases

- The activity log is empty (fresh client) → a clear empty state.
- A filter matches nothing → a clear empty state, not an error.
- The four outcomes — approved, rejected-invalid (incl. self-review), not-permitted, unavailable — MUST each be
  rendered distinctly so the user knows whether to retry, escalate, or wait.
- Missing required inputs → inline validation blocks the call before the edge.
- A read/write returns 4xx/5xx via the edge → mapped to the corresponding outcome and shown clearly.
- The acting employee identity is required for governed actions; if it cannot be determined, the action is
  blocked with a clear message rather than sent anonymously.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Workflow tab MUST present its functions grouped by area (Activity log, Authorization, Reviews,
  Actions) in the 011 left pane.
- **FR-002**: Users MUST be able to browse the append-only activity log, newest-first, showing actor, action,
  target, outcome, and timestamp.
- **FR-003**: Users MUST be able to filter the activity log by outcome and by action type.
- **FR-004**: Users MUST be able to view the authorization mappings (role → action → kind: perform/maker/checker).
- **FR-005**: Users MUST be able to view items awaiting review (pending maker-checker), with their action,
  target, recorded maker, and stage.
- **FR-006**: Users MUST be able to perform a governed maker action via a validated form; the result and its
  activity-log entry MUST reflect the outcome.
- **FR-007**: Users MUST be able to review a pending item as a checker via a validated action.
- **FR-008**: The UI MUST render the four outcomes distinctly — approved, rejected-invalid (including self-review),
  not-permitted, and unavailable — with guidance implied by each (retry / escalate / wait).
- **FR-009**: Every function MUST show explicit loading, empty, result, and error states (reusing the 011
  framework), with results in the shape best suited to the data.
- **FR-010**: All calls MUST go through the 009 edge with the signed-in client's session; the UI MUST NOT bypass
  the edge or embed credentials.
- **FR-011**: Write actions MUST block on missing required inputs (including the acting employee where required)
  with inline validation before calling the edge.
- **FR-012**: The feature MUST NOT modify `workflow-service` or the services it orchestrates; authorization,
  maker-checker guards, and recording are performed by the backend — the UI only invokes and displays.

### Key Entities *(include if feature involves data)*

- **Activity Entry**: an append-only record of one back-office action — actor (employee), action, target,
  outcome, retry info, timestamp.
- **Outcome**: the result taxonomy — approved, rejected-invalid (incl. self-review), not-permitted, unavailable.
- **Authorization Rule**: a mapping of role → action → kind (perform / maker / checker).
- **Pending Review**: an item awaiting a checker — action, target, recorded maker, stage.
- **Actor**: the acting employee (identity + role, resolved from HR by the backend).
- **Governed Action**: a back-office operation (e.g. raise sales order, confirm payment, record/approve receiving)
  run through the workflow with maker-checker controls.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From the Workflow tab, a user can open the activity log and filter to a specific outcome in **under
  30 seconds**, with no page reloads between functions.
- **SC-002**: **100%** of Workflow functions show a distinct loading, empty, result, and error state, and the four
  outcomes are visually distinguishable.
- **SC-003**: A checker can approve a pending item and see it leave the queue and appear in the activity log
  entirely within the console.
- **SC-004**: A self-review attempt is shown as **rejected-invalid** and is clearly distinct from a
  **not-permitted** result (no conflation of the two controls).
- **SC-005**: **0** service calls bypass the edge or expose credentials in the browser (session-cookie only).
- **SC-006**: The full Workflow workspace is usable at the 011 responsive floor (down to **768px**) and fully
  keyboard-navigable.

## Assumptions

- `workflow-service` (spec 007) exposes the activity log, authorization mappings, pending reviews, and governed
  actions via the edge under `/api/workflow`; this feature maps them into manifest functions and adds no
  endpoints.
- The acting employee identity required by governed actions is carried to the backend via the edge/session (the
  service reads it as the caller); where an action needs an explicit actor/target, the run-form supplies it.
- Authorization, maker-checker guards (self-review, distinct-role), retries, and recording are enforced by the
  backend; the UI invokes actions and renders the returned outcome — it does not re-implement any control.
- Result rendering reuses the 011 shapes (table/json/detail/message); a Workflow-specific activity/outcome view
  may be added where a generic shape is insufficient.
- In the local simulation every client can reach all services (011 assumption A1).

## Dependencies

- **011 service-console-ui** — the console shell, workspace framework, manifest seam, and edge fetch.
- **009 client-login-deploy-sim** — the edge/session the calls route through.
- **007 back-office-workflows** — the `workflow-service` backend being surfaced.

## Out of Scope

- Any backend/API change to `workflow-service` or the services it orchestrates.
- A visual workflow/process designer or BPMN editing — the service is thin orchestration, not a BPM engine.
- Other services' UIs — their own specs.
- Real cloud deployment; analytics dashboards; role/entitlement administration (mappings are viewed, not edited,
  here).
