# hr-service

KITA's people bounded context (feature 004): employee records, time & attendance, payroll runs,
statutory and voluntary deductions, leave, and remittance summaries. Spring Boot + JPA + Flyway on
PostgreSQL (schema `hr`), behind the gateway at `/api/hr`.

## Modules

`employee` (master data, effective-dated compensation history), `attendance` (schedules, daily time
records, worked-time and premium computation), `payroll` (pay periods, runs, payslips, register, run
state machine), `deduction` (effective-dated rule engine, loans), `leave` (types, balances, requests,
accrual), `remittance` (per-agency summaries), `common` (money/rounding, effective-dating, audit,
security, error handling), `api` (REST controllers).

## How payroll computes

Per employee, for the run's pay period:

1. **Basic** — monthly basic × period factor, pro-rated by calendar days actually active
   (hire/separation dates), then reduced by approved **UNPAID leave** days (FR-020).
2. **Premiums** — overtime, holiday, and night differential from attendance × schedule × holiday
   calendar. `gross = basic + premiums`.
3. **Deductions** — pre-tax statutory contributions first (reducing the taxable base), then
   withholding tax on taxable income, then voluntary items (loan installments).
4. **Net** — `net = gross − total employee deductions`. Employer contributions never affect net.

An employee is **flagged and excluded** rather than mis-paid when they have no effective
compensation, are not active in the period, have incomplete attendance, or would fall below the
net-pay floor (FR-010/015).

Money is exact decimal (`NUMERIC`/`BigDecimal`), rounded half-up per line, so the register
reconciles to the payslips to the cent.

## Run lifecycle

`DRAFT → COMPUTED → FINALIZED`, and `DRAFT|COMPUTED → CANCELLED`. There is no transition out of
`FINALIZED`: finalize is atomic, row-locked, and idempotent, so a period cannot be double-paid
(FR-009). Corrections are made with an **ADJUSTMENT** run that references an existing FINALIZED run;
the original is never mutated (FR-011). Loan installments are drawn down once, at finalize.

## Statutory rules are data, not code

Deduction rules are effective-dated and versioned in `deduction_rule` (+ `deduction_rule_row` for
TABLE/BRACKET rows). A run uses the version effective for its period, so re-versioning a rule never
retroactively changes a finalized run. The Philippines ruleset (SSS, PhilHealth, Pag-IBIG, BIR
withholding) ships as **adoptable seed data** in `V5__seed_ph_statutory.sql`.

> The seeded PH values are **representative**. Reconcile them against the official agency tables
> before production use, and adopt changes by inserting a new `(code, effective_date)` version rather
> than editing rows in place.

Supported computations: `TABLE` (bracketed lookup), `BRACKET` (base + rate on excess), `PERCENT`
(with optional floor/cap), `FIXED`.

## Security

The gateway authenticates and forwards `X-Kita-Roles` and `X-Kita-Employee-Id`; this service only
interprets them. Roles: `HR_ADMIN`, `PAYROLL_OFFICER`, `MANAGER`, `EMPLOYEE_SELF`. An `EMPLOYEE_SELF`
caller is pinned to their own employee record and payslips.

Statutory/tax identifiers are stored for deductions and remittance but are **masked to a last-four
hint in API responses** and scrubbed from logs and audit detail (FR-004). Logs are structured JSON
(`logback-spring.xml`) with masking as a backstop.

`hr.security.stub=true` (dev/test default) treats a caller with no role header as `HR_ADMIN` so the
service is usable before the gateway is wired up. **Set `HR_SECURITY_STUB=false` in any real
environment.**

## Endpoints

| Area | Endpoint |
|---|---|
| Employees | `POST/GET /api/hr/employees`, `GET/PATCH /api/hr/employees/{id}`, `POST/GET /api/hr/employees/{id}/compensation` |
| Attendance | `POST /api/hr/attendance`, `GET /api/hr/attendance/worked-time`, `POST /api/hr/work-schedules`, `POST /api/hr/holidays`, `POST /api/hr/premium-rules` |
| Payroll | `POST /api/hr/payroll/runs`, `POST .../{id}/compute`, `POST .../{id}/finalize`, `POST .../{id}/cancel`, `GET .../{id}/register`, `GET .../{id}/remittances` |
| Payslips | `GET /api/hr/payslips?runId=&employeeId=` (self allowed) |
| Deductions | `GET/POST /api/hr/deduction-rules`, `POST /api/hr/employees/{id}/loans` |
| Leave | `POST/GET /api/hr/leave/types`, `POST /api/hr/leave/requests`, `POST /api/hr/leave/requests/{id}/decision`, `GET /api/hr/leave/balances` |
| Health | `GET /actuator/health` |

`specs/004-hr-payroll/contracts/hr-openapi.yaml` is the contract source of truth.

## Run & test

Requires **JDK 17** and, for tests, a running **Docker** daemon (Testcontainers PostgreSQL 16).

```bash
cd backend
./gradlew :hr-service:spotlessApply      # google-java-format; run before build
./gradlew :hr-service:build              # compile + Spotless + Checkstyle + tests
./gradlew :hr-service:test --tests "*LeaveServiceIT*"
```

On Windows + Docker Desktop the tests reach the daemon over `tcp://127.0.0.1:2375`, so enable
*Settings → General → Expose daemon on tcp://localhost:2375 without TLS*. The workaround is applied
in `build.gradle.kts` and is a no-op on Linux/CI.

Configuration is environment-driven (`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`,
`HR_SECURITY_STUB`); no secrets live in the repo.

## Read endpoints added for the console UI (feature 013, FR-015)

Read-only, additive endpoints so the service console can list/view resources that were previously
write-only (no existing endpoint or write/business logic changed). Role-gated like their siblings;
each covered by a MockMvc contract test.

- `GET /api/hr/payroll/runs` and `/{id}` — payroll runs (period + state); a run's per-employee lines
  remain the existing `/runs/{id}/register`.
- `GET /api/hr/leave/requests` (optional `employeeId`/`status`) and `/{id}` — leave requests.
