# Research — HR & Payroll Service UI

Phase 0 for 013. Grounded by reading `backend/hr-service/src/main/java/com/kita/hr/` (controllers, DTOs,
repositories, `common/security/CallerContext`).

## D1 — Endpoint inventory (grounded)

Under `/api/hr`. **Reads (GET):**

| Function | Method + path | Inputs | Returns |
|---|---|---|---|
| List employees | `GET /employees` | — | `EmployeeResponse[]` |
| Employee detail | `GET /employees/{id}` | id | `EmployeeResponse` |
| Compensation history | `GET /employees/{id}/compensation` | id | `CompensationResponse[]` |
| Status history | `GET /employees/{id}/status-history` | id | `StatusHistoryResponse[]` |
| Worked time | `GET /attendance/worked-time?employeeId&start&end` | employeeId, start, end (ISO date) | `WorkedTimeResponse` |
| Leave types | `GET /leave/types` | — | `LeaveTypeResponse[]` |
| Leave balances | `GET /leave/balances?employeeId` | employeeId | `LeaveBalanceResponse[]` |
| Deduction rules | `GET /deduction-rules` | — | rule list |
| Payroll register | `GET /payroll/runs/{id}/register` | run id | `RegisterResponse` (per-employee payslips + totals) |
| Payslips | `GET /payslips?runId&employeeId` | runId?, employeeId? | `PayslipResponse[]` |
| Remittances | `GET /payroll/runs/{id}/remittances` | run id | per-contribution totals |

**Writes (POST/PATCH):** create/patch employee, `POST /employees/{id}/compensation`, `POST /employees/{id}/loans`,
`POST /attendance` (**list body** of DTRs), `POST /work-schedules`, `POST /holidays`, `POST /premium-rules`,
`POST /deduction-rules`, `POST /leave/types`, `POST /leave/requests`, `POST /leave/requests/{id}/decision`,
`POST /leave/accruals`, `POST /payroll/runs`, `POST /payroll/runs/{id}/{compute|finalize|cancel}`.

## D2 — Write-only resources → RESOLVED by adding reads (mirror 012)

**Payroll runs** and **leave requests** have no list/get GET. Per the clarification (add reads), 013 adds:
- `GET /payroll/runs` (list) + `GET /payroll/runs/{id}` (single) — reuse `PayrollRunResponse`.
- `GET /leave/requests?employeeId=&status=` (list) + `GET /leave/requests/{id}` (single) — reuse
  `LeaveRequestResponse`.

Backing repositories already exist (`PayrollRunRepository`, `LeaveRequestRepository`, both `JpaRepository` →
`findAll`/`findById`). See [contracts/hr-read-api.md](./contracts/hr-read-api.md).

## D3 — Role-gating (the key HR difference from operations)

Unlike operations-service (no auth), **every hr-service endpoint is role-gated** via
`CallerContext.require(Role…)` (`HR_ADMIN` / `PAYROLL_OFFICER` / `MANAGER` / `EMPLOYEE_SELF`). Roles come from the
gateway header `X-Kita-Roles`. **Decision / finding**: `CallerContext` runs in **stub mode by default**
(`hr.security.stub=true`) — a caller with **no** role header is treated as `HR_ADMIN` (`EnumSet.allOf`). So the
011/009 console's demo session (no HR role header) can exercise HR in the sim. If deployed with
`hr.security.stub=false` and no HR role, calls return **403** — rendered by the 011 error state (FR-012). The new
reads are gated like their siblings (runs → `HR_ADMIN`/`PAYROLL_OFFICER`; leave requests →
`HR_ADMIN`/`MANAGER`/`PAYROLL_OFFICER`/`EMPLOYEE_SELF`).

## D4 — Reuse the 012 shared inputs (no new frontend framework)

HR's forms need exactly what 012 built: **employee reference pickers** (`GET /employees` → value `id`, label
`employeeNo — firstName lastName`), **list inputs** (the DTR ingest body is `List<DtrRequest>`; loans/lines),
enum `select`s (EmploymentType/EmployeeStatus/RunType/PayTreatment/LeaveStatus/PremiumKind), and **id→label**
(`resultRefs` relabel `employeeId` columns via the employees list). **Decision**: 013 adds **no** framework code;
it authors `manifests/hr.ts` against the shared inputs. ⚠️ The 013 branch predates the 012 merge — **sync `main`
first** so those inputs + the `reference`/`list` types exist.

## D5 — A run's lines come from the register (no lines endpoint)

`GET /payroll/runs/{id}/register` already returns `RegisterResponse` (per-employee `payslips` + `totalGross/
totalDeductions/totalEmployerContrib/totalNet`). **Decision**: US3 "view a run's computed per-employee lines" =
the register function; the new runs list/get only carries period + state (`PayrollRunResponse`). No new
per-run-lines endpoint.

## D6 — PII handling (Constitution III)

`EmployeeResponse` **already masks** statutory/tax ids (SSS/PhilHealth/Pag-IBIG/TIN) to a last-four hint
server-side. **Decision**: the UI renders responses as-is (masked) and persists nothing beyond the 009 session
cookie; no client-side PII storage (FR-012).

## Summary of decisions

1. Manifest wires to the existing HR GETs/POSTs **plus** the 4 new read endpoints (D2).
2. Payroll runs + leave requests become **listable/viewable**; a run's lines = the existing register.
3. HR is **role-gated**; stub mode (sim default) → demo user is `HR_ADMIN`, so HR works; else clear 403.
4. **Reuse 012's shared inputs** (reference picker, list input, id→label) — no new frontend framework; **sync
   `main`** into 013 first.
5. Backend addition is **read-only, additive, role-gated, tenant-scoped**, each with a red-first contract test.
6. PII stays server-masked; the UI stores nothing sensitive.
