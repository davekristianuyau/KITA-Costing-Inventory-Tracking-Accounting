// Operations service manifest (spec 012, contracts/operations-manifest.md). Grows through the user
// stories: US1 catalog+stock, US2 movements/locations, US3 BOM, US4 writes + order/build/receipt reads,
// US5 costing. Each function is rendered by the 011 FunctionWorkspace and called via the 009 edge.
import type { ReferenceSource, ServiceManifest } from "../types";

// A reference to the catalog items list — the pickers + id→label resolution both source from here.
const ITEMS_SOURCE: ReferenceSource = {
  path: "/api/operations/items",
  valueKey: "id",
  labelKeys: ["sku", "name"],
};

/** Resolve item-id columns in a result table to "SKU — name". */
const itemLabels = (columns: string[]) => [{ columns, source: ITEMS_SOURCE }];

export const operationsManifest: ServiceManifest = {
  id: "operations",
  label: "Operations",
  icon: "Package",
  basePath: "/api/operations",
  functions: [
    // --- Catalog ---
    {
      id: "items",
      label: "Items",
      icon: "Package",
      method: "GET",
      path: "/items",
      result: "table",
      description: "Catalog items — sku, name, type, unit, valuation, cost.",
    },
    {
      id: "item",
      label: "Item detail",
      icon: "FileText",
      method: "GET",
      path: "/items/{id}",
      result: "detail",
      inputs: [{ name: "id", label: "Item", type: "reference", required: true, source: ITEMS_SOURCE }],
      description: "Full attributes for one catalog item.",
    },
    // --- Inventory ---
    {
      id: "stock",
      label: "Stock on hand",
      icon: "Boxes",
      method: "GET",
      path: "/items/{id}/availability",
      result: "table",
      inputs: [{ name: "id", label: "Item", type: "reference", required: true, source: ITEMS_SOURCE }],
      resultRefs: itemLabels(["itemId"]),
      description: "On-hand / reserved / available per location (the reservations view).",
    },
  ],
};
