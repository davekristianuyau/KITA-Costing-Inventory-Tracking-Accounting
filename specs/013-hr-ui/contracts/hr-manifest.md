# Contract — HR service manifest

The concrete manifest that fills the 011 HR tab. Every function maps to a **real** hr-service endpoint (Phase 0
D1 + the FR-015 reads) under `basePath: "/api/hr"`, rendered by the 011 `FunctionWorkspace`, using the **012
shared inputs** (reference picker, list, id→label). `{param}` tokens fill from inputs; empty optional query
params are dropped (012 `buildPath`). The **employee reference source** = `GET /api/hr/employees`
(value `id`, label `employeeNo — firstName lastName`); `resultRefs` relabel `employeeId` columns from it.

## Reads

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `employees` | Employees | GET | `/employees` | — | table |
| `employee` | Employee detail | GET | `/employees/{id}` | id: reference→employees | detail |
| `compensation` | Compensation history | GET | `/employees/{id}/compensation` | id: reference→employees | table |
| `status-history` | Status history | GET | `/employees/{id}/status-history` | id: reference→employees | table |
| `worked-time` | Worked time & premiums | GET | `/attendance/worked-time?employeeId={employeeId}&start={start}&end={end}` | employeeId: reference→employees; start*, end* (ISO date) | detail |
| `leave-types` | Leave types | GET | `/leave/types` | — | table |
| `leave-balances` | Leave balances | GET | `/leave/balances?employeeId={employeeId}` | employeeId*: reference→employees | table |
| `leave-requests` | Leave requests | GET | `/leave/requests?employeeId={employeeId}&status={status}` | employeeId?: reference→employees; status?: select(LeaveStatus) | table |
| `leave-request` | Leave request detail | GET | `/leave/requests/{id}` | id*: text | detail |
| `deduction-rules` | Deduction rules | GET | `/deduction-rules` | — | table |
| `payroll-runs` | Payroll runs | GET | `/payroll/runs` | — | table |
| `payroll-run` | Payroll run detail | GET | `/payroll/runs/{id}` | id*: text | detail |
| `register` | Payroll register | GET | `/payroll/runs/{id}/register` | id*: text | detail (per-employee payslips + totals) |
| `remittances` | Statutory remittances | GET | `/payroll/runs/{id}/remittances` | id*: text | detail |
| `payslips` | Payslips | GET | `/payslips?runId={runId}&employeeId={employeeId}` | runId?: text; employeeId?: reference→employees | table |

## Writes

| id | label | method | path | inputs | result |
|---|---|---|---|---|---|
| `create-employee` | New employee | POST | `/employees` | employeeNo*, firstName*, lastName* text; birthDate?, email?, phone?, position? text; employmentType select(EmploymentType); dateHired? text | detail |
| `update-employee` | Update employee | PATCH | `/employees/{id}` | id*: reference→employees; (editable fields) | detail |
| `add-compensation` | Add compensation | POST | `/employees/{id}/compensation` | id*: reference→employees; rate*, effectiveDate* | detail |
| `add-loan` | Add loan | POST | `/employees/{id}/loans` | id*: reference→employees; (loan fields) | detail |
| `ingest-dtr` | Record time (DTR) | POST | `/attendance` | `records*` list of { employeeId* ref→employees, workDate* text, timeIn* text, timeOut* text, source? text } | message (202) |
| `create-leave-type` | New leave type | POST | `/leave/types` | code*, name* text; payTreatment select(PayTreatment); accrual fields | detail |
| `file-leave` | File leave request | POST | `/leave/requests` | employeeId* ref→employees; leaveTypeId* ref→leave-types; startDate*, endDate* text; duration* number; reason? text | detail |
| `decide-leave` | Approve / reject leave | POST | `/leave/requests/{id}/decision` | id*: text; approved* boolean; decidedBy? text | detail |
| `accrue-leave` | Run leave accrual | POST | `/leave/accruals` | employeeId* ref→employees; leaveTypeId* ref→leave-types; periods* number | detail |
| `create-deduction-rule` | New deduction rule | POST | `/deduction-rules` | (rule fields) | detail |
| `create-run` | New payroll run | POST | `/payroll/runs` | period* (start/end/payDate) ; type? select(RunType); employeeIds? list of { employeeId* ref→employees } | detail |
| `compute-run` | Compute payroll run | POST | `/payroll/runs/{id}/compute` | id*: text | detail |
| `finalize-run` | Finalize payroll run | POST | `/payroll/runs/{id}/finalize` | id*: text | detail (idempotent — repeat is a no-op) |
| `cancel-run` | Cancel payroll run | POST | `/payroll/runs/{id}/cancel` | id*: text | detail |

## Left-pane grouping

- **Employees**: Employees, Employee detail, Compensation history, Status history, New employee, Update
  employee, Add compensation, Add loan
- **Attendance**: Worked time & premiums, Record time (DTR)
- **Leave**: Leave balances, Leave requests, Leave request detail, Leave types, File leave request,
  Approve / reject leave, Run leave accrual, New leave type
- **Payroll**: Payroll runs, Payroll run detail, New payroll run, Compute / Finalize / Cancel payroll run,
  Deduction rules, New deduction rule
- **Outputs**: Payroll register, Statutory remittances, Payslips

## Rules

- `reference→employees` inputs load from `GET /api/hr/employees` (value `id`, label `employeeNo — firstName
  lastName`); `leaveTypeId` uses `reference→leave-types`. See 012 `workspace-framework-extensions`.
- Required (`*`) inputs block the call with inline validation; list bodies (DTRs, run employeeIds) use the 012
  `list` input.
- Result tables resolve `employeeId` → the employee label via `resultRefs`.
- **Role/state errors** (403 when a role is missing in non-stub mode; wrong-state compute/finalize) surface
  through 011's error state with the backend's message. Finalize is **idempotent** (a repeat is a safe no-op).
- Payroll runs / leave requests are listable via the FR-015 reads; a run's per-employee lines are the
  **register**.

## Acceptance

- Opening HR shows all groups; each read returns real data (or a clear empty/error) via the edge.
- `employees`/`compensation`/`worked-time`/`leave-*`/`payroll-runs`/`register`/`remittances`/`payslips` render;
  writes validate required inputs and render the response.
- After `create-run` (or a leave decision), the matching list/detail read reflects it; finalize twice does not
  double-post.
