import { describe, it, expect, beforeEach, vi, type Mock } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import FunctionWorkspace from "../src/workspace/FunctionWorkspace";
import { ThemeProvider } from "../src/theme/ThemeProvider";
import { workflowManifest } from "../src/services/manifests/workflow";
import { callEdge } from "../src/api/edge";
import type { ServiceFunction } from "../src/services/types";

vi.mock("../src/api/edge", () => ({ callEdge: vi.fn() }));
const edge = callEdge as unknown as Mock;

function fn(id: string): ServiceFunction {
  const f = workflowManifest.functions.find((x) => x.id === id);
  if (!f) throw new Error(`workflow manifest missing function: ${id}`);
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
        <FunctionWorkspace service={workflowManifest} fn={fn(id)} />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const activity = [
  {
    id: "a1",
    actorEmployeeId: "emp-sales",
    action: "TAKE_SALES_ORDER",
    outcome: "SUCCESS",
    reason: null,
    targetRef: "sales-order:so-1",
    makerEmployeeId: null,
    retryCount: 0,
    at: "2026-07-22T02:10:04Z",
  },
  {
    id: "a2",
    actorEmployeeId: "emp-whse",
    action: "RAISE_PURCHASE_ORDER",
    outcome: "REJECTED_NOT_PERMITTED",
    reason: "role WAREHOUSE_STAFF may not perform RAISE_PURCHASE_ORDER",
    targetRef: null,
    makerEmployeeId: null,
    retryCount: 0,
    at: "2026-07-22T02:09:00Z",
  },
];

beforeEach(() => edge.mockReset());

describe("Workflow manifest — US1 activity log", () => {
  it("activity: lists the audit trail with actor, action, outcome and time", async () => {
    const user = userEvent.setup();
    routeEdge({ "/api/workflow/activity": activity });
    renderFn("activity");
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    const table = await screen.findByRole("table");
    expect(within(table).getByText("emp-sales")).toBeInTheDocument();
    expect(within(table).getByText("REJECTED_NOT_PERMITTED")).toBeInTheDocument();
    expect(within(table).getByText("sales-order:so-1")).toBeInTheDocument();
    // no filters entered → no query string at all
    expect(edge).toHaveBeenCalledWith("GET", "/api/workflow/activity", undefined);
  });

  it("activity: sends only the filters that were set, as wire tokens", async () => {
    const user = userEvent.setup();
    routeEdge({ "/api/workflow/activity?outcome=REJECTED_INVALID": [] });
    renderFn("activity");

    await user.selectOptions(screen.getByLabelText(/outcome/i), "REJECTED_INVALID");
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    // blank actor/action/from/to are stripped; the outcome value is the backend enum token
    expect(edge).toHaveBeenCalledWith(
      "GET",
      "/api/workflow/activity?outcome=REJECTED_INVALID",
      undefined,
    );
  });

  it("activity: an empty result is an empty state, not an error", async () => {
    const user = userEvent.setup();
    routeEdge({ "/api/workflow/activity": [] });
    renderFn("activity");
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    expect(await screen.findByText(/no results/i)).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("activity: offers every action and outcome as a filter option", () => {
    renderFn("activity");
    const actions = screen.getByLabelText(/action/i);
    expect(within(actions).getAllByRole("option").length).toBeGreaterThanOrEqual(12);
    const outcomes = screen.getByLabelText(/outcome/i);
    expect(within(outcomes).getByRole("option", { name: "FAILED_UNAVAILABLE" })).toBeInTheDocument();
  });
});

describe("Workflow manifest — US2 authorization rules + pending reviews", () => {
  it("authorization: lists each role → action → kind grant", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/workflow/authorization": [
        { action: "TAKE_SALES_ORDER", role: "SALES", kind: "MAKER" },
        { action: "CONFIRM_SALES_PAYMENT", role: "CASHIER", kind: "CHECKER" },
      ],
    });
    renderFn("authorization");
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    const table = await screen.findByRole("table");
    expect(within(table).getByText("MAKER")).toBeInTheDocument();
    expect(within(table).getByText("CHECKER")).toBeInTheDocument();
    expect(within(table).getByText("CASHIER")).toBeInTheDocument();
  });

  it("pending-reviews: lists what awaits a checker, with its maker and target", async () => {
    const user = userEvent.setup();
    routeEdge({
      "/api/workflow/pending-reviews": [
        {
          pendingId: "pr-1",
          action: "RECORD_DELIVERY_RECEIPT",
          makerEmployeeId: "emp-whse",
          targetRef: "po:po-1",
          stage: null,
          createdAt: "2026-07-22T02:12:41Z",
        },
      ],
    });
    renderFn("pending-reviews");
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    const table = await screen.findByRole("table");
    expect(within(table).getByText("emp-whse")).toBeInTheDocument();
    expect(within(table).getByText("po:po-1")).toBeInTheDocument();
  });

  it("pending-reviews: an empty queue is an empty state, not an error", async () => {
    const user = userEvent.setup();
    routeEdge({ "/api/workflow/pending-reviews": [] });
    renderFn("pending-reviews");
    await user.click(screen.getByRole("button", { name: /^run$/i }));

    expect(await screen.findByText(/no results/i)).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("Workflow manifest — FR-013 guard", () => {
  it("lets no governed action carry an acting-employee input", () => {
    // The actor is the signed-in user; the edge sets it and strips anything the browser sends.
    // If this fails, someone added an impersonation vector to a write. (Filtering a READ by actor is
    // fine and is not an identity claim — `activity?actor=` is a query, not a caller assertion.)
    const offenders = workflowManifest.functions
      .filter((f) => f.method !== "GET")
      .flatMap((f) =>
        (f.inputs ?? [])
          .filter((i) =>
            /^(actor|acting.*|.*actingEmployee.*|employee|employeeId|actorEmployeeId)$/i.test(i.name),
          )
          .map((i) => `${f.id}.${i.name}`),
      );
    expect(offenders).toEqual([]);
  });
});
