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
