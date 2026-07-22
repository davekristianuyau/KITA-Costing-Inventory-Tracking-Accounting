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
  functions: [],
};

export { ACTIONS, CUSTOMERS_SOURCE, ITEMS_SOURCE, OUTCOMES, PENDING_RECEIPTS_SOURCE, SUPPLIERS_SOURCE, idInput };
