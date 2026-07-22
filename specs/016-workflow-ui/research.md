# Phase 0 Research — Workflow (Back-Office) Service UI

Grounded by reading the code, not assumed. Sources: `backend/workflow-service/src/main/java/com/kita/workflow/`
(`api/`, `authorization/`, `pending/`, `actor/`, `common/security/`, `ports/fake/`),
`backend/edge-gateway/.../SessionAuthFilter.java`, `backend/gateway/.../RoutesConfig.java`,
`backend/identity-service/.../DemoSeeder.java`, `sim/client-overlay.yml`, `docker-compose.yml`, and
`frontend/src/{services,workspace,app,api}`.

## D1 — Endpoint inventory: every governed action exists; only one read does

**Decision**: Map all 12 governed actions straight to manifest functions; add **three read-only** additions for the
reads the spec needs.

Existing (no change):

| Action | Endpoint | Kind |
|---|---|---|
| Take sales order | `POST /api/workflow/sales-orders` | MAKER |
| Confirm sales payment | `POST /api/workflow/sales-orders/{id}/confirm-payment` | CHECKER |
| Release sales order | `POST /api/workflow/sales-orders/{id}/release` | CHECKER |
| Complete sales order | `POST /api/workflow/sales-orders/{id}/complete` | PERFORM |
| Cancel sales order | `POST /api/workflow/sales-orders/{id}/cancel` | MAKER |
| Raise purchase order | `POST /api/workflow/purchase-orders` | PERFORM |
| Approve purchase order | `POST /api/workflow/purchase-orders/{id}/approve` | PERFORM |
| Send purchase order | `POST /api/workflow/purchase-orders/{id}/send` | PERFORM |
| Record delivery receipt | `POST /api/workflow/purchase-orders/{id}/receipts` | MAKER |
| Confirm delivery receipt | `POST /api/workflow/receipts/{pendingReceiptId}/confirm` | CHECKER |
| Build product | `POST /api/workflow/builds` | PERFORM |
| Maintain customer / supplier | `POST|PATCH /api/workflow/customers[/{id}]`, `POST|PATCH /api/workflow/suppliers[/{id}]`, `PUT /api/workflow/suppliers/{id}/items` | PERFORM |
| Activity log | `GET /api/workflow/activity?actor&action&from&to` | read |

Missing for FR-003/FR-004/FR-005: an **outcome** filter on the activity read, an **authorization mapping** read,
and a **pending review** read. `AuthorizationMappingRepository.findAll()` and `PendingReviewStore` already hold the
data; `PendingReviewStore` has `put/get/remove` but no `list()`.

**Rationale**: A UI cannot filter or display data an API never returns. These are projections, not behaviour.
**Alternatives considered**: client-side filtering of the full activity list (can't produce rules or the queue at
all, and re-implements query state per function); rendering nothing for FR-004/FR-005 (fails US2 outright).

## D2 — The acting employee is the session subject; the UI must not offer an actor field

**Decision**: No actor input anywhere in the Workflow manifest. The acting employee = the signed-in console user.

`SessionAuthFilter` **strips every inbound `X-Kita-*` header** (anti-spoofing) and sets `X-Kita-User` from the
validated session subject. `CallerContext.actor()` reads exactly that header (falling back to `stub-admin` only when
absent, i.e. never behind the edge). `ActorResolver` then resolves the id against HR — unknown/inactive ⇒ 422 — and
takes **roles from the HR record, never the header**.

**Rationale**: Any UI-supplied actor would be either ignored (stripped) or a privilege-escalation hole. The
security model is correct as built; the UI conforms to it.
**Alternatives considered**: an "acting as" input mapped to a header (impossible + unsafe); a query param actor
(would bypass the whole identity model).

## D3 — Sim actors: demo logins named after the seeded workflow employees

**Decision**: Seed the identity demo users, per client, with subjects that match `InMemoryHrAdapter`'s employees —
`emp-sales`, `emp-cashier`, `emp-sales-mgr`, `emp-whse`, `emp-whse-mgr`, `emp-proc`, `emp-approver`, `emp-prod`,
`emp-crm` (keep `alice`/`bob`). Switching the acting employee = signing in as that employee.

Today `DemoSeeder` seeds one user per client (`alice`@client-a, `bob`@client-b) and `sim/client-overlay.yml` runs
**a separate full stack per client** — so `alice` and `bob` can never be maker and checker for each other (different
`workflow-service` instances, different DBs). Without same-client demo employees, US3/US4 are untestable and every
governed action fails 422 (`employee not active: alice`).

**Rationale**: smallest change that makes identity real; touches only the sim's seed data, not `workflow-service`.
**Alternatives considered**: seeding `alice`/`bob` into the fake HR (gives one all-powerful actor per stack ⇒
maker == checker always ⇒ every checker action is a self-review 422); `HR_ADAPTER=http` against the real
`hr-service` (its employee ids are UUIDs that no login subject matches).

## D4 — Downstream adapters must be `http` in the sim (except HR)

**Decision**: In `docker-compose.yml`, set `OPERATIONS_ADAPTER=http`, `CRM_ADAPTER=http`, `PROCUREMENT_ADAPTER=http`
with the existing base-URL env vars; leave `HR_ADAPTER=fake` (D3 depends on the `emp-*` seed).

The service defaults every port to `fake`, and compose passes only `DATABASE_*`. With fakes, a workflow write
against a real item/customer/supplier id picked from the Operations/CRM/Procurement lists fails validation, and
nothing shows up in the other tabs. Env-only change; the http adapters already exist, retry via `RemoteCall`, and
forward `X-Kita-User`.

**Rationale**: makes governed actions actually govern real records — the honest demo, and the only way the
cross-tab effects in the quickstart are true.
**Alternatives considered**: keep fakes and seed them (an invented second source of truth); leave it broken and
document it (fails US3's independent test).

## D5 — Framework extension 1: optional `group` on a manifest function

**Decision**: Add `group?: string` to `ServiceFunction`; `Sidebar` renders a heading per contiguous group and no
heading when a manifest declares none (012–015 unaffected).

**Rationale**: FR-001 names four areas, and this tab has ~19 functions — the largest so far. The field is optional
and general, so it costs nothing elsewhere.
**Alternatives considered**: naming conventions/ordering only (doesn't satisfy "grouped", and 19 flat links is the
worst navigation in the console); a nested manifest shape (breaking change to four existing manifests).

## D6 — Framework extension 2: outcome-aware results

**Decision**: Add a `"outcome"` `ResultKind` rendered by a new `OutcomeView`, and teach `callEdge` to read the
`{outcome, reason, status}` envelope on failures.

`GlobalExceptionHandler` returns `ErrorResponse{outcome, reason, status}` with 403 `REJECTED_NOT_PERMITTED`,
422 `REJECTED_INVALID`, 503 `FAILED_UNAVAILABLE`. `callEdge` only looks for a `message` field, so all three collapse
into "Request failed (4xx)" — conflating exactly the two controls SC-004 requires be distinct. Mapping:

| Outcome | Source | Rendering | Implied guidance |
|---|---|---|---|
| Approved | 2xx | success banner + the response detail | — |
| Rejected — invalid (incl. self-review) | 422 / `REJECTED_INVALID` | warning banner + `reason` | fix the input / have someone else check |
| Not permitted | 403 / `REJECTED_NOT_PERMITTED` | denial banner + `reason` | escalate — your role lacks the grant |
| Temporarily unavailable | 503 / `FAILED_UNAVAILABLE` | retry banner + `reason` | wait and retry |

Self-review is reported as **422 rejected-invalid**, and (per the 007 CI gap fix) is checked **before**
authorization, so a maker self-checking sees invalid, not not-permitted. The UI just renders what it is told.

**Rationale**: the outcome taxonomy is this service's whole contract with its users; it must be visible.
**Alternatives considered**: per-manifest error copy (duplicated 12×, drifts from the backend taxonomy); leaving
the generic banner (fails FR-008 and SC-004).

## D7 — Everything else reuses the 012–015 framework

- **Query-param filters** on GET paths already work: `buildPath` fills `{param}` tokens and strips blank query
  params (the 013 `from`/`to` precedent) — the activity filters need no new code.
- **Line inputs** (sales/PO/receipt `lines[]`) reuse `ListInput`; **reference pickers** reuse `ReferenceInput`
  against `/api/operations/items`, `/api/crm/customers`, `/api/procurement/suppliers`.
- **Detail results** with nested arrays reuse the 014 sub-table.
- **Money is a decimal string on the wire** (`@JsonFormat(shape = STRING)` on `RaiseResponse.total`) — display as
  returned, never parse to a float.
- **Pending reviews are in-memory and per-instance** (`InMemoryPendingReviewStore`): the queue empties on restart
  by design (losing one means the maker re-records; no domain effect). Say so in the UI description.
- The **captured `payload`** on a `PendingReview` must never be serialised to the browser — the projection returns
  identity/position fields only.
