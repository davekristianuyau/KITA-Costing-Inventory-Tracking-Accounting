import { describe, it, expect, beforeEach, vi, type Mock } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ReferenceInput from "../src/workspace/inputs/ReferenceInput";
import { callEdge } from "../src/api/edge";
import type { ReferenceSource } from "../src/services/types";

vi.mock("../src/api/edge", () => ({ callEdge: vi.fn() }));
const edge = callEdge as unknown as Mock;

const source: ReferenceSource = {
  path: "/api/operations/items",
  valueKey: "id",
  labelKeys: ["sku", "name"],
};

const rows = [
  { id: "id-1", sku: "A-1", name: "Widget" },
  { id: "id-2", sku: "B-2", name: "Gadget" },
  { id: "id-3", sku: "C-3", name: "Gizmo" },
];

beforeEach(() => edge.mockReset());

function setup(props: Partial<React.ComponentProps<typeof ReferenceInput>> = {}) {
  const onChange = vi.fn();
  render(
    <ReferenceInput
      id="ref"
      label="Item"
      source={source}
      value=""
      required
      onChange={onChange}
      {...props}
    />,
  );
  return { onChange };
}

describe("ReferenceInput", () => {
  it("loads options once from the source endpoint and lets the user pick by label", async () => {
    const user = userEvent.setup();
    edge.mockResolvedValue({ ok: true, status: 200, data: rows });
    const { onChange } = setup();

    // options are labelled "sku — name", not raw ids
    expect(await screen.findByText(/A-1 — Widget/)).toBeInTheDocument();
    expect(edge).toHaveBeenCalledTimes(1);
    expect(edge).toHaveBeenCalledWith("GET", "/api/operations/items", undefined);

    await user.click(screen.getByText(/B-2 — Gadget/));
    expect(onChange).toHaveBeenLastCalledWith("id-2"); // submits the underlying value (id)
  });

  it("filters options client-side as the user types (type-ahead, no refetch)", async () => {
    const user = userEvent.setup();
    edge.mockResolvedValue({ ok: true, status: 200, data: rows });
    setup();

    const search = await screen.findByRole("searchbox");
    await user.type(search, "giz");
    expect(screen.getByText(/C-3 — Gizmo/)).toBeInTheDocument();
    expect(screen.queryByText(/A-1 — Widget/)).not.toBeInTheDocument();
    expect(edge).toHaveBeenCalledTimes(1); // filtering did not refetch
  });

  it("shows a clear error with retry when the load fails", async () => {
    const user = userEvent.setup();
    edge.mockResolvedValueOnce({ ok: false, status: 502, data: null, error: "Bad gateway" });
    setup();

    expect(await screen.findByRole("alert")).toHaveTextContent(/bad gateway|error|unavailable/i);
    edge.mockResolvedValueOnce({ ok: true, status: 200, data: rows });
    await user.click(screen.getByRole("button", { name: /retry/i }));
    expect(await screen.findByText(/A-1 — Widget/)).toBeInTheDocument();
  });
});
