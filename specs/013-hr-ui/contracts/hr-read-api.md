# Contract — hr-service read endpoints (FR-015)

The bounded, **read-only** backend addition for 013 (mirrors 012's Q1=C). New `@GetMapping`s on existing
controllers under `/api/hr`, backed by `findAll`/`findById` on existing repositories, reusing existing response
DTOs. **No existing endpoint, entity, or write path changes** (FR-014). Each read is **role-gated** like its
siblings and tenant-scoped by the hr-service datasource.

## Endpoints

| Method + path | Backing | Response (200) | Roles | Not found |
|---|---|---|---|---|
| `GET /api/hr/payroll/runs` | `PayrollRunRepository.findAll` | `PayrollRunResponse[]` | HR_ADMIN, PAYROLL_OFFICER | — (empty `[]`) |
| `GET /api/hr/payroll/runs/{id}` | `PayrollRunRepository.findById` | `PayrollRunResponse` | HR_ADMIN, PAYROLL_OFFICER | 404 |
| `GET /api/hr/leave/requests?employeeId=&status=` | `LeaveRequestRepository` (findAll / by employee / by status) | `LeaveRequestResponse[]` | HR_ADMIN, MANAGER, PAYROLL_OFFICER, EMPLOYEE_SELF | — (empty `[]`) |
| `GET /api/hr/leave/requests/{id}` | `LeaveRequestRepository.findById` | `LeaveRequestResponse` | HR_ADMIN, MANAGER, PAYROLL_OFFICER, EMPLOYEE_SELF | 404 |

## Response shapes (reuse existing records, unchanged)

- `PayrollRunResponse` = (id, status, type, periodStart, periodEnd, payDate).
- `LeaveRequestResponse` = (id, employeeId, leaveTypeId, startDate, endDate, duration, status).

## Rules

- **Read-only & additive**: only new GET handlers + new `list()/get(id)` (payroll) / `listRequests()/
  getRequest(id)` (leave) service methods — thin pass-throughs mapping entities via the existing `from(...)`
  factories. No change to any existing controller method, service write path, entity, or migration.
- **Role-gating**: each read calls `caller.require(...)` with the roles above (consistent with `register` /
  `balances`). In stub mode the demo caller is `HR_ADMIN` (research D3).
- **Tenant isolation**: relies on the existing hr-service datasource; `findAll` returns only this client's rows.
- **Leave request filtering**: `employeeId` and `status` are optional query filters; absent → all (a test asserts
  the filters narrow correctly, reusing the repository's `findByEmployeeIdAndStatus`).
- **Ordering**: lists return newest-first where a creation timestamp exists, else a stable id/insertion order
  (documented per endpoint in the tests).
- **Errors**: `{id}` reads return **404** when absent; lists return `200` with `[]` when empty; a missing role
  (non-stub) returns **403** via the existing `ForbiddenException` handler.

## Tests (red-first, per constitution)

- A **MockMvc contract test per endpoint**: list returns created rows in the documented shape/order; `{id}`
  returns the row or 404; the leave-request `employeeId`/`status` filters narrow the list. Reuse the service's
  existing test fixtures/seed helpers; stub security keeps the caller `HR_ADMIN`.
- Runs in `:hr-service:build`; heavier Testcontainers ITs stay CI-only (local Docker caveat).

## Acceptance

- Each new GET returns the documented shape; `{id}` 404s when absent; lists are empty-safe and role-gated.
- No existing hr-service test regresses; no existing endpoint/behavior changes.
- After `POST /payroll/runs` (or a leave decision), the matching list/detail GET returns that record — making the
  spec's US3/US4 acceptance verifiable through the API.
