import { describe, it, expect, beforeEach, vi, type Mock } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import FunctionWorkspace from "../src/workspace/FunctionWorkspace";
import { ThemeProvider } from "../src/theme/ThemeProvider";
import { crmManifest } from "../src/services/manifests/crm";
import { callEdge } from "../src/api/edge";
import type { ServiceFunction } from "../src/services/types";

vi.mock("../src/api/edge", () => ({ callEdge: vi.fn() }));
const edge = callEdge as unknown as Mock;

function fn(id: string): ServiceFunction {
  const f = crmManifest.functions.find((x) => x.id === id);
  if (!f) throw new Error(`crm manifest missing function: ${id}`);
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
        <FunctionWorkspace service={crmManifest} fn={fn(id)} />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const customers = [
  { id: "c1", customerCode: "CUST-1", name: "Acme Co", type: "BUSINESS", status: "ACTIVE" },
  { id: "c2", customerCode: "CUST-2", name: "Jane Dela Cruz", type: "INDIVIDUAL", status: "ACTIVE" },
];
const tiers = [{ id: "t1", code: "GOLD", name: "Gold" }];

beforeEach(() => edge.mockReset());

describe("CRM manifest — US1 customers + tiers", () => {
  it("customers: lists customers in a table", async () => {
    const user = userEvent.setup();
    routeEdge({ "/api/crm/customers": customers, "/api/crm/loyalty/tiers": tiers });
    renderFn("customers");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("CUST-1")).toBeInTheDocument();
    expect(within(table).getByText("Jane Dela Cruz")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/crm/customers", undefined);
  });

  it("customer: picks a customer and shows detail with the loyalty tier resolved", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/crm/customers": customers,
      "/api/crm/loyalty/tiers": tiers,
      "/api/crm/customers/c1": { id: "c1", customerCode: "CUST-1", name: "Acme Co", loyaltyTierId: "t1" },
    });
    renderFn("customer");
    await user.click(await screen.findByText(/CUST-1 — Acme Co/));
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(edge).toHaveBeenCalledWith("GET", "/api/crm/customers/c1", undefined);
    // loyaltyTierId resolved to the tier label
    expect(await screen.findByText(/GOLD — Gold/)).toBeInTheDocument();
  });

  it("entitlements: lists a customer's SENIOR/PWD eligibility", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/crm/customers": customers,
      "/api/crm/loyalty/tiers": tiers,
      "/api/crm/customers/c2/entitlements": [{ kind: "SENIOR", validFrom: "2026-01-01" }],
    });
    renderFn("entitlements");
    await user.click(await screen.findByText(/CUST-2 — Jane Dela Cruz/));
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("SENIOR")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/crm/customers/c2/entitlements", undefined);
  });
});
