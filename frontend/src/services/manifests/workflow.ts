// Workflow (back-office) service manifest (spec 016, contracts/workflow-manifest.md). Surfaces the
// append-only activity log, the authorization rules, the pending maker-checker queue, and every governed
// action, reusing the 011–015 shared framework plus 016's `group` + "outcome" result kind.
//
// Two rules this file must keep:
//   • NO acting-employee input, ever (FR-013) — the actor is the signed-in user; the edge sets it and
//     strips anything the browser sends. A guard test in tests/WorkflowManifest.test.tsx enforces this.
//   • The UI never decides an outcome — it renders whatever the backend returned.
import type { InputField, ReferenceSource, ServiceManifest } from "../types";

// Cross-service pickers — these lists are served by the services that own the records (012/014/015).
const ITEMS_SOURCE: ReferenceSource = {
  path: "/api/operations/items",
  valueKey: "id",
  labelKeys: ["sku", "name"],
};
const CUSTOMERS_SOURCE: ReferenceSource = {
  path: "/api/crm/customers",
  valueKey: "id",
  labelKeys: ["customerCode", "name"],
};
const SUPPLIERS_SOURCE: ReferenceSource = {
  path: "/api/procurement/suppliers",
  valueKey: "id",
  labelKeys: ["supplierCode", "name"],
};
// Handles awaiting a checker. Narrowed to delivery receipts: the same store also holds sales-order review
// positions, and offering those here would let a sales-order id be submitted to the receipts endpoint.
const PENDING_RECEIPTS_SOURCE: ReferenceSource = {
  path: "/api/workflow/pending-reviews?action=RECORD_DELIVERY_RECEIPT",
  valueKey: "pendingId",
  labelKeys: ["targetRef", "makerEmployeeId"],
};

// Wire tokens, not display names: these are sent verbatim as query params / bound to backend enums.
const ACTIONS = [
  "TAKE_SALES_ORDER",
  "CONFIRM_SALES_PAYMENT",
  "RELEASE_SALES_ORDER",
  "COMPLETE_SALES_ORDER",
  "RAISE_PURCHASE_ORDER",
  "APPROVE_PURCHASE_ORDER",
  "SEND_PURCHASE_ORDER",
  "RECORD_DELIVERY_RECEIPT",
  "CONFIRM_DELIVERY_RECEIPT",
  "BUILD_PRODUCT",
  "MAINTAIN_CUSTOMER",
  "MAINTAIN_SUPPLIER",
];
const OUTCOMES = ["SUCCESS", "REJECTED_INVALID", "REJECTED_NOT_PERMITTED", "FAILED_UNAVAILABLE"];

/** An order/PO id typed or pasted from a result or the review queue (no short code exists to pick from). */
const idInput = (name: string, label: string): InputField => ({
  name,
  label,
  type: "text",
  required: true,
});

export const workflowManifest: ServiceManifest = {
  id: "workflow",
  label: "Workflow",
  icon: "Workflow",
  basePath: "/api/workflow",
  functions: [
    // --- Activity log (US1) ---
    {
      id: "activity",
      label: "Activity log",
      icon: "ScrollText",
      group: "Activity log",
      method: "GET",
      path: "/activity?actor={actor}&action={action}&outcome={outcome}&from={from}&to={to}",
      result: "table",
      inputs: [
        { name: "actor", label: "Actor (employee)", type: "text", placeholder: "emp-sales" },
        { name: "action", label: "Action", type: "select", options: ACTIONS },
        { name: "outcome", label: "Outcome", type: "select", options: OUTCOMES },
        { name: "from", label: "From", type: "text", placeholder: "2026-07-22T00:00:00Z" },
        { name: "to", label: "To", type: "text", placeholder: "2026-07-22T23:59:59Z" },
      ],
      description:
        "Append-only record of every back-office attempt, newest first. All filters are optional.",
    },

    // --- Authorization (US2) ---
    {
      id: "authorization",
      label: "Authorization rules",
      icon: "ShieldCheck",
      group: "Authorization",
      method: "GET",
      path: "/authorization",
      result: "table",
      description:
        "Who may perform, make, or check each action. Roles come from the employee record; view-only here.",
    },

    // --- Reviews (US2) ---
    {
      id: "pending-reviews",
      label: "Pending reviews",
      icon: "Inbox",
      group: "Reviews",
      method: "GET",
      path: "/pending-reviews?action={action}",
      result: "table",
      inputs: [{ name: "action", label: "Action", type: "select", options: ACTIONS }],
      description:
        "Items awaiting a checker. Held in memory: a service restart empties the queue and the maker re-records — no domain effect is lost.",
    },
  ],
};

export { ACTIONS, CUSTOMERS_SOURCE, ITEMS_SOURCE, OUTCOMES, PENDING_RECEIPTS_SOURCE, SUPPLIERS_SOURCE, idInput };
