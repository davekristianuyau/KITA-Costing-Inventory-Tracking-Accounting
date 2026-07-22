import { describe, it, expect, beforeEach, vi, type Mock } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ListInput from "../src/workspace/inputs/ListInput";
import { callEdge } from "../src/api/edge";
import type { InputField } from "../src/services/types";

vi.mock("../src/api/edge", () => ({ callEdge: vi.fn() }));
const edge = callEdge as unknown as Mock;

const fields: InputField[] = [
  { name: "itemId", label: "Item", type: "text", required: true },
  { name: "quantity", label: "Quantity", type: "number", required: true },
];

beforeEach(() => edge.mockReset());

describe("ListInput", () => {
  it("has no rows initially and adds one on Add", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<ListInput id="lines" label="Lines" fields={fields} value={[]} onChange={onChange} />);

    expect(screen.queryByLabelText("Item")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /add/i }));
    expect(onChange).toHaveBeenLastCalledWith([{ itemId: "", quantity: "" }]);
  });

  it("renders one group of nested fields per row", () => {
    render(
      <ListInput
        id="lines"
        label="Lines"
        fields={fields}
        value={[{ itemId: "id-1", quantity: "2" }]}
        onChange={vi.fn()}
      />,
    );
    expect(screen.getByLabelText(/item/i)).toHaveValue("id-1");
    expect(screen.getByLabelText(/quantity/i)).toHaveValue(2);
  });

  it("edits a row cell and reports the updated array", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <ListInput
        id="lines"
        label="Lines"
        fields={fields}
        value={[{ itemId: "", quantity: "" }]}
        onChange={onChange}
      />,
    );
    await user.type(screen.getByLabelText(/item/i), "X");
    expect(onChange).toHaveBeenLastCalledWith([{ itemId: "X", quantity: "" }]);
  });

  it("removes a row on Remove", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <ListInput
        id="lines"
        label="Lines"
        fields={fields}
        value={[{ itemId: "a", quantity: "1" }]}
        onChange={onChange}
      />,
    );
    await user.click(screen.getByRole("button", { name: /remove/i }));
    expect(onChange).toHaveBeenLastCalledWith([]);
  });
});
