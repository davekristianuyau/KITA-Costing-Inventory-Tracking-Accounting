# Quickstart — HR & Payroll Service UI

Validates the full HR workspace on top of the 011 console + the bounded **read-only** hr-service endpoints
(FR-015). Run from the repo root on branch `013-hr-ui`. **0 real cloud.**

> **Before implementing**: sync `main` into `013-hr-ui` so the 012 shared inputs (`ReferenceInput`, `ListInput`,
> `idLabels` + the `reference`/`list` manifest types) are present. This branch predates the 012 merge.

## 0. Backend read endpoints (FR-015)

```bash
cd backend && ./gradlew :hr-service:build   # includes the new GET contract tests (red-first)
```

**Expect**: green — `GET /payroll/runs` (+ `/{id}`) and `GET /leave/requests` (+ `/{id}`) return the documented
shapes (or 404 for a missing id), role-gated (stub mode → HR_ADMIN); no existing hr-service test regresses.

## 1. Bring up the environment

```bash
cd frontend && npm run dev        # dev server, proxies /auth + /api to the edge
# — or the whole local stack (floci-aws + 009 backend/edge + console):
bash sim/console/console-up.sh client-a
open http://localhost:8080/login  # company=client-a user=alice password=demo-pass
```

## 2. Read-first (US1–US3)

**Expect** — HR tab shows the left-pane groups (Employees, Attendance, Leave, Payroll, Outputs):

- **Employees** → lists employees; **Employee detail** (pick an employee) shows attributes (statutory ids masked);
  **Compensation history** shows effective-dated rates in date order.
- **Worked time & premiums** → pick an employee + start/end → worked hours + OT/holiday/night-diff premiums.
- **Leave balances / Leave requests** → pick an employee → balances by type; requests list with status.
- **Payroll runs** → lists runs (period + state); **Payroll run detail** opens one; **Payroll register** shows
  per-employee gross/deductions/net + reconciling totals.

## 3. Write actions + outputs (US4–US5)

**Expect** — each is a validated run-form; missing required inputs block the call:

- **New payroll run** → create → it appears in **Payroll runs** as draft; **Compute** → register populates;
  **Finalize** (twice) → finalizes once, the repeat is a safe no-op.
- **Record time (DTR)** → submit a list of DTR rows → **Worked time** reflects it.
- **File leave request** → then **Approve / reject leave** by id → the request's status transitions (visible via
  **Leave requests**).
- **Payslips / Statutory remittances** for a finalized run render per-employee earnings and per-contribution
  totals.
- A call missing an HR role (non-stub deployment) shows a clear **403** message, not a crash.

## 4. Automated checks

```bash
cd frontend && npm test           # Vitest: HR manifest render/run (reuses the 012 input suites)
cd frontend && npm run build      # type-check + production build
```

**Expect**: all green; employee reference pickers load from `GET /api/hr/employees`; existing 011/012 suites stay
green.

Details: [contracts/hr-manifest.md](./contracts/hr-manifest.md), [contracts/hr-read-api.md](./contracts/hr-read-api.md),
[data-model.md](./data-model.md), [research.md](./research.md).
