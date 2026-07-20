// Operations service manifest (spec 012, contracts/operations-manifest.md). Grows through the user
// stories: US1 catalog+stock, US2 movements/locations, US3 BOM, US4 writes + order/build/receipt reads,
// US5 costing. Each function is rendered by the 011 FunctionWorkspace and called via the 009 edge.
import type { ReferenceSource, ServiceManifest } from "../types";

// References to the catalog items + locations lists — the pickers + id→label resolution source from here.
const ITEMS_SOURCE: ReferenceSource = {
  path: "/api/operations/items",
  valueKey: "id",
  labelKeys: ["sku", "name"],
};
const LOCATIONS_SOURCE: ReferenceSource = {
  path: "/api/operations/locations",
  valueKey: "id",
  labelKeys: ["code", "name"],
};

/** Resolve item-id + location-id result columns to human labels. */
const stockLabels = [
  { columns: ["itemId"], source: ITEMS_SOURCE },
  { columns: ["locationId"], source: LOCATIONS_SOURCE },
];

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
      resultRefs: stockLabels,
      description: "On-hand / reserved / available per location (the reservations view).",
    },
    {
      id: "movements",
      label: "Movement ledger",
      icon: "ScrollText",
      method: "GET",
      path: "/movements?itemId={itemId}&from={from}&to={to}",
      result: "table",
      inputs: [
        { name: "itemId", label: "Item", type: "reference", required: true, source: ITEMS_SOURCE },
        { name: "from", label: "From", type: "text", placeholder: "ISO datetime (optional)" },
        { name: "to", label: "To", type: "text", placeholder: "ISO datetime (optional)" },
      ],
      resultRefs: stockLabels,
      description: "Stock movements for an item (receipts, issues, transfers, builds).",
    },
    {
      id: "locations",
      label: "Locations",
      icon: "Warehouse",
      method: "GET",
      path: "/locations",
      result: "table",
      description: "Stock locations for this client.",
    },
  ],
};
