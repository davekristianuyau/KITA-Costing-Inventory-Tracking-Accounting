# Implementation Plan: Workflow (Back-Office) Service UI

**Branch**: `016-workflow-ui` | **Date**: 2026-07-22 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/016-workflow-ui/spec.md`

## Summary

Fill the 011 console's Workflow tab with the **full `workflow-service` manifest** — the append-only activity log,
the authorization mappings, the pending maker-checker queue, and the governed maker/checker actions (sales order,
purchase order, receiving, build, party maintenance) — each a manifest function rendered by the 011
`FunctionWorkspace` through the 009 edge, reusing the 012–015 shared framework.

Phase 0 read `workflow-service`'s controllers, the edge/identity wiring, and the frontend framework. Unlike 014/015
this is **not** purely frontend:

1. **Two reads the spec requires do not exist** — there is no endpoint for the authorization mappings (FR-004) or
   the pending review queue (FR-005), and `GET /api/workflow/activity` filters by `actor`/`action`/`from`/`to` but
   **not by outcome** (FR-003). 016 adds three **read-only** projections of state the service already holds
   (precedent: 012's FR-015 reads). No control, guard, or workflow logic changes — FR-012 holds in substance.
2. **Two small framework extensions**: an optional `group` on a manifest function so the left pane renders the four
   areas FR-001 asks for, and an **outcome-aware result view** so the four-outcome taxonomy (FR-008) is visible —
   today the workflow error envelope `{outcome, reason, status}` degrades to "Request failed (403)".
3. **The acting employee is the signed-in console user**, not a UI field. The edge strips inbound `X-Kita-*` and
   sets `X-Kita-User` from the session subject; `workflow-service` resolves roles from HR by that id. So the sim
   needs demo logins whose subjects are the seeded workflow employees (`emp-sales`, `emp-whse-mgr`, …) — switching
   actor means switching login, and maker ≠ checker is demonstrated honestly rather than spoofed.

## Technical Context

**Language/Version**: TypeScript 5.5 / React 18 / Vite 5 (evolve `frontend/`); Java 17 / Spring Boot 3.5 for the
three read-only endpoints in `backend/workflow-service/`.
**Primary Dependencies**: the 011 design system + workspace framework, the shared inputs (`ReferenceInput`,
`ListInput`, `FieldInput`, `result/idLabels`, `bodyInput`/dotted-name bodies, the 014 detail sub-table), the
generic edge fetch (`src/api/edge.ts`); on the backend, the existing `ActivityRecordRepository`,
`AuthorizationMappingRepository`, and `PendingReviewStore`.
**Storage**: none client-side (session is the 009 httpOnly cookie); the new reads project existing rows
(`back_office_activity`, `authorization_mapping`) and the in-memory pending store — no schema change, no migration.
**Testing**: **Vitest + Testing Library** for the Workflow manifest render/run, the group-aware sidebar, and the
outcome view; **JUnit** unit/contract tests for the three read endpoints (red-first).
**Target Platform**: modern browsers, responsive to 768px; workflow-service on port 8088 behind the client gateway.
**Performance Goals**: workspace interactions feel instant; pickers load once per function open.
**Constraints**: every UI call via the 009 edge with the client session; **no change to any control** —
authorization, self-review/distinct-role guards, retries, and recording stay exactly as `workflow-service`
implements them; the UI never supplies or overrides the actor; monetary/decimal values displayed exactly as
returned (money is a decimal **string** on the wire); WCAG-AA + keyboard (011).
**Scale/Scope**: ~19 manifest functions in 4 groups (Activity log, Authorization, Reviews, Actions); 3 read-only
endpoints; 2 small framework extensions; sim env/seed changes (no new service).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Spec-Driven Development | ✅ spec present; this plan precedes code; P1–P3 stories independently testable. One spec delta recorded below (FR-012 vs. the three read endpoints). |
| II. Test-Driven Development | ✅ Red-first Vitest for the manifest/sidebar/outcome view; red-first JUnit for the three reads. No back-office logic added client-side — the UI invokes and displays. |
| III. Security & Data Integrity | ✅ Reuses 009's httpOnly session + edge tenancy. **The UI cannot assert an actor** — the edge strips inbound `X-Kita-*`; the acting employee is the session subject. The pending-review projection deliberately omits the captured `payload`. |
| IV. Environment Isolation | ✅ Frontend + local sim only; adapter/seed changes are compose/env, per-client stacks unchanged; 0 real cloud. |
| V. Observability & Debuggability | ✅ The whole point of the tab: the activity log is browsable, and every action's outcome (approved / rejected-invalid / not-permitted / unavailable) is rendered distinctly with its `reason`. |
| VI. Simplicity & YAGNI | ✅ Two framework additions, both small and general (`group`, outcome view). Reads are thin projections of existing repositories. No BPM/designer, no new store, no new service. |
| VII. Automated Quality Gates | ✅ Frontend build/test in the 011 `frontend` CI job; `:workflow-service:build` already gated in `ci.yml`. |

**Result**: PASS — one recorded spec delta, no unjustified complexity.

### Spec delta (recorded, not a violation)

FR-012 reads "MUST NOT modify `workflow-service`". Its stated intent — "authorization, maker-checker guards, and
recording are performed by the backend — the UI only invokes and displays" — is preserved exactly. But FR-004 and
FR-005 cannot be satisfied without a read endpoint, and FR-003's outcome filter has no server support. This plan
therefore adds **three read-only endpoints/params that change no behaviour**, matching the precedent set by 012
(FR-015 reads). Tasks must not touch any workflow, pipeline, authorizer, or recorder code path.

## Project Structure

### Documentation (this feature)

```text
specs/016-workflow-ui/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/
│   ├── workflow-manifest.md       # the concrete Workflow manifest (function → group/method/path/inputs/result)
│   └── workflow-read-endpoints.md # the 3 read-only additions (request/response shapes)
└── checklists/requirements.md
```

### Source Code (repository root)

```text
backend/workflow-service/src/main/java/com/kita/workflow/
├── api/ActivityController.java             # EDIT — add the `outcome` filter param (FR-003)
├── api/AuthorizationController.java        # NEW  — GET /api/workflow/authorization (FR-004)
├── api/PendingReviewController.java        # NEW  — GET /api/workflow/pending-reviews (FR-005)
└── pending/PendingReviewStore.java         # EDIT — add list(); + InMemoryPendingReviewStore impl

frontend/
├── src/services/types.ts                   # EDIT — optional `group` on ServiceFunction; "outcome" ResultKind
├── src/services/registry.ts                # EDIT — point the `workflow` entry at the manifest below
├── src/services/manifests/workflow.ts      # NEW  — the full Workflow manifest (4 groups)
├── src/app/Sidebar.tsx                     # EDIT — render group headings when functions declare a group
├── src/api/edge.ts                         # EDIT — surface the {outcome, reason} envelope on failures
├── src/workspace/result/OutcomeView.tsx    # NEW  — the four-outcome banner (approved/invalid/denied/unavailable)
└── tests/WorkflowManifest.test.tsx         # NEW  — manifest renders + runs each group; outcome states

docker-compose.yml                           # EDIT — workflow's CRM/OPERATIONS/PROCUREMENT adapters → http
backend/identity-service/.../DemoSeeder.java # EDIT — seed the emp-* demo logins per client (sim actors)
```

**Structure Decision**: Evolve `frontend/` on the 011 + 012–015 framework; add three read-only endpoints to
`workflow-service`; make the sim's actor identity real via env/seed changes. No new module, no schema migration.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| 3 read-only endpoints in `workflow-service` | FR-004/FR-005 have no endpoint; FR-003 has no outcome filter | Client-side filtering can't invent data the API never returns; the framework has no client-side filter, and adding one would duplicate query state per manifest. |
| `group` on `ServiceFunction` | FR-001 requires four grouped areas; 19 functions in a flat list is unusable | Ordering + naming (the 012–015 approach) doesn't satisfy "grouped" and degrades at this size. Optional field ⇒ zero change to existing manifests. |
| `OutcomeView` + envelope parsing | FR-008/SC-004 require the four outcomes to be distinguishable; today a 403 renders as "Request failed (403)" | Reusing the generic error banner conflates not-permitted with rejected-invalid — exactly what SC-004 forbids. |
| Sim adapter/seed changes | Actions must have real effects and a real actor, or US3/US4 are untestable | A UI actor field is impossible (the edge strips `X-Kita-*`) and would be spoofing; fake downstream adapters make every write fail on unknown ids. |

## Phase 0 — Research (see research.md)

Grounded by reading `workflow-service`'s controllers/DTOs (the full action inventory + the single existing read),
`edge-gateway`'s `SessionAuthFilter` (strips inbound `X-Kita-*`, sets `X-Kita-User` from the session subject),
`CallerContext`/`ActorResolver`/`InMemoryHrAdapter` (identity → HR → roles; `emp-*` seeds), the `sim/` per-client
overlay (each client is a separate stack, so the two demo users can't be maker/checker for each other), and the
011 framework (`FunctionWorkspace`, `Sidebar`, `edge.ts`, the 014 detail sub-table). Decisions D1–D7 in research.md.

## Phase 1 — Design & Contracts (see data-model.md, contracts/, quickstart.md)

- **data-model.md**: the surfaced entities — Activity Entry, Outcome taxonomy, Authorization Rule, Pending Review,
  Actor, Governed Action — with the exact fields each projection returns.
- **contracts/workflow-read-endpoints.md**: the three read-only additions, request params and response shapes,
  including the deliberate omission of `payload` from the pending-review projection.
- **contracts/workflow-manifest.md**: the concrete Workflow manifest — every function's group, method, edge path,
  inputs (with `reference` pickers onto operations items / crm customers / procurement suppliers), and result kind.
- **quickstart.md**: bring up the sim → sign in as `emp-sales` → browse the activity log and filter by outcome →
  view rules + pending queue → take a sales order (maker) → attempt self-confirm (rejected-invalid) → sign in as
  `emp-cashier` and confirm (approved) → `npm test` + `npm run build` + `:workflow-service:build`.

**Post-design constitution re-check**: PASS — the added endpoints are read-only projections, the framework
additions are optional and general, and no control or actor decision moves into the browser.
