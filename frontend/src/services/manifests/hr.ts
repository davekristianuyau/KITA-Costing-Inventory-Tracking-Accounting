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
  ],
};

// Exported for use by later stories' functions (kept referenced to avoid unused-symbol churn as the
// manifest grows through US2–US5).
export { EMPLOYEES_SOURCE, LEAVE_TYPES_SOURCE, employeeLabels };
