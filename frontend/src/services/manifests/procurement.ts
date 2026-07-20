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
  ],
};

// Exported for later stories' functions as the manifest grows through US2–US4.
export { SUPPLIERS_SOURCE, supplierLabels };
