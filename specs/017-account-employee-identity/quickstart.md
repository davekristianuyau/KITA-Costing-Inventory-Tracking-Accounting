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
`account_link_change` with the acting administrator. A non-`HR_ADMIN` caller is refused (FR-010).

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
