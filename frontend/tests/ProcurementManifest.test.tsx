import { describe, it, expect, beforeEach, vi, type Mock } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import FunctionWorkspace from "../src/workspace/FunctionWorkspace";
import { ThemeProvider } from "../src/theme/ThemeProvider";
import { procurementManifest } from "../src/services/manifests/procurement";
import { callEdge } from "../src/api/edge";
import type { ServiceFunction } from "../src/services/types";

vi.mock("../src/api/edge", () => ({ callEdge: vi.fn() }));
const edge = callEdge as unknown as Mock;

function fn(id: string): ServiceFunction {
  const f = procurementManifest.functions.find((x) => x.id === id);
  if (!f) throw new Error(`procurement manifest missing function: ${id}`);
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
        <FunctionWorkspace service={procurementManifest} fn={fn(id)} />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const suppliers = [
  { id: "s1", supplierCode: "SUP-1", name: "Acme Supply", status: "ACTIVE" },
  { id: "s2", supplierCode: "SUP-2", name: "Bolt Traders", status: "ACTIVE" },
];

beforeEach(() => edge.mockReset());

describe("Procurement manifest — US1 suppliers + purchase orders", () => {
  it("suppliers: lists the supplier master in a table", async () => {
    const user = userEvent.setup();
    routeEdge({ "/api/procurement/suppliers": suppliers });
    renderFn("suppliers");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("SUP-1")).toBeInTheDocument();
    expect(within(table).getByText("Bolt Traders")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/procurement/suppliers", undefined);
  });

  it("supplier: picks a supplier and shows detail", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/procurement/suppliers": suppliers,
      "/api/procurement/suppliers/s1": { id: "s1", supplierCode: "SUP-1", name: "Acme Supply", paymentTerms: "NET30" },
    });
    renderFn("supplier");
    await user.click(await screen.findByText(/SUP-1 — Acme Supply/));
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(edge).toHaveBeenCalledWith("GET", "/api/procurement/suppliers/s1", undefined);
    expect(await screen.findByText("NET30")).toBeInTheDocument();
  });

  it("purchase-orders: lists POs and resolves the supplier column", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/procurement/suppliers": suppliers,
      "/api/procurement/purchase-orders": [
        { id: "po1", poNo: "PO-001", supplierId: "s1", status: "DRAFT", orderTotal: "1000" },
      ],
    });
    renderFn("purchase-orders");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("PO-001")).toBeInTheDocument();
    expect(within(table).getByText(/SUP-1 — Acme Supply/)).toBeInTheDocument(); // supplierId resolved
    expect(edge).toHaveBeenCalledWith("GET", "/api/procurement/purchase-orders", undefined);
  });

  it("purchase-order: shows a PO's lines as a nested sub-table + status", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/procurement/suppliers": suppliers,
      "/api/procurement/purchase-orders/po1": {
        id: "po1",
        poNo: "PO-001",
        supplierId: "s1",
        status: "APPROVED",
        orderTotal: "1000",
        lines: [{ itemRef: "WIDGET", qtyOrdered: "10", qtyReceived: "0", agreedPrice: "100" }],
      },
    });
    renderFn("purchase-order");
    await user.type(screen.getByLabelText(/purchase order id/i), "po1");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    // scalar field + the lines[] rendered as a sub-table (014 detail sub-table)
    expect(await screen.findByText("APPROVED")).toBeInTheDocument();
    const table = await screen.findByRole("table");
    expect(within(table).getByText("WIDGET")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/procurement/purchase-orders/po1", undefined);
  });
});
