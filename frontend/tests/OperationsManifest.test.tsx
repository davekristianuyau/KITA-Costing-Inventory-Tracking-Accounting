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
