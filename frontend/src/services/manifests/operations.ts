// Operations service manifest (spec 012, contracts/operations-manifest.md). Grows through the user
// stories: US1 catalog+stock, US2 movements/locations, US3 BOM, US4 writes + order/build/receipt reads,
// US5 costing. Each function is rendered by the 011 FunctionWorkspace and called via the 009 edge.
import type { ServiceManifest } from "../types";

export const operationsManifest: ServiceManifest = {
  id: "operations",
  label: "Operations",
  icon: "Package",
  basePath: "/api/operations",
  functions: [
    {
      id: "items",
      label: "Items",
      icon: "Package",
      method: "GET",
      path: "/items",
      result: "table",
      description: "Catalog items — sku, name, type, unit, valuation, cost.",
    },
  ],
};
