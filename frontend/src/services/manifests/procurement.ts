// Procurement service manifest (spec 015, contracts/procurement-manifest.md). FRONTEND-ONLY — every
// read/write already exists in procurement-service; reuses the full 012/013/014 shared framework (PO/receipt
// lines[] render via the 014 detail sub-table). Grows through the user stories: US1 suppliers + POs, US2
// suggestions, US3 writes/lifecycle, US4 receiving. Each function is rendered by the 011 FunctionWorkspace.
import type { ReferenceSource, ServiceManifest } from "../types";

// Supplier reference source — the picker + id→label resolution source from this list.
const SUPPLIERS_SOURCE: ReferenceSource = {
  path: "/api/procurement/suppliers",
  valueKey: "id",
  labelKeys: ["supplierCode", "name"],
};

/** Resolve supplier-id result columns to the supplier label. */
const supplierLabels = (columns: string[]) => [{ columns, source: SUPPLIERS_SOURCE }];

export const procurementManifest: ServiceManifest = {
  id: "procurement",
  label: "Procurement",
  icon: "ShoppingCart",
  basePath: "/api/procurement",
  functions: [
    // --- Suppliers (US1) ---
    {
      id: "suppliers",
      label: "Suppliers",
      icon: "Truck",
      method: "GET",
      path: "/suppliers",
      result: "table",
      description: "Supplier master — code, name, status, terms.",
    },
    {
      id: "supplier",
      label: "Supplier detail",
      icon: "Building2",
      method: "GET",
      path: "/suppliers/{id}",
      result: "detail",
      inputs: [{ name: "id", label: "Supplier", type: "reference", required: true, source: SUPPLIERS_SOURCE }],
      description: "Full attributes for one supplier.",
    },
    {
      id: "supplier-items",
      label: "Supplier items",
      icon: "PackageSearch",
      method: "GET",
      path: "/suppliers/{id}/items",
      result: "table",
      inputs: [{ name: "id", label: "Supplier", type: "reference", required: true, source: SUPPLIERS_SOURCE }],
      description: "Items a supplier offers (price, lead time, min order qty).",
    },
    {
      id: "supplier-history",
      label: "Supplier history",
      icon: "History",
      method: "GET",
      path: "/suppliers/{id}/history",
      result: "table",
      inputs: [{ name: "id", label: "Supplier", type: "reference", required: true, source: SUPPLIERS_SOURCE }],
      description: "Change log for a supplier and its items.",
    },
    // --- Purchase orders (US1 reads) ---
    {
      id: "purchase-orders",
      label: "Purchase orders",
      icon: "ClipboardList",
      method: "GET",
      path: "/purchase-orders",
      result: "table",
      resultRefs: supplierLabels(["supplierId"]),
      description: "All purchase orders with supplier, status, and total.",
    },
    {
      id: "purchase-order",
      label: "Purchase order detail",
      icon: "FileText",
      method: "GET",
      path: "/purchase-orders/{id}",
      result: "detail",
      inputs: [{ name: "id", label: "Purchase order id", type: "text", required: true }],
      resultRefs: supplierLabels(["supplierId"]),
      description: "A PO's lines (item / ordered / received / price) + status.",
    },
    // --- Reorder (US2) ---
    {
      id: "reorder-suggestions",
      label: "Reorder suggestions",
      icon: "TrendingDown",
      method: "GET",
      path: "/restock/suggestions",
      result: "table",
      resultRefs: supplierLabels(["supplierId"]),
      description: "Items at/below reorder point with a suggested quantity + supplier.",
    },
    // --- Supplier writes (US3) ---
    {
      id: "create-supplier",
      label: "New supplier",
      icon: "Plus",
      method: "POST",
      path: "/suppliers",
      result: "detail",
      inputs: [
        { name: "supplierCode", label: "Supplier code", type: "text", required: true },
        { name: "name", label: "Name", type: "text", required: true },
        { name: "email", label: "Email", type: "text" },
        { name: "phone", label: "Phone", type: "text" },
        { name: "address", label: "Address", type: "text" },
        { name: "paymentTerms", label: "Payment terms", type: "text" },
        { name: "deliveryTerms", label: "Delivery terms", type: "text" },
      ],
      description: "Create a supplier record.",
    },
    {
      id: "update-supplier",
      label: "Update supplier",
      icon: "Pencil",
      method: "PATCH",
      path: "/suppliers/{id}",
      result: "detail",
      inputs: [
        { name: "id", label: "Supplier", type: "reference", required: true, source: SUPPLIERS_SOURCE },
        { name: "name", label: "Name", type: "text" },
        { name: "email", label: "Email", type: "text" },
        { name: "phone", label: "Phone", type: "text" },
        { name: "address", label: "Address", type: "text" },
        { name: "paymentTerms", label: "Payment terms", type: "text" },
        { name: "deliveryTerms", label: "Delivery terms", type: "text" },
        { name: "status", label: "Status", type: "select", options: ["ACTIVE", "INACTIVE"] },
      ],
      description: "Update a supplier's fields or status.",
    },
    {
      id: "add-supplier-item",
      label: "Add supplier item",
      icon: "PackagePlus",
      method: "POST",
      path: "/suppliers/{id}/items",
      result: "detail",
      inputs: [
        { name: "id", label: "Supplier", type: "reference", required: true, source: SUPPLIERS_SOURCE },
        { name: "itemRef", label: "Item ref", type: "text", required: true },
        { name: "supplierPrice", label: "Supplier price", type: "number", required: true },
        { name: "leadTimeDays", label: "Lead time (days)", type: "number" },
        { name: "minOrderQty", label: "Min order qty", type: "number" },
      ],
      description: "Add an item a supplier offers.",
    },
    // --- Purchase order writes / lifecycle (US3) ---
    {
      id: "create-po",
      label: "New purchase order",
      icon: "ClipboardPlus",
      method: "POST",
      path: "/purchase-orders",
      result: "detail",
      inputs: [
        { name: "poNo", label: "PO number", type: "text" },
        { name: "supplierId", label: "Supplier", type: "reference", required: true, source: SUPPLIERS_SOURCE },
        {
          name: "lines",
          label: "Lines",
          type: "list",
          required: true,
          minRows: 1,
          fields: [
            { name: "itemRef", label: "Item ref", type: "text", required: true },
            { name: "qtyOrdered", label: "Qty ordered", type: "number", required: true },
            { name: "agreedPrice", label: "Agreed price", type: "number" },
          ],
        },
      ],
      description: "Create a purchase order with one or more lines.",
    },
    {
      id: "approve-po",
      label: "Approve PO",
      icon: "CheckCircle2",
      method: "POST",
      path: "/purchase-orders/{id}/approve",
      result: "detail",
      inputs: [{ name: "id", label: "Purchase order id", type: "text", required: true }],
    },
    {
      id: "send-po",
      label: "Send PO",
      icon: "Send",
      method: "POST",
      path: "/purchase-orders/{id}/send",
      result: "detail",
      inputs: [{ name: "id", label: "Purchase order id", type: "text", required: true }],
    },
    {
      id: "cancel-po",
      label: "Cancel PO",
      icon: "XCircle",
      method: "POST",
      path: "/purchase-orders/{id}/cancel",
      result: "detail",
      inputs: [{ name: "id", label: "Purchase order id", type: "text", required: true }],
    },
    {
      id: "close-po",
      label: "Close PO",
      icon: "Lock",
      method: "POST",
      path: "/purchase-orders/{id}/close",
      result: "detail",
      inputs: [{ name: "id", label: "Purchase order id", type: "text", required: true }],
    },
    // --- Suggestion actions (US3) ---
    {
      id: "generate-suggestions",
      label: "Generate reorder suggestions",
      icon: "Sparkles",
      method: "POST",
      path: "/restock/suggestions",
      result: "table",
      resultRefs: supplierLabels(["supplierId"]),
      description: "Compute fresh restock suggestions from current stock.",
    },
    {
      id: "convert-suggestion",
      label: "Convert suggestion to PO",
      icon: "ArrowRightLeft",
      method: "POST",
      path: "/restock/suggestions/{id}/convert",
      result: "detail",
      inputs: [{ name: "id", label: "Suggestion id", type: "text", required: true }],
    },
    {
      id: "dismiss-suggestion",
      label: "Dismiss suggestion",
      icon: "Trash2",
      method: "POST",
      path: "/restock/suggestions/{id}/dismiss",
      result: "detail",
      inputs: [{ name: "id", label: "Suggestion id", type: "text", required: true }],
    },
    // --- Receiving (US4) ---
    {
      id: "receive-po",
      label: "Receive against PO",
      icon: "PackageCheck",
      method: "POST",
      path: "/purchase-orders/{id}/receipts",
      result: "detail",
      inputs: [
        { name: "id", label: "Purchase order id", type: "text", required: true },
        {
          name: "lines",
          label: "Received lines",
          type: "list",
          required: true,
          minRows: 1,
          fields: [
            { name: "itemRef", label: "Item ref", type: "text", required: true },
            { name: "qtyReceived", label: "Qty received", type: "number", required: true },
          ],
        },
      ],
      description: "Record receiving against a PO (the backend posts the goods receipt to Operations).",
    },
    {
      id: "po-receipts",
      label: "PO receipts",
      icon: "Boxes",
      method: "GET",
      path: "/purchase-orders/{id}/receipts",
      result: "table",
      inputs: [{ name: "id", label: "Purchase order id", type: "text", required: true }],
      description: "Goods receipts already recorded against a PO.",
    },
  ],
};

// Exported for later stories' functions as the manifest grows through US2–US4.
export { SUPPLIERS_SOURCE, supplierLabels };
