# Quickstart — Workflow (Back-Office) Service UI

Validates the full Workflow workspace on top of the 011 console. Run from the repo root on branch
`016-workflow-ui`. **0 real cloud.** Backend touch is limited to three read-only endpoints
([contracts/workflow-read-endpoints.md](./contracts/workflow-read-endpoints.md)) plus sim env/seed changes.

> `main` (012–015) has already been merged into this branch, so the shared framework — `ReferenceInput`,
> `ListInput`, `FieldInput`, `result/idLabels`, `bodyInput`/dotted-name bodies, the 014 detail sub-table — is
> present. 016 adds only `group` and the outcome view on top of it.

## 1. Bring up the environment

```bash
bash sim/console/console-up.sh client-a       # floci-aws + 009 edge/identity + the client stack + console
open http://localhost:8080/login              # company=client-a
```

Sign in as an **employee**, not `alice` — the acting employee for every governed action is the session subject
(the edge strips inbound `X-Kita-*`; roles come from HR). Demo logins seeded per client:

| Login | Role | Can |
|---|---|---|
| `emp-sales` | SALES | take/cancel a sales order (maker) |
| `emp-cashier` | CASHIER | confirm sales payment (checker) |
| `emp-whse` | WAREHOUSE_STAFF | record a delivery receipt (maker) |
| `emp-whse-mgr` | WAREHOUSE_MANAGER | confirm a delivery receipt (checker) |
| `emp-proc` / `emp-approver` | PROCUREMENT_* | raise / approve + send a PO |

Password: `demo-pass` (the existing `identity.seed.demo-password`).

> The `emp-*` logins arrive with US3. Steps 2–3 below are read-only and work under the original `alice`
> login, so the MVP (US1) and US2 are verifiable before any of the write work lands.

## 2. Read-first (US1)

Open **Workflow** — the left pane shows grouped areas (Activity log, Authorization, Reviews, Actions …).

- **Activity log** → every recorded attempt, newest-first: actor, action, target, outcome, reason, time.
- Filter by **outcome** (e.g. `REJECTED_NOT_PERMITTED`) and by **action** → the list narrows; a filter that matches
  nothing shows an empty state, not an error. A fresh client shows the empty state.

## 3. Rules and queue (US2)

- **Authorization rules** → every seeded `role → action → kind` grant, with `PERFORM` / `MAKER` / `CHECKER` visible.
- **Pending reviews** → items awaiting a checker: action, target, recorded maker, stage. Empty until step 4.
  (In-memory by design — it clears if `workflow-service` restarts.)

## 4. Perform as maker (US3)

As `emp-sales`:

- **Take sales order (maker)** → pick a customer, add lines (item picker + quantity/price) → **approved**, the id is
  returned, the order appears in **Pending reviews**, and the attempt is in the **Activity log** as `SUCCESS`.
- Clear a required field → the call is **blocked inline**, before the edge.
- Try **Raise purchase order** as `emp-sales` → **not permitted** (their role holds no grant), and the refusal is
  recorded in the activity log.

## 5. Review as checker (US4) — the four outcomes

- Still signed in as `emp-sales`, run **Confirm payment (checker)** on your own order →
  **rejected — invalid: self review not allowed** (422), *distinct* from a not-permitted result. This is the SC-004
  check: the two must never render the same.
- Sign out, sign in as `emp-cashier`, confirm the same order → **approved**; it leaves **Pending reviews** and
  appears in the **Activity log**.
- Stop a downstream service (`docker compose -p kita-client-a stop operations-service`) and retry an action →
  **temporarily unavailable** (503) after the bounded retries, recorded as `FAILED_UNAVAILABLE`. Restart it after.

Receiving mirrors this: `emp-whse` **Record delivery receipt (maker)** → `emp-whse-mgr` **Confirm delivery receipt
(checker)** (picking the handle straight from the pending queue) → the goods receipt commits, and the stock shows in
the **Operations** tab because workflow's downstream adapters run in `http` mode.

## 6. Automated checks

```bash
cd frontend && npm test        # Vitest: Workflow manifest render/run, grouped sidebar, the four outcome states
cd frontend && npm run build   # type-check + production build
cd backend && ./gradlew :workflow-service:build   # the three read endpoints (unit + contract tests)
```

**Expect**: all green; existing 011–015 suites unaffected (`group` is optional, outcome parsing is additive);
`:workflow-service:build` green with no change to any pipeline/authorizer/recorder test.

Details: [contracts/workflow-manifest.md](./contracts/workflow-manifest.md),
[contracts/workflow-read-endpoints.md](./contracts/workflow-read-endpoints.md),
[data-model.md](./data-model.md), [research.md](./research.md).
