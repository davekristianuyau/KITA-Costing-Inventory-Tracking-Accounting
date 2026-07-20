# Data Model — HR & Payroll Service UI

No new tables or schema changes. The backend addition (FR-015) is **read-only endpoints over existing entities**
(see [contracts/hr-read-api.md](./contracts/hr-read-api.md)). Field names/types mirror `hr-service`'s DTOs so
result rendering matches the wire. No new frontend framework — reuses the 012 shared inputs.

## Surfaced HR entities (read/response shapes)

- **Employee** (`EmployeeResponse`): `id`, `employeeNo`, `firstName`, `lastName`, `birthDate`, `email`, `phone`,
  `employmentType`, `position`, `dateHired`, `dateSeparated`, `status`, and **masked** `sssNo`/`philhealthNo`/
  `pagibigNo`/`tin` (last-four hint only — server-side PII masking).
- **Compensation** (`CompensationResponse`): effective-dated pay rate for an employee (rate + effective date).
- **Status history** (`StatusHistoryResponse`): employee status changes over time.
- **Worked time** (`WorkedTimeResponse`): worked hours + OT / holiday / night-diff premiums for a period.
- **Leave type** (`LeaveTypeResponse`): code, name, pay treatment, accrual policy.
- **Leave balance** (`LeaveBalanceResponse`): balance per leave type for an employee.
- **Leave request** (`LeaveRequestResponse`): `id`, `employeeId`, `leaveTypeId`, `startDate`, `endDate`,
  `duration`, `status` — **listable via FR-015**.
- **Payroll run** (`PayrollRunResponse`): `id`, `status`, `type`, `periodStart`, `periodEnd`, `payDate` —
  **listable via FR-015**.
- **Register** (`RegisterResponse`): `runId`, `employeeCount`, `totalGross`, `totalDeductions`,
  `totalEmployerContrib`, `totalNet`, `payslips[]` (`PayslipResponse`) — the run's per-employee lines.
- **Payslip** (`PayslipResponse`): an employee's earnings / deductions / net for a run.
- **Remittance**: per-contribution statutory totals for a run (SSS/PhilHealth/Pag-IBIG/BIR).
- **Deduction rule**: a statutory/voluntary deduction rule definition.

## Request shapes (write forms) — required (`*`) from the DTOs

- **Create employee** (`CreateEmployeeRequest`): `employeeNo*`, `firstName*`, `lastName*`, + optional
  birthDate/email/phone/position/dateHired/employmentType.
- **Add compensation** (`CompensationRequest`): rate + effective date (required per DTO).
- **DTR ingest** (`List<DtrRequest>`): each row `employeeId*`, `workDate*`, `timeIn*`, `timeOut*`, `source?` —
  the **012 list input**.
- **File leave** (`FileLeaveRequest`): `employeeId*`, `leaveTypeId*`, `startDate*`, `endDate*`, `duration*`
  (positive), `reason?`.
- **Leave decision** (`LeaveDecisionRequest`): `approved*` (boolean), `decidedBy?`.
- **Create payroll run** (`CreatePayrollRunRequest`): `period*` (start/end/payDate), `type?` (RunType),
  `adjustsRunId?`, `employeeIds?` (list).

## Enums (for `select` inputs)

`EmploymentType`, `EmployeeStatus`, `RunType`, `RunStatus`, `PayTreatment`, `LeaveStatus`, premium `kind` —
values read at implementation time from the hr-service enums.

## Manifest-model additions

**None.** 013 reuses the 012 `InputField` kinds (`reference`, `list`) + `resultRefs` unchanged. The 013 branch
must **sync `main`** (post-012-merge) so those exist before implementation.

## Notes

- Monetary/decimal values (gross, deductions, net, totals) are displayed exactly as returned; the UI performs no
  payroll arithmetic.
- PII (statutory ids) is already masked server-side; the UI stores nothing sensitive beyond the 009 session
  cookie.
