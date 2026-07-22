import { describe, it, expect, beforeEach, vi, type Mock } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import FunctionWorkspace from "../src/workspace/FunctionWorkspace";
import { ThemeProvider } from "../src/theme/ThemeProvider";
import { callEdge } from "../src/api/edge";
import type { ServiceFunction, ServiceManifest } from "../src/services/types";

vi.mock("../src/api/edge", () => ({ callEdge: vi.fn() }));
const edge = callEdge as unknown as Mock;

const svc: ServiceManifest = {
  id: "workflow",
  label: "Workflow",
  icon: "Workflow",
  basePath: "/api/workflow",
  functions: [],
};

const action: ServiceFunction = {
  id: "confirm",
  label: "Confirm payment",
  method: "POST",
  path: "/sales-orders/{id}/confirm-payment",
  result: "outcome",
  inputs: [{ name: "id", label: "Sales order id", type: "text", required: true }],
};

async function run(result: unknown) {
  const user = userEvent.setup();
  edge.mockResolvedValue(result);
  render(
    <ThemeProvider>
      <MemoryRouter>
        <FunctionWorkspace service={svc} fn={action} />
      </MemoryRouter>
    </ThemeProvider>,
  );
  await user.type(screen.getByLabelText(/sales order id/i), "so-1");
  await user.click(screen.getByRole("button", { name: /^run$/i }));
}

beforeEach(() => edge.mockReset());

// The four-outcome taxonomy (FR-008, SC-004). The UI decides nothing — it renders what the backend
// returned. Above all: rejected-invalid (incl. self-review) must never read as not-permitted.
describe("Outcome view — the back-office taxonomy", () => {
  it("approved: shows a success outcome and the returned detail", async () => {
    await run({ ok: true, status: 200, data: { state: "PAYMENT_CONFIRMED" } });

    expect(await screen.findByText(/approved/i)).toBeInTheDocument();
    expect(screen.getByText("PAYMENT_CONFIRMED")).toBeInTheDocument();
  });

  it("rejected-invalid: shows the self-review reason as an invalid result", async () => {
    await run({
      ok: false,
      status: 422,
      data: { outcome: "REJECTED_INVALID", reason: "self review not allowed", status: "UNPROCESSABLE_ENTITY" },
      outcome: "REJECTED_INVALID",
      error: "self review not allowed",
    });

    expect(await screen.findByText(/rejected.*invalid/i)).toBeInTheDocument();
    expect(screen.getByText(/self review not allowed/i)).toBeInTheDocument();
  });

  it("not-permitted: shows a distinct denial", async () => {
    await run({
      ok: false,
      status: 403,
      data: { outcome: "REJECTED_NOT_PERMITTED", reason: "role SALES may not check", status: "FORBIDDEN" },
      outcome: "REJECTED_NOT_PERMITTED",
      error: "role SALES may not check",
    });

    expect(await screen.findByText(/not permitted/i)).toBeInTheDocument();
    expect(screen.getByText(/role SALES may not check/i)).toBeInTheDocument();
  });

  it("unavailable: shows a retryable temporary failure", async () => {
    await run({
      ok: false,
      status: 503,
      data: { outcome: "FAILED_UNAVAILABLE", reason: "operations-service 503", status: "SERVICE_UNAVAILABLE" },
      outcome: "FAILED_UNAVAILABLE",
      error: "operations-service 503",
    });

    expect(await screen.findByText(/temporarily unavailable/i)).toBeInTheDocument();
    expect(screen.getByText(/operations-service 503/i)).toBeInTheDocument();
  });

  it("SC-004: a self-review and a permission refusal do not render the same", async () => {
    await run({
      ok: false,
      status: 422,
      data: { outcome: "REJECTED_INVALID", reason: "self review not allowed" },
      outcome: "REJECTED_INVALID",
      error: "self review not allowed",
    });
    const invalid = (await screen.findByRole("alert")).textContent ?? "";

    cleanup();
    await run({
      ok: false,
      status: 403,
      data: { outcome: "REJECTED_NOT_PERMITTED", reason: "role SALES may not check" },
      outcome: "REJECTED_NOT_PERMITTED",
      error: "role SALES may not check",
    });
    const denied = (await screen.findByRole("alert")).textContent ?? "";

    expect(invalid).not.toEqual(denied);
    expect(invalid.toLowerCase()).not.toMatch(/not permitted/);
    expect(denied.toLowerCase()).not.toMatch(/invalid/);
  });

  it("an unmapped failure still falls back to the generic error banner", async () => {
    await run({ ok: false, status: 502, data: null, error: "Bad gateway" });

    expect(await screen.findByRole("alert")).toHaveTextContent(/bad gateway/i);
  });
});
