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

    // --- Actions — sales (US3 maker / lifecycle) ---
    {
      id: "take-sales-order",
      label: "Take sales order (maker)",
      icon: "ClipboardPlus",
      group: "Actions — sales",
      method: "POST",
      path: "/sales-orders",
      result: "outcome",
      inputs: [
        { name: "customerId", label: "Customer", type: "reference", required: true, source: CUSTOMERS_SOURCE },
        {
          name: "lines",
          label: "Lines",
          type: "list",
          required: true,
          minRows: 1,
          fields: [
            { name: "itemId", label: "Item", type: "reference", required: true, source: ITEMS_SOURCE },
            { name: "quantity", label: "Quantity", type: "number", required: true },
            { name: "unitPrice", label: "Unit price", type: "number", required: true },
          ],
        },
      ],
      description: "Draft an order and reserve stock. You are the maker — a different employee confirms payment.",
    },
    {
      id: "confirm-sales-payment",
      label: "Confirm payment (checker)",
      icon: "BadgeCheck",
      group: "Actions — sales",
      method: "POST",
      path: "/sales-orders/{id}/confirm-payment",
      result: "outcome",
      inputs: [idInput("id", "Sales order id")],
      description:
        "Confirm payment as the checker. You cannot check your own order — that is rejected as invalid, not as a permission refusal.",
    },
    {
      id: "release-sales-order",
      label: "Release order (checker)",
      icon: "PackageCheck",
      group: "Actions — sales",
      method: "POST",
      path: "/sales-orders/{id}/release",
      result: "outcome",
      inputs: [idInput("id", "Sales order id")],
      description: "Release a paid order for fulfilment.",
    },
    {
      id: "complete-sales-order",
      label: "Complete order",
      icon: "CircleCheck",
      group: "Actions — sales",
      method: "POST",
      path: "/sales-orders/{id}/complete",
      result: "outcome",
      inputs: [idInput("id", "Sales order id")],
      description: "Close out a released order.",
    },
    {
      id: "cancel-sales-order",
      label: "Cancel order",
      icon: "CircleX",
      group: "Actions — sales",
      method: "POST",
      path: "/sales-orders/{id}/cancel",
      result: "outcome",
      inputs: [idInput("id", "Sales order id")],
      description: "Cancel an order and release its reservations.",
    },

    // --- Actions — purchasing (US3) ---
    {
      id: "raise-purchase-order",
      label: "Raise purchase order",
      icon: "FilePlus",
      group: "Actions — purchasing",
      method: "POST",
      path: "/purchase-orders",
      result: "outcome",
      inputs: [
        { name: "supplierId", label: "Supplier", type: "reference", required: true, source: SUPPLIERS_SOURCE },
        {
          name: "lines",
          label: "Lines",
          type: "list",
          required: true,
          minRows: 1,
          fields: [
            { name: "itemId", label: "Item", type: "reference", required: true, source: ITEMS_SOURCE },
            { name: "quantity", label: "Quantity", type: "number", required: true },
            { name: "unitCost", label: "Unit cost", type: "number", required: true },
          ],
        },
      ],
      description: "Raise a PO with the supplier. The total is returned as an exact decimal.",
    },
    {
      id: "approve-purchase-order",
      label: "Approve purchase order",
      icon: "Stamp",
      group: "Actions — purchasing",
      method: "POST",
      path: "/purchase-orders/{id}/approve",
      result: "outcome",
      inputs: [idInput("id", "Purchase order id")],
      description: "Approve a drafted PO (requires the approver role).",
    },
    {
      id: "send-purchase-order",
      label: "Send purchase order",
      icon: "Send",
      group: "Actions — purchasing",
      method: "POST",
      path: "/purchase-orders/{id}/send",
      result: "outcome",
      inputs: [idInput("id", "Purchase order id")],
      description: "Send an approved PO to the supplier.",
    },
    {
      id: "record-receipt",
      label: "Record delivery receipt (maker)",
      icon: "PackagePlus",
      group: "Actions — purchasing",
      method: "POST",
      path: "/purchase-orders/{id}/receipts",
      result: "outcome",
      inputs: [
        idInput("id", "Purchase order id"),
        {
          name: "lines",
          label: "Received lines",
          type: "list",
          required: true,
          minRows: 1,
          fields: [
            { name: "itemId", label: "Item", type: "reference", required: true, source: ITEMS_SOURCE },
            { name: "quantityReceived", label: "Quantity received", type: "number", required: true },
          ],
        },
      ],
      description: "Record what arrived. Nothing moves until a different employee confirms it.",
    },
    {
      id: "confirm-receipt",
      label: "Confirm delivery receipt (checker)",
      icon: "PackageCheck",
      group: "Actions — purchasing",
      method: "POST",
      path: "/receipts/{pendingReceiptId}/confirm",
      result: "outcome",
      inputs: [
        {
          name: "pendingReceiptId",
          label: "Pending receipt",
          type: "reference",
          required: true,
          source: PENDING_RECEIPTS_SOURCE,
        },
      ],
      description:
        "Commit a recorded receipt: stock moves and the PO advances. Must be a different employee than the maker.",
    },

    // --- Actions — production & parties (US3) ---
    {
      id: "build-product",
      label: "Build product",
      icon: "Hammer",
      group: "Actions — production & parties",
      method: "POST",
      path: "/builds",
      result: "outcome",
      inputs: [
        { name: "itemId", label: "Item", type: "reference", required: true, source: ITEMS_SOURCE },
        { name: "quantity", label: "Quantity", type: "number", required: true },
      ],
      description: "Consume components per the BOM and produce the finished item.",
    },
    {
      id: "create-customer",
      label: "New customer",
      icon: "UserPlus",
      group: "Actions — production & parties",
      method: "POST",
      path: "/customers",
      result: "outcome",
      inputs: [
        { name: "name", label: "Name", type: "text", required: true },
        { name: "active", label: "Active", type: "boolean" },
      ],
      description: "Create a customer record.",
    },
    {
      id: "update-customer",
      label: "Update customer",
      icon: "UserCog",
      group: "Actions — production & parties",
      method: "PATCH",
      path: "/customers/{id}",
      result: "outcome",
      inputs: [
        { name: "id", label: "Customer", type: "reference", required: true, source: CUSTOMERS_SOURCE },
        { name: "name", label: "Name", type: "text", required: true },
        { name: "active", label: "Active", type: "boolean" },
      ],
      description: "Rename or deactivate a customer.",
    },
    {
      id: "create-supplier",
      label: "New supplier",
      icon: "TruckElectric",
      group: "Actions — production & parties",
      method: "POST",
      path: "/suppliers",
      result: "outcome",
      inputs: [
        { name: "name", label: "Name", type: "text", required: true },
        { name: "active", label: "Active", type: "boolean" },
      ],
      description: "Create a supplier record.",
    },
    {
      id: "update-supplier",
      label: "Update supplier",
      icon: "Settings2",
      group: "Actions — production & parties",
      method: "PATCH",
      path: "/suppliers/{id}",
      result: "outcome",
      inputs: [
        { name: "id", label: "Supplier", type: "reference", required: true, source: SUPPLIERS_SOURCE },
        { name: "name", label: "Name", type: "text", required: true },
        { name: "active", label: "Active", type: "boolean" },
      ],
      description: "Rename or deactivate a supplier.",
    },
    {
      id: "set-supplied-items",
      label: "Set supplied items",
      icon: "ListPlus",
      group: "Actions — production & parties",
      method: "PUT",
      path: "/suppliers/{id}/items",
      result: "outcome",
      inputs: [
        { name: "id", label: "Supplier", type: "reference", required: true, source: SUPPLIERS_SOURCE },
        {
          name: "items",
          label: "Supplied items",
          type: "list",
          required: true,
          minRows: 1,
          fields: [
            { name: "itemId", label: "Item", type: "reference", required: true, source: ITEMS_SOURCE },
            { name: "unitCost", label: "Unit cost", type: "number", required: true },
          ],
        },
      ],
      description: "Replace the supplier's catalogue of supplied items and costs.",
    },
  ],
};

export { ACTIONS, CUSTOMERS_SOURCE, ITEMS_SOURCE, OUTCOMES, PENDING_RECEIPTS_SOURCE, SUPPLIERS_SOURCE, idInput };
