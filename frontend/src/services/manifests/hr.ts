// HR & Payroll service manifest (spec 013, contracts/hr-manifest.md). Reuses the 012 shared inputs
// (reference picker, list, id→label) — no new framework. Grows through the user stories: US1 employees +
// compensation, US2 attendance/leave (+ leave-request read), US3 payroll runs review (+ run read), US4
// writes, US5 outputs. Each function is rendered by the 011 FunctionWorkspace and called via the 009 edge.
import type { ReferenceSource, ServiceManifest } from "../types";

// Reference sources — the pickers + id→label resolution source from these lists.
const EMPLOYEES_SOURCE: ReferenceSource = {
  path: "/api/hr/employees",
  valueKey: "id",
  labelKeys: ["employeeNo", "firstName", "lastName"],
  labelSep: " ",
};
const LEAVE_TYPES_SOURCE: ReferenceSource = {
  path: "/api/hr/leave/types",
  valueKey: "id",
  labelKeys: ["code", "name"],
};

/** Resolve employee-id result columns to the employee label. */
const employeeLabels = (columns: string[]) => [{ columns, source: EMPLOYEES_SOURCE }];

export const hrManifest: ServiceManifest = {
  id: "hr",
  label: "HR & Payroll",
  icon: "Users",
  basePath: "/api/hr",
  functions: [
    // --- Employees (US1) ---
    {
      id: "employees",
      label: "Employees",
      icon: "Users",
      method: "GET",
      path: "/employees",
      result: "table",
      description: "Workforce — name, number, position, status (statutory ids masked).",
    },
    {
      id: "employee",
      label: "Employee detail",
      icon: "UserRound",
      method: "GET",
      path: "/employees/{id}",
      result: "detail",
      inputs: [{ name: "id", label: "Employee", type: "reference", required: true, source: EMPLOYEES_SOURCE }],
      description: "Full attributes for one employee.",
    },
    {
      id: "compensation",
      label: "Compensation history",
      icon: "Coins",
      method: "GET",
      path: "/employees/{id}/compensation",
      result: "table",
      inputs: [{ name: "id", label: "Employee", type: "reference", required: true, source: EMPLOYEES_SOURCE }],
      description: "Effective-dated pay rates for an employee.",
    },
    {
      id: "status-history",
      label: "Status history",
      icon: "History",
      method: "GET",
      path: "/employees/{id}/status-history",
      result: "table",
      inputs: [{ name: "id", label: "Employee", type: "reference", required: true, source: EMPLOYEES_SOURCE }],
      description: "Employment status changes over time.",
    },
    // --- Attendance (US2) ---
    {
      id: "worked-time",
      label: "Worked time & premiums",
      icon: "Clock",
      method: "GET",
      path: "/attendance/worked-time?employeeId={employeeId}&start={start}&end={end}",
      result: "detail",
      inputs: [
        { name: "employeeId", label: "Employee", type: "reference", required: true, source: EMPLOYEES_SOURCE },
        { name: "start", label: "Start date", type: "text", required: true, placeholder: "YYYY-MM-DD" },
        { name: "end", label: "End date", type: "text", required: true, placeholder: "YYYY-MM-DD" },
      ],
      description: "Worked hours + OT / holiday / night-diff premiums for a period.",
    },
    // --- Leave (US2 reads) ---
    {
      id: "leave-balances",
      label: "Leave balances",
      icon: "CalendarCheck",
      method: "GET",
      path: "/leave/balances?employeeId={employeeId}",
      result: "table",
      inputs: [
        { name: "employeeId", label: "Employee", type: "reference", required: true, source: EMPLOYEES_SOURCE },
      ],
      description: "Leave balance per type for an employee.",
    },
    {
      id: "leave-requests",
      label: "Leave requests",
      icon: "CalendarClock",
      method: "GET",
      path: "/leave/requests?employeeId={employeeId}&status={status}",
      result: "table",
      inputs: [
        { name: "employeeId", label: "Employee", type: "reference", source: EMPLOYEES_SOURCE },
        { name: "status", label: "Status", type: "select", options: ["FILED", "APPROVED", "REJECTED"] },
      ],
      resultRefs: employeeLabels(["employeeId"]),
      description: "Leave requests with status (optionally filtered by employee/status).",
    },
    {
      id: "leave-request",
      label: "Leave request detail",
      icon: "FileText",
      method: "GET",
      path: "/leave/requests/{id}",
      result: "detail",
      inputs: [{ name: "id", label: "Leave request id", type: "text", required: true }],
      resultRefs: employeeLabels(["employeeId"]),
    },
    {
      id: "leave-types",
      label: "Leave types",
      icon: "ListChecks",
      method: "GET",
      path: "/leave/types",
      result: "table",
      description: "Configured leave types + accrual policy.",
    },
    {
      id: "deduction-rules",
      label: "Deduction rules",
      icon: "Percent",
      method: "GET",
      path: "/deduction-rules",
      result: "table",
      description: "Statutory + voluntary deduction rules.",
    },
    // --- Payroll (US3 reads) ---
    {
      id: "payroll-runs",
      label: "Payroll runs",
      icon: "CalendarRange",
      method: "GET",
      path: "/payroll/runs",
      result: "table",
      description: "All payroll runs with their period + state.",
    },
    {
      id: "payroll-run",
      label: "Payroll run detail",
      icon: "FileText",
      method: "GET",
      path: "/payroll/runs/{id}",
      result: "detail",
      inputs: [{ name: "id", label: "Payroll run id", type: "text", required: true }],
    },
    {
      id: "register",
      label: "Payroll register",
      icon: "Table2",
      method: "GET",
      path: "/payroll/runs/{id}/register",
      result: "detail",
      inputs: [{ name: "id", label: "Payroll run id", type: "text", required: true }],
      description: "Per-employee gross / deductions / net + reconciling totals for a run.",
    },
    // --- Employee writes (US4) ---
    {
      id: "create-employee",
      label: "New employee",
      icon: "UserPlus",
      method: "POST",
      path: "/employees",
      result: "detail",
      inputs: [
        { name: "employeeNo", label: "Employee no.", type: "text", required: true },
        { name: "firstName", label: "First name", type: "text", required: true },
        { name: "lastName", label: "Last name", type: "text", required: true },
        {
          name: "employmentType",
          label: "Employment type",
          type: "select",
          options: ["REGULAR", "PROBATIONARY", "CONTRACTUAL", "CASUAL"],
        },
        { name: "position", label: "Position", type: "text" },
        { name: "dateHired", label: "Date hired", type: "text", placeholder: "YYYY-MM-DD" },
        { name: "email", label: "Email", type: "text" },
        { name: "phone", label: "Phone", type: "text" },
      ],
      description: "Create an employee record.",
    },
    {
      id: "add-compensation",
      label: "Add compensation",
      icon: "Coins",
      method: "POST",
      path: "/employees/{id}/compensation",
      result: "detail",
      inputs: [
        { name: "id", label: "Employee", type: "reference", required: true, source: EMPLOYEES_SOURCE },
        { name: "effectiveDate", label: "Effective date", type: "text", required: true, placeholder: "YYYY-MM-DD" },
        { name: "basicPay", label: "Basic pay", type: "number", required: true },
        { name: "payFrequency", label: "Pay frequency", type: "select", required: true, options: ["MONTHLY", "SEMI_MONTHLY"] },
      ],
      description: "Add an effective-dated pay rate for an employee.",
    },
    // --- Attendance write (raw-array body) ---
    {
      id: "ingest-dtr",
      label: "Record time (DTR)",
      icon: "ClockArrowUp",
      method: "POST",
      path: "/attendance",
      result: "message",
      bodyInput: "records",
      inputs: [
        {
          name: "records",
          label: "Daily time records",
          type: "list",
          required: true,
          minRows: 1,
          fields: [
            { name: "employeeId", label: "Employee", type: "reference", required: true, source: EMPLOYEES_SOURCE },
            { name: "workDate", label: "Work date", type: "text", required: true, placeholder: "YYYY-MM-DD" },
            { name: "timeIn", label: "Time in", type: "text", required: true, placeholder: "HH:MM" },
            { name: "timeOut", label: "Time out", type: "text", required: true, placeholder: "HH:MM" },
            { name: "source", label: "Source", type: "text" },
          ],
        },
      ],
      description: "Ingest daily time records (feeds worked time + premiums).",
    },
    // --- Leave writes ---
    {
      id: "create-leave-type",
      label: "New leave type",
      icon: "CalendarPlus",
      method: "POST",
      path: "/leave/types",
      result: "detail",
      inputs: [
        { name: "code", label: "Code", type: "text", required: true },
        { name: "name", label: "Name", type: "text", required: true },
        { name: "payTreatment", label: "Pay treatment", type: "select", required: true, options: ["PAID", "UNPAID", "CONVERTIBLE"] },
        { name: "accrualRate", label: "Accrual rate", type: "number", required: true },
        { name: "accrualPeriod", label: "Accrual period", type: "select", options: ["MONTHLY", "ANNUAL"] },
        { name: "allowNegative", label: "Allow negative", type: "boolean" },
      ],
      description: "Define a leave type + accrual policy.",
    },
    {
      id: "file-leave",
      label: "File leave request",
      icon: "CalendarClock",
      method: "POST",
      path: "/leave/requests",
      result: "detail",
      inputs: [
        { name: "employeeId", label: "Employee", type: "reference", required: true, source: EMPLOYEES_SOURCE },
        { name: "leaveTypeId", label: "Leave type", type: "reference", required: true, source: LEAVE_TYPES_SOURCE },
        { name: "startDate", label: "Start date", type: "text", required: true, placeholder: "YYYY-MM-DD" },
        { name: "endDate", label: "End date", type: "text", required: true, placeholder: "YYYY-MM-DD" },
        { name: "duration", label: "Duration (days)", type: "number", required: true },
        { name: "reason", label: "Reason", type: "text" },
      ],
      description: "File a leave request for an employee.",
    },
    {
      id: "decide-leave",
      label: "Approve / reject leave",
      icon: "CheckCircle2",
      method: "POST",
      path: "/leave/requests/{id}/decision",
      result: "detail",
      inputs: [
        { name: "id", label: "Leave request id", type: "text", required: true },
        { name: "approved", label: "Approve", type: "boolean" },
        { name: "decidedBy", label: "Decided by", type: "text" },
      ],
      description: "Approve or reject a filed leave request.",
    },
    {
      id: "accrue-leave",
      label: "Run leave accrual",
      icon: "CalendarCheck",
      method: "POST",
      path: "/leave/accruals",
      result: "detail",
      inputs: [
        { name: "employeeId", label: "Employee", type: "reference", required: true, source: EMPLOYEES_SOURCE },
        { name: "leaveTypeId", label: "Leave type", type: "reference", required: true, source: LEAVE_TYPES_SOURCE },
        { name: "periods", label: "Periods", type: "number", required: true },
      ],
      description: "Apply the leave type's accrual policy for an employee.",
    },
    // --- Payroll writes ---
    {
      id: "create-run",
      label: "New payroll run",
      icon: "CalendarPlus",
      method: "POST",
      path: "/payroll/runs",
      result: "detail",
      inputs: [
        { name: "period.frequency", label: "Frequency", type: "select", required: true, options: ["MONTHLY", "SEMI_MONTHLY"] },
        { name: "period.startDate", label: "Period start", type: "text", required: true, placeholder: "YYYY-MM-DD" },
        { name: "period.endDate", label: "Period end", type: "text", required: true, placeholder: "YYYY-MM-DD" },
        { name: "period.payDate", label: "Pay date", type: "text", required: true, placeholder: "YYYY-MM-DD" },
        { name: "type", label: "Type", type: "select", options: ["REGULAR", "ADJUSTMENT"] },
      ],
      description: "Create a payroll run for a period (empty employee scope = all eligible).",
    },
    {
      id: "compute-run",
      label: "Compute payroll run",
      icon: "Calculator",
      method: "POST",
      path: "/payroll/runs/{id}/compute",
      result: "detail",
      inputs: [{ name: "id", label: "Payroll run id", type: "text", required: true }],
    },
    {
      id: "finalize-run",
      label: "Finalize payroll run",
      icon: "Lock",
      method: "POST",
      path: "/payroll/runs/{id}/finalize",
      result: "detail",
      inputs: [{ name: "id", label: "Payroll run id", type: "text", required: true }],
      description: "Finalize a computed run (idempotent — a repeat is a safe no-op).",
    },
    {
      id: "cancel-run",
      label: "Cancel payroll run",
      icon: "XCircle",
      method: "POST",
      path: "/payroll/runs/{id}/cancel",
      result: "detail",
      inputs: [{ name: "id", label: "Payroll run id", type: "text", required: true }],
    },
    // NOTE: add-loan (POST /employees/{id}/loans) and create-deduction-rule (POST /deduction-rules)
    // have complex nested/list request bodies (loan schedule; rule computation/base + rows) — deferred
    // to a follow-up rather than shipped with an approximate form.
    // --- Outputs (US5) ---
    {
      id: "payslips",
      label: "Payslips",
      icon: "ReceiptText",
      method: "GET",
      path: "/payslips?runId={runId}&employeeId={employeeId}",
      result: "table",
      inputs: [
        { name: "runId", label: "Payroll run id", type: "text" },
        { name: "employeeId", label: "Employee", type: "reference", source: EMPLOYEES_SOURCE },
      ],
      resultRefs: employeeLabels(["employeeId"]),
      description: "Per-employee earnings / deductions / net (filter by run and/or employee).",
    },
    {
      id: "remittances",
      label: "Statutory remittances",
      icon: "Landmark",
      method: "GET",
      path: "/payroll/runs/{id}/remittances",
      result: "detail",
      inputs: [{ name: "id", label: "Payroll run id", type: "text", required: true }],
      description: "Per-contribution statutory totals (SSS / PhilHealth / Pag-IBIG / BIR) for a run.",
    },
  ],
};

// Exported for use by later stories' functions (kept referenced to avoid unused-symbol churn as the
// manifest grows through US2–US5).
export { EMPLOYEES_SOURCE, LEAVE_TYPES_SOURCE, employeeLabels };
