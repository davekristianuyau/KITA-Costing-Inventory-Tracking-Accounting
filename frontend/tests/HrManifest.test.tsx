import { describe, it, expect, beforeEach, vi, type Mock } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import FunctionWorkspace from "../src/workspace/FunctionWorkspace";
import { ThemeProvider } from "../src/theme/ThemeProvider";
import { hrManifest } from "../src/services/manifests/hr";
import { callEdge } from "../src/api/edge";
import type { ServiceFunction } from "../src/services/types";

vi.mock("../src/api/edge", () => ({ callEdge: vi.fn() }));
const edge = callEdge as unknown as Mock;

function fn(id: string): ServiceFunction {
  const f = hrManifest.functions.find((x) => x.id === id);
  if (!f) throw new Error(`hr manifest missing function: ${id}`);
  return f;
}

function routeEdge(map: Record<string, unknown>) {
  edge.mockImplementation((_m: string, url: string) =>
    Promise.resolve({ ok: true, status: 200, data: map[url] ?? null }),
  );
}

function renderFn(id: string) {
  return render(
    <ThemeProvider>
      <MemoryRouter>
        <FunctionWorkspace service={hrManifest} fn={fn(id)} />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const employees = [
  { id: "e1", employeeNo: "E-001", firstName: "Alice", lastName: "Cruz", status: "ACTIVE" },
  { id: "e2", employeeNo: "E-002", firstName: "Bob", lastName: "Reyes", status: "ACTIVE" },
];

beforeEach(() => edge.mockReset());

describe("HR manifest — US1 employees + compensation", () => {
  it("employees: lists the workforce in a table", async () => {
    const user = userEvent.setup();
    routeEdge({ "/api/hr/employees": employees });
    renderFn("employees");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("E-001")).toBeInTheDocument();
    expect(within(table).getByText("Reyes")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/hr/employees", undefined);
  });

  it("employee: picks an employee and shows their detail", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/hr/employees": employees,
      "/api/hr/employees/e1": { id: "e1", employeeNo: "E-001", firstName: "Alice", position: "Clerk" },
    });
    renderFn("employee");
    await user.click(await screen.findByText(/E-001 Alice Cruz/));
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(edge).toHaveBeenCalledWith("GET", "/api/hr/employees/e1", undefined);
    expect(await screen.findByText("Clerk")).toBeInTheDocument();
  });

  it("compensation: shows effective-dated rates for a picked employee", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/hr/employees": employees,
      "/api/hr/employees/e1/compensation": [{ rate: "50000", effectiveDate: "2026-01-01" }],
    });
    renderFn("compensation");
    await user.click(await screen.findByText(/E-001 Alice Cruz/));
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("50000")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/hr/employees/e1/compensation", undefined);
  });
});

describe("HR manifest — US2 attendance + leave", () => {
  it("worked-time: renders the premium breakdown for a picked employee + period", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/hr/employees": employees,
      "/api/hr/attendance/worked-time?employeeId=e1&start=2026-08-01&end=2026-08-15": {
        regularHours: 80,
        overtimeHours: 4,
        nightDiffHours: 2,
      },
    });
    renderFn("worked-time");
    await user.click(await screen.findByText(/E-001 Alice Cruz/));
    await user.type(screen.getByLabelText(/start date/i), "2026-08-01");
    await user.type(screen.getByLabelText(/end date/i), "2026-08-15");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(await screen.findByText("80")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith(
      "GET",
      "/api/hr/attendance/worked-time?employeeId=e1&start=2026-08-01&end=2026-08-15",
      undefined,
    );
  });

  it("leave-requests: lists requests and resolves the employee column", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/hr/employees": employees,
      "/api/hr/leave/requests": [{ id: "lr1", employeeId: "e1", status: "FILED", duration: 2 }],
    });
    renderFn("leave-requests");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("FILED")).toBeInTheDocument();
    expect(within(table).getByText(/E-001 Alice Cruz/)).toBeInTheDocument(); // employeeId resolved
    expect(edge).toHaveBeenCalledWith("GET", "/api/hr/leave/requests", undefined);
  });

  it("leave-request: opens one request in detail", async () => {
    const user = userEvent.setup();
    routeEdge({ "/api/hr/leave/requests/lr1": { id: "lr1", status: "APPROVED", duration: 2 } });
    renderFn("leave-request");
    await user.type(screen.getByLabelText(/leave request id/i), "lr1");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(await screen.findByText("APPROVED")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/hr/leave/requests/lr1", undefined);
  });
});

describe("HR manifest — US3 payroll runs + register", () => {
  it("payroll-runs: lists runs with period + state", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/hr/payroll/runs": [
        { id: "run1", status: "COMPUTED", periodStart: "2026-03-01", periodEnd: "2026-03-31" },
      ],
    });
    renderFn("payroll-runs");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("COMPUTED")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/hr/payroll/runs", undefined);
  });

  it("register: shows per-employee totals for a run", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/hr/payroll/runs/run1/register": {
        runId: "run1",
        employeeCount: 1,
        totalGross: "50000",
        totalNet: "42000",
      },
    });
    renderFn("register");
    await user.type(screen.getByLabelText(/payroll run id/i), "run1");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(await screen.findByText("42000")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/hr/payroll/runs/run1/register", undefined);
  });
});
