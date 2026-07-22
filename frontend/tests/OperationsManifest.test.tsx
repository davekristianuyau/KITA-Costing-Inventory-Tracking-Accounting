import { describe, it, expect, beforeEach, vi, type Mock } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import FunctionWorkspace from "../src/workspace/FunctionWorkspace";
import { ThemeProvider } from "../src/theme/ThemeProvider";
import { operationsManifest } from "../src/services/manifests/operations";
import { callEdge } from "../src/api/edge";
import type { ServiceFunction } from "../src/services/types";

vi.mock("../src/api/edge", () => ({ callEdge: vi.fn() }));
const edge = callEdge as unknown as Mock;

function fn(id: string): ServiceFunction {
  const f = operationsManifest.functions.find((x) => x.id === id);
  if (!f) throw new Error(`operations manifest missing function: ${id}`);
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
        <FunctionWorkspace service={operationsManifest} fn={fn(id)} />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const items = [
  { id: "id-1", sku: "A-1", name: "Widget", type: "FINISHED_GOOD" },
  { id: "id-2", sku: "B-2", name: "Gadget", type: "COMPONENT" },
];

beforeEach(() => edge.mockReset());

describe("Operations manifest — US1 catalog + stock", () => {
  it("items: lists catalog items in a table", async () => {
    const user = userEvent.setup();
    routeEdge({ "/api/operations/items": items });
    renderFn("items");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("A-1")).toBeInTheDocument();
    expect(within(table).getByText("Gadget")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/operations/items", undefined);
  });

  it("item: picks an item and shows its detail", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/operations/items": items,
      "/api/operations/items/id-1": { id: "id-1", sku: "A-1", name: "Widget", perishable: false },
    });
    renderFn("item");
    // reference picker sourced from the items list
    await user.click(await screen.findByText(/A-1 — Widget/));
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(edge).toHaveBeenCalledWith("GET", "/api/operations/items/id-1", undefined);
    expect(await screen.findByText("Widget")).toBeInTheDocument();
  });

  it("stock: shows onHand/reserved/available per location", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/operations/items": items,
      "/api/operations/items/id-1/availability": [
        { itemId: "id-1", locationId: "loc-1", onHand: 7, reserved: 2, available: 5 },
      ],
    });
    renderFn("stock");
    await user.click(await screen.findByText(/A-1 — Widget/));
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("7")).toBeInTheDocument();
    expect(within(table).getByText("5")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/operations/items/id-1/availability", undefined);
  });
});

describe("Operations manifest — US2 movements + locations", () => {
  it("movements: lists the ledger for a picked item", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/operations/items": items,
      "/api/operations/movements?itemId=id-1": [
        { id: "m1", itemId: "id-1", type: "ADJUSTMENT", quantity: 7 },
      ],
    });
    renderFn("movements");
    await user.click(await screen.findByText(/A-1 — Widget/));
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("ADJUSTMENT")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/operations/movements?itemId=id-1", undefined);
  });

  it("locations: lists the client's stock locations", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/operations/locations": [{ id: "loc-1", code: "WH1", name: "Warehouse 1" }],
    });
    renderFn("locations");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("WH1")).toBeInTheDocument();
    expect(within(table).getByText("Warehouse 1")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/operations/locations", undefined);
  });
});

describe("Operations manifest — US3 BOM explosion", () => {
  it("explodes a BOM into a flat requirements table", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/operations/items": items,
      "/api/operations/boms/id-1/explosion?quantity=2": [
        { componentItemId: "id-2", requiredQuantity: 4, uom: "ea" },
      ],
    });
    renderFn("bom-explosion");
    await user.click(await screen.findByText(/A-1 — Widget/));
    await user.type(screen.getByLabelText(/quantity/i), "2");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("4")).toBeInTheDocument();
    expect(within(table).getByText(/B-2 — Gadget/)).toBeInTheDocument(); // componentItemId resolved
    expect(edge).toHaveBeenCalledWith(
      "GET",
      "/api/operations/boms/id-1/explosion?quantity=2",
      undefined,
    );
  });

  it("shows a clear error for a cyclic BOM", async () => {
    const user = userEvent.setup();
    edge.mockImplementation((_m: string, url: string) =>
      url === "/api/operations/items"
        ? Promise.resolve({ ok: true, status: 200, data: items })
        : Promise.resolve({ ok: false, status: 409, data: null, error: "cycle detected" }),
    );
    renderFn("bom-explosion");
    await user.click(await screen.findByText(/A-1 — Widget/));
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/cycle detected/i);
  });
});

describe("Operations manifest — US4 writes + order/build/receipt reads", () => {
  it("create-item: blocks on missing required fields then POSTs the item", async () => {
    const user = userEvent.setup();
    edge.mockResolvedValue({ ok: true, status: 201, data: { id: "x", sku: "NEW" } });
    renderFn("create-item");

    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(screen.getByText(/sku is required/i)).toBeInTheDocument();
    expect(edge).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText(/^sku/i), "NEW");
    await user.type(screen.getByLabelText(/^name/i), "New Item");
    await user.selectOptions(screen.getByLabelText(/^type/i), "FINISHED_GOOD");
    await user.type(screen.getByLabelText(/base unit code/i), "ea");
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    expect(edge).toHaveBeenCalledWith(
      "POST",
      "/api/operations/items",
      expect.objectContaining({ sku: "NEW", name: "New Item", type: "FINISHED_GOOD", baseUom: "ea" }),
    );
  });

  it("sales-orders: lists created orders", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/operations/sales-orders": [{ id: "so1", customerRef: "CUST-1", status: "DRAFT" }],
    });
    renderFn("sales-orders");
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    const table = await screen.findByRole("table");
    expect(within(table).getByText("CUST-1")).toBeInTheDocument();
    expect(within(table).getByText("DRAFT")).toBeInTheDocument();
  });

  it("create-sales-order: submits a lines array built from the list input", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/operations/items": items,
      "/api/operations/sales-orders": { id: "so1", status: "DRAFT" },
    });
    renderFn("create-sales-order");

    await user.type(screen.getByLabelText(/customer reference/i), "CUST-1");
    await user.click(screen.getByRole("button", { name: /add row/i }));
    await user.click(await screen.findByText(/A-1 — Widget/)); // pick the row's item (reference)
    await user.type(screen.getByLabelText(/quantity/i), "3");
    await user.type(screen.getByLabelText(/unit price/i), "10");
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    expect(edge).toHaveBeenCalledWith(
      "POST",
      "/api/operations/sales-orders",
      expect.objectContaining({
        customerRef: "CUST-1",
        lines: [expect.objectContaining({ itemId: "id-1", quantity: "3", unitPrice: "10" })],
      }),
    );
  });
});

describe("Operations manifest — US5 costing", () => {
  it("cost: shows the cost/margin detail for a picked item", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/operations/items": items,
      "/api/operations/items/id-1/cost": { unitCost: "4.00", method: "AVCO", margin: "6.00" },
    });
    renderFn("cost");
    await user.click(await screen.findByText(/A-1 — Widget/));
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(await screen.findByText("AVCO")).toBeInTheDocument();
    expect(screen.getByText("6.00")).toBeInTheDocument();
    // optional salePrice left blank → dropped from the query
    expect(edge).toHaveBeenCalledWith("GET", "/api/operations/items/id-1/cost", undefined);
  });
});
