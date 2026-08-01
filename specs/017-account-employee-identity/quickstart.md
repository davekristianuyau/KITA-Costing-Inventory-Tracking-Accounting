# Quickstart & Validation: 017 Account-to-Employee Identity

Proves a signed-in user acts as **their own employee**, with status and roles read from the personnel
record. Details in [research.md](./research.md), [data-model.md](./data-model.md) and
[contracts/hr-identity-api.md](./contracts/hr-identity-api.md).

## Prerequisites
- Docker (the composed stack). JDK 17 + Gradle wrapper from `backend/`.
- Docker Desktop's *Expose daemon on tcp://localhost:2375* toggle for Testcontainers ITs locally.

## 1. Build gates (US1/US4 — SC-003)
```bash
cd backend
./gradlew :hr-service:build :workflow-service:build
```
Expected: hr's new link/roles tests pass; workflow's **`contractTest`** binds against hr's real
`EmployeeResponse` — it goes red the moment `roles`/`accountUsername` land until the caller is updated,
which is the drift guard from 018 working as intended.

## 2. A user acts as their own employee (US1 — SC-003)
```bash
docker compose up --build -d      # HR_ADAPTER is now `http` everywhere
# link an account to an employee holding a permitting role
curl -X PUT  localhost:8081/api/hr/employees/{id}/account -d '{"accountUsername":"alice"}'
curl -X PUT  localhost:8081/api/hr/employees/{id}/roles   -d '{"roles":["SALES"]}'
# act as that login
curl -X POST localhost:8081/api/workflow/sales-orders -H 'X-Kita-User: alice' -d '{...}'
```
Expected: the action succeeds and `back_office_activity` attributes it to **that employee's id**, not to
a shared or stand-in identity. A second account linked to a different employee is attributed separately —
never conflated (US1 scenario 2).

## 3. Roles come from the personnel record, live (SC-006)
Remove `SALES` via `PUT /roles`, retry the same action → refused **on the next attempt**, no re-login, no
redeploy, no seed edit. Re-add it → access resumes.

## 4. Leavers lose access immediately (US3 — SC-002)
With a session already open, set the employee's status to `SEPARATED`, then retry → refused with a reason
**naming the status**, distinct from "not permitted". An existing session must not extend access.

## 5. The four failures stay distinguishable (SC-004)
| Situation | Expect |
|---|---|
| account with no linked employee | 422, "account has no employee" |
| employee inactive / separated | 422, reason names the status |
| linked employee deleted | 422, "linked employee no longer exists" |
| hr-service stopped | **503** temporarily-unavailable — never granted (FR-011) |
| linked + active but lacking the role | **403** not-permitted |

Each is recorded in `back_office_activity` (FR-006). Confirm no two collapse into the same outcome.

## 6. Administration + audit (US2 — FR-008/009/010)
Link, list (`GET /api/hr/account-links`), unlink. Linking a second account to the same employee (or a
second employee to the same account) → **409** naming the conflict. Every change appears in
`identity_change` with the acting administrator. A non-`OWNER` caller is refused (FR-010/FR-017).

## 6b. OWNER: privileges, and the accepted maker-checker trade-off (US2 — FR-017/FR-020, SC-010)

Grant an employee `OWNER`, then confirm:
- they can administer links and grant roles, while a non-`OWNER` administrator cannot (FR-017);
- `OWNER` implies every service's own roles — one account can reach hr, crm, procurement and workflow;
- they may raise **and** approve the same purchase order (FR-020), whereas any non-`OWNER` doing so is
  still refused **422 self-review** — the control is unchanged for everyone else;
- that action is findable afterwards, which is the whole mitigation for the trade-off:

```sql
SELECT action, actor_employee_id, at FROM back_office_activity
WHERE maker_employee_id IS NOT NULL AND maker_employee_id = actor_employee_id;
```
Expected: the owner's self-approved action is listed. **No schema change** — both columns already exist.

## 6c. Roles resolve per request, never from the token (US4 — FR-018, SC-006/SC-008)

- Revoke a role and **immediately** retry without re-logging-in → refused. If it still succeeds, roles are
  being carried in the session, which is exactly what this design forbids.
- Stop hr-service and retry any authorized request → **temporarily unavailable**, never granted (FR-011).
- Call a service with **no** role header at all → **nothing** is granted. Before 017 this returned full
  privileges via `stub`; that must no longer happen in any stack that authorizes (SC-008).
- ⚠️ **Before flipping `stub` off**, confirm at least one account resolves to an employee holding `OWNER`
  (FR-019) — otherwise the deployment is unadministerable. The plain `docker-compose.yml` stack sets no
  identity headers at all, so it must be fronted by the edge or labelled explicitly unauthenticated.

## 7. Stand-ins are gone (US4 — SC-005, FR-012)
```bash
grep -rn "position-roles\|HrPositionRoles" backend/ ; echo "--- expect: no hits ---"
grep -rn "HR_ADAPTER" docker-compose*.yml sim/   # expect: http, no `fake` in a deployed path
```
The sim's logins must resolve through **real links**, not `emp-*` magic names. `InMemoryHrAdapter`
remains only as a `:workflow-service:test` seam.

## 8. Nothing else changed (SC-007 — FR-013)
```bash
cd backend && ./gradlew build
```
Expected: existing authorization outcomes, maker-checker controls and activity records pass unchanged —
this feature changes *whose* roles are read, not *what* the rules are. Baseline: only hr's
`OpenApiContractTest` is red on `main` (see [[kita-ci-known-red-jobs]]) — and 017 should **fix** it,
since the new endpoints must be documented.

## Success signals
SC-001 new hire works via administration alone · SC-002 separation bites on the next attempt ·
SC-003 100% attributed to the real employee · SC-004 four failures distinguishable · SC-005 zero seeded
directories in deployed paths · SC-006 role change applies next action · SC-007 existing checks unchanged.

---

## Verified run — 2026-08-01 (composed stack, `stub` OFF, `HR_ADAPTER=http`)

Everything below was executed against the running stack, not inferred.

| Criterion | Evidence |
|---|---|
| **SC-001** | `POST /api/workflow/customers` as `emp-crm` → **201**, and the customer is readable from crm-service with the derived `customerCode` |
| **SC-002** | Employee set `SEPARATED` in hr → the **very next action** with the same session → `422 "employee is SEPARATED"`. No re-login, no redeploy |
| **SC-003** | The activity row is attributed to `4bde73d5…` — the **employee id** behind account `emp-crm`, not the login. An unresolvable account is honestly recorded as the account name |
| **SC-004** | Four distinct outcomes proven live: no employee → `422 "no employee is linked to account X"`; separated → `422 "employee is SEPARATED"`; linked+active but wrong role → `403 role not permitted`; hr down → `503` |
| **SC-008** | With `stub` off, a caller whose roles do not grant the action is refused — the permissive default is gone |
| **SC-009** | `SELECT … FROM hr.identity_change WHERE action LIKE 'ROLE_%'` lists every grant with its administrator |
| **SC-010** | `WHERE maker_employee_id = actor_employee_id` lists the owner's self-approved `CONFIRM_SALES_PAYMENT` |
| **FR-011** | hr stopped → `503 FAILED_UNAVAILABLE`, never granted — **and the refusal is recorded** (FR-006) |
| **FR-017** | The `owner` account performed operations-service actions (uom/item/location/adjustment), proving `OWNER` implies every service's own roles |
| **FR-019** | hr seeds 10 employees with real links; exactly one holds `OWNER`, so the deployment is administerable |
| **FR-020** | The owner drafted **and** confirmed the same sales order → `200 PAYMENT_CONFIRMED`; a non-owner doing the same is still `422 self-review` |

⚠️ **Two real bugs this run caught** that no unit test had:
1. workflow forwarded `X-Kita-User` but **not** `X-Kita-Roles` on service-to-service calls, so every
   orchestrated write was refused once `stub` was off (`stub=true` had masked it).
2. `SalesOrderWorkflow`/`ReceivingWorkflow` each hold a **second** self-review guard behind the
   pipeline's. They did not know about `OWNER`, so FR-020 silently did nothing end to end even though
   the pipeline exempted correctly.
