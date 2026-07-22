import { describe, it, expect, beforeEach, vi, type Mock } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import App from "../src/App";
import FunctionWorkspace from "../src/workspace/FunctionWorkspace";
import { AuthProvider } from "../src/auth/AuthContext";
import { ThemeProvider } from "../src/theme/ThemeProvider";
import { callEdge } from "../src/api/edge";
import type { ServiceFunction, ServiceManifest } from "../src/services/types";

// The workspace runs manifest functions through a generic authenticated edge call (not the typed /auth client).
vi.mock("../src/api/edge", () => ({ callEdge: vi.fn() }));
const edge = callEdge as unknown as Mock;

const svc: ServiceManifest = {
  id: "demo",
  label: "Demo",
  icon: "Box",
  basePath: "/api/demo",
  functions: [],
};

function renderWorkspace(fn: ServiceFunction) {
  return render(
    <ThemeProvider>
      <MemoryRouter>
        <FunctionWorkspace service={svc} fn={fn} />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

function renderApp(path: string) {
  sessionStorage.setItem("kita.client", "acme");
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  edge.mockReset();
  sessionStorage.clear();
  localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
});

describe("Sidebar (service functions)", () => {
  it("lists the selected service's functions and links each to its route", () => {
    renderApp("/app/operations");
    // operations' reference function is present in the left pane
    expect(screen.getByRole("link", { name: /items/i })).toHaveAttribute(
      "href",
      "/app/operations/items",
    );
  });
});

describe("FunctionWorkspace", () => {
  const listFn: ServiceFunction = {
    id: "items",
    label: "Items",
    method: "GET",
    path: "/items",
    result: "table",
  };

  it("runs a function and renders a table result with a loading state first", async () => {
    const user = userEvent.setup();
    let resolve!: (v: unknown) => void;
    edge.mockReturnValue(new Promise((r) => (resolve = r)));

    renderWorkspace(listFn);
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    expect(screen.getByRole("status")).toHaveTextContent(/running/i);

    resolve({ ok: true, status: 200, data: [{ sku: "A-1", name: "Widget" }] });

    const table = await screen.findByRole("table");
    expect(within(table).getByText("A-1")).toBeInTheDocument();
    expect(within(table).getByText("Widget")).toBeInTheDocument();
    expect(edge).toHaveBeenCalledWith("GET", "/api/demo/items", undefined);
  });

  it("shows a clear error when the edge call fails", async () => {
    const user = userEvent.setup();
    edge.mockResolvedValue({ ok: false, status: 502, data: null, error: "Bad gateway" });

    renderWorkspace(listFn);
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/bad gateway|502|error/i);
  });

  it("blocks the call and shows inline validation when a required input is empty", async () => {
    const user = userEvent.setup();
    const byId: ServiceFunction = {
      id: "item",
      label: "Item by id",
      method: "GET",
      path: "/items/{id}",
      result: "detail",
      inputs: [{ name: "id", label: "Item ID", type: "text", required: true }],
    };
    renderWorkspace(byId);
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    expect(edge).not.toHaveBeenCalled();
    expect(screen.getByText(/item id is required/i)).toBeInTheDocument();

    // fill it and the path param is substituted
    await user.type(screen.getByLabelText(/item id/i), "42");
    edge.mockResolvedValue({ ok: true, status: 200, data: { id: 42, name: "Widget" } });
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(edge).toHaveBeenCalledWith("GET", "/api/demo/items/42", undefined);
  });

  it("swaps content when a different function is rendered (no reload)", async () => {
    const { rerender } = renderWorkspace(listFn);
    expect(screen.getByRole("heading", { name: /items/i })).toBeInTheDocument();

    const other: ServiceFunction = {
      id: "boms",
      label: "Bills of Material",
      method: "GET",
      path: "/boms",
      result: "json",
    };
    rerender(
      <ThemeProvider>
        <MemoryRouter>
          <FunctionWorkspace service={svc} fn={other} />
        </MemoryRouter>
      </ThemeProvider>,
    );
    expect(screen.getByRole("heading", { name: /bills of material/i })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /^items$/i })).not.toBeInTheDocument();
  });
});

// Route the mocked edge by URL so both the reference/resultRef source loads and the function call resolve.
function routeEdge(map: Record<string, unknown>) {
  edge.mockImplementation((_method: string, url: string) =>
    Promise.resolve({ ok: true, status: 200, data: map[url] ?? null }),
  );
}

const items = [
  { id: "id-1", sku: "A-1", name: "Widget" },
  { id: "id-2", sku: "B-2", name: "Gadget" },
];

describe("Framework extensions (012): reference input + id→label", () => {
  it("renders a reference picker sourced from a list endpoint and blocks Run when required + empty", async () => {
    const user = userEvent.setup();
    routeEdge({ "/api/operations/items": items });
    const fn: ServiceFunction = {
      id: "item",
      label: "Item detail",
      method: "GET",
      path: "/items/{id}",
      result: "detail",
      inputs: [
        {
          name: "id",
          label: "Item",
          type: "reference",
          required: true,
          source: { path: "/api/operations/items", valueKey: "id", labelKeys: ["sku", "name"] },
        },
      ],
    };
    renderWorkspace(fn);

    // options came from the source endpoint, shown by label (not raw id)
    expect(await screen.findByText(/A-1 — Widget/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(screen.getByText(/item is required/i)).toBeInTheDocument();
    // the function itself was not called (only the source list load happened)
    expect(edge.mock.calls.some((c) => String(c[1]).startsWith("/api/demo/items/"))).toBe(false);

    // pick an option → Run substitutes the id into the path
    await user.click(screen.getByText(/B-2 — Gadget/));
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(edge).toHaveBeenCalledWith("GET", "/api/demo/items/id-2", undefined);
  });

  it("resolves item-id result columns to SKU — name via resultRefs", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/operations/items": items,
      "/api/demo/movements": [{ itemId: "id-1", type: "ADJUSTMENT", quantity: 7 }],
    });
    const fn: ServiceFunction = {
      id: "movements",
      label: "Movements",
      method: "GET",
      path: "/movements",
      result: "table",
      resultRefs: [
        {
          columns: ["itemId"],
          source: { path: "/api/operations/items", valueKey: "id", labelKeys: ["sku", "name"] },
        },
      ],
    };
    renderWorkspace(fn);
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    const table = await screen.findByRole("table");
    expect(within(table).getByText(/A-1 — Widget/)).toBeInTheDocument();
    expect(within(table).queryByText("id-1")).not.toBeInTheDocument();
  });
});

describe("Framework extension (014): detail sub-table for nested arrays", () => {
  const quoteFn: ServiceFunction = {
    id: "quote",
    label: "Quote",
    method: "POST",
    path: "/quote",
    result: "detail",
    inputs: [],
  };

  it("renders an array-of-objects field as a nested sub-table and scalars normally", async () => {
    const user = userEvent.setup();
    edge.mockResolvedValue({
      ok: true,
      status: 200,
      data: {
        baseTotal: "1000",
        finalPrice: "850",
        breakdown: [
          { tierCode: "VIP", origin: "TIER", amountRemoved: "100" },
          { tierCode: "SENIOR", origin: "STATUTORY", amountRemoved: "50" },
        ],
        flags: ["capped", "vat-inclusive"],
      },
    });
    renderWorkspace(quoteFn);
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    // scalar fields render as key/value
    expect(await screen.findByText("850")).toBeInTheDocument();
    // the breakdown array-of-objects renders as a sub-table with its rows
    const table = await screen.findByRole("table");
    expect(within(table).getByText("VIP")).toBeInTheDocument();
    expect(within(table).getByText("SENIOR")).toBeInTheDocument();
    expect(within(table).getByText("100")).toBeInTheDocument();
    // the array-of-scalars flags render as a joined list (not a table row)
    expect(screen.getByText(/capped/)).toHaveTextContent(/capped.*vat-inclusive/);
  });

  it("leaves a single-level detail object unchanged", async () => {
    const user = userEvent.setup();
    edge.mockResolvedValue({ ok: true, status: 200, data: { mode: "CASCADE", active: true } });
    renderWorkspace(quoteFn);
    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(await screen.findByText("CASCADE")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
});
