// CRM service manifest (spec 014, contracts/crm-manifest.md). FRONTEND-ONLY — every read/write already
// exists in crm-service; reuses the 012/013 shared inputs. Grows through the user stories: US1 customers +
// tiers, US2 quote, US3 rules, US4 writes. Each function is rendered by the 011 FunctionWorkspace via the edge.
import type { ReferenceSource, ServiceManifest } from "../types";

// Reference sources — the pickers + id→label resolution source from these lists.
const CUSTOMERS_SOURCE: ReferenceSource = {
  path: "/api/crm/customers",
  valueKey: "id",
  labelKeys: ["customerCode", "name"],
};
const LOYALTY_TIERS_SOURCE: ReferenceSource = {
  path: "/api/crm/loyalty/tiers",
  valueKey: "id",
  labelKeys: ["code", "name"],
};

/** Resolve customer-id / loyalty-tier-id result columns to human labels. */
const customerLabels = (columns: string[]) => [{ columns, source: CUSTOMERS_SOURCE }];
const tierLabels = (columns: string[]) => [{ columns, source: LOYALTY_TIERS_SOURCE }];

export const crmManifest: ServiceManifest = {
  id: "crm",
  label: "Customers",
  icon: "Contact",
  basePath: "/api/crm",
  functions: [
    // --- Customers (US1) ---
    {
      id: "customers",
      label: "Customers",
      icon: "Contact",
      method: "GET",
      path: "/customers",
      result: "table",
      resultRefs: tierLabels(["loyaltyTierId"]),
      description: "Customer records — code, type, name, status.",
    },
    {
      id: "customer",
      label: "Customer detail",
      icon: "UserRound",
      method: "GET",
      path: "/customers/{id}",
      result: "detail",
      inputs: [{ name: "id", label: "Customer", type: "reference", required: true, source: CUSTOMERS_SOURCE }],
      resultRefs: tierLabels(["loyaltyTierId"]),
      description: "Full attributes for one customer, including its loyalty tier.",
    },
    {
      id: "entitlements",
      label: "Customer entitlements",
      icon: "BadgeCheck",
      method: "GET",
      path: "/customers/{id}/entitlements",
      result: "table",
      inputs: [{ name: "id", label: "Customer", type: "reference", required: true, source: CUSTOMERS_SOURCE }],
      description: "Government-mandated eligibility (SENIOR / PWD) for a customer.",
    },
  ],
};

// Exported for later stories' functions as the manifest grows through US2–US4.
export { CUSTOMERS_SOURCE, LOYALTY_TIERS_SOURCE, customerLabels, tierLabels };
