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

describe("Procurement manifest — US2 reorder suggestions", () => {
  it("lists suggestions and resolves the supplier column", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/procurement/suppliers": suppliers,
      "/api/procurement/restock/suggestions": [{ id: "r1", supplierId: "s1", status: "OPEN" }],
    });
    renderFn("reorder-suggestions");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText(/SUP-1 — Acme Supply/)).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/procurement/restock/suggestions", undefined);
  });

  it("shows a clear empty state when there are no suggestions", async () => {
    const user = userEvent.setup();
    routeEdge({ "/api/procurement/restock/suggestions": [] });
    renderFn("reorder-suggestions");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(await screen.findByText(/no results/i)).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
});

describe("Procurement manifest — US3 supplier + PO writes", () => {
  it("create-supplier: blocks on missing required fields then POSTs", async () => {
    const user = userEvent.setup();
    edge.mockResolvedValue({ ok: true, status: 201, data: { id: "s9", supplierCode: "SUP-9" } });
    renderFn("create-supplier");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(screen.getByText(/supplier code is required/i)).toBeInTheDocument();
    expect(edge).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText(/supplier code/i), "SUP-9");
    await user.type(screen.getByLabelText(/^name/i), "New Vendor");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(edge).toHaveBeenCalledWith(
      "POST",
      "/api/procurement/suppliers",
      expect.objectContaining({ supplierCode: "SUP-9", name: "New Vendor" }),
    );
  });

  it("create-po: submits a lines array built from the list input", async () => {
    const user = userEvent.setup();
    edge.mockImplementation((_m: string, url: string) =>
      url === "/api/procurement/suppliers"
        ? Promise.resolve({ ok: true, status: 200, data: suppliers })
        : Promise.resolve({ ok: true, status: 201, data: { id: "po9", status: "DRAFT" } }),
    );
    renderFn("create-po");
    await user.click(await screen.findByText(/SUP-1 — Acme Supply/));
    await user.click(screen.getByRole("button", { name: /add row/i }));
    await user.type(screen.getByLabelText(/item ref/i), "WIDGET");
    await user.type(screen.getByLabelText(/qty ordered/i), "5");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(edge).toHaveBeenCalledWith(
      "POST",
      "/api/procurement/purchase-orders",
      expect.objectContaining({
        supplierId: "s1",
        lines: [expect.objectContaining({ itemRef: "WIDGET", qtyOrdered: "5" })],
      }),
    );
  });
});

describe("Procurement manifest — US4 receiving", () => {
  it("receive-po: submits a {lines:[...]} body to the receipts endpoint", async () => {
    const user = userEvent.setup();
    edge.mockResolvedValue({
      ok: true,
      status: 201,
      data: { id: "gr1", purchaseOrderId: "po1", orderStatus: "PARTIALLY_RECEIVED", lines: [] },
    });
    renderFn("receive-po");
    await user.type(screen.getByLabelText(/purchase order id/i), "po1");
    await user.click(screen.getByRole("button", { name: /add row/i }));
    await user.type(screen.getByLabelText(/item ref/i), "WIDGET");
    await user.type(screen.getByLabelText(/qty received/i), "3");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(edge).toHaveBeenCalledWith(
      "POST",
      "/api/procurement/purchase-orders/po1/receipts",
      expect.objectContaining({
        lines: [expect.objectContaining({ itemRef: "WIDGET", qtyReceived: "3" })],
      }),
    );
    expect(await screen.findByText("PARTIALLY_RECEIVED")).toBeInTheDocument();
  });

  it("po-receipts: reads the receipts recorded against a PO", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/procurement/purchase-orders/po1/receipts": [
        { id: "gr1", purchaseOrderId: "po1", orderStatus: "FULLY_RECEIVED" },
      ],
    });
    renderFn("po-receipts");
    await user.type(screen.getByLabelText(/purchase order id/i), "po1");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("FULLY_RECEIVED")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/procurement/purchase-orders/po1/receipts", undefined);
  });
});
