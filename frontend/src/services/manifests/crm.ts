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
    // --- Quote (US2) ---
    {
      id: "quote",
      label: "Price quote",
      icon: "Calculator",
      method: "POST",
      path: "/discounts/compute",
      result: "detail",
      inputs: [
        { name: "customerId", label: "Customer", type: "reference", required: true, source: CUSTOMERS_SOURCE },
        { name: "saleDate", label: "Sale date", type: "text", required: true, placeholder: "YYYY-MM-DD" },
        {
          name: "lineItems",
          label: "Line items",
          type: "list",
          required: true,
          minRows: 1,
          fields: [
            { name: "itemRef", label: "Item", type: "text" },
            { name: "quantity", label: "Quantity", type: "number", required: true },
            { name: "unitPrice", label: "Unit price", type: "number", required: true },
          ],
        },
      ],
      description: "Itemized cascading discount + statutory + VAT breakdown for a customer's line items.",
    },
    // --- Discount rules (US3) ---
    {
      id: "discount-rules",
      label: "Discount rules",
      icon: "Percent",
      method: "GET",
      path: "/discount-rules?asOf={asOf}",
      result: "table",
      inputs: [{ name: "asOf", label: "As of", type: "text", placeholder: "YYYY-MM-DD (optional)" }],
      description: "The cascading discount-tier definitions effective on a date.",
    },
    {
      id: "discount-policy",
      label: "Discount policy",
      icon: "Settings2",
      method: "GET",
      path: "/discount-policy",
      result: "detail",
      description: "The stacking mode that governs how discounts cascade.",
    },
    {
      id: "loyalty-tiers",
      label: "Loyalty tiers",
      icon: "Award",
      method: "GET",
      path: "/loyalty/tiers",
      result: "table",
      description: "The loyalty / repeat-purchase tier definitions.",
    },
    // --- Customer writes (US4) ---
    {
      id: "create-customer",
      label: "New customer",
      icon: "UserPlus",
      method: "POST",
      path: "/customers",
      result: "detail",
      inputs: [
        { name: "customerCode", label: "Customer code", type: "text", required: true },
        { name: "type", label: "Type", type: "select", required: true, options: ["INDIVIDUAL", "BUSINESS"] },
        { name: "name", label: "Name", type: "text", required: true },
        { name: "email", label: "Email", type: "text" },
        { name: "phone", label: "Phone", type: "text" },
        { name: "address", label: "Address", type: "text" },
      ],
      description: "Create a customer record.",
    },
    {
      id: "update-customer",
      label: "Update customer",
      icon: "UserCog",
      method: "PATCH",
      path: "/customers/{id}",
      result: "detail",
      inputs: [
        { name: "id", label: "Customer", type: "reference", required: true, source: CUSTOMERS_SOURCE },
        { name: "name", label: "Name", type: "text" },
        { name: "email", label: "Email", type: "text" },
        { name: "phone", label: "Phone", type: "text" },
        { name: "address", label: "Address", type: "text" },
        { name: "status", label: "Status", type: "select", options: ["ACTIVE", "INACTIVE"] },
      ],
      description: "Update a customer's fields or status.",
    },
    {
      id: "add-entitlement",
      label: "Mark senior/PWD eligible",
      icon: "BadgeCheck",
      method: "POST",
      path: "/customers/{id}/entitlements",
      result: "detail",
      inputs: [
        { name: "id", label: "Customer", type: "reference", required: true, source: CUSTOMERS_SOURCE },
        { name: "kind", label: "Kind", type: "select", required: true, options: ["SENIOR", "PWD"] },
        { name: "supportingIdRef", label: "Supporting ID ref", type: "text" },
        { name: "validFrom", label: "Valid from", type: "text", required: true, placeholder: "YYYY-MM-DD" },
        { name: "validTo", label: "Valid to", type: "text", placeholder: "YYYY-MM-DD (optional)" },
      ],
      description: "Record a government-mandated entitlement (SENIOR / PWD).",
    },
    {
      id: "evaluate-loyalty",
      label: "Evaluate loyalty tier",
      icon: "Award",
      method: "POST",
      path: "/customers/{id}/loyalty/evaluate",
      result: "detail",
      inputs: [
        { name: "id", label: "Customer", type: "reference", required: true, source: CUSTOMERS_SOURCE },
        { name: "purchaseCount", label: "Purchase count", type: "number", required: true },
        { name: "purchaseValue", label: "Purchase value", type: "number", required: true },
      ],
      description: "Re-evaluate the customer's loyalty tier from supplied activity.",
    },
    // --- Rule authoring (US4) ---
    {
      id: "create-discount-rule",
      label: "New discount rule",
      icon: "Plus",
      method: "POST",
      path: "/discount-rules",
      result: "detail",
      inputs: [
        { name: "code", label: "Code", type: "text", required: true },
        { name: "origin", label: "Origin", type: "select", required: true, options: ["STATUTORY", "PROMOTIONAL", "LOYALTY"] },
        { name: "computation", label: "Computation", type: "select", required: true, options: ["PERCENT", "FIXED"] },
        { name: "value", label: "Value", type: "number", required: true },
        { name: "effectiveDate", label: "Effective date", type: "text", required: true, placeholder: "YYYY-MM-DD" },
      ],
      description: "Define a cascading discount rule.",
    },
    {
      id: "set-discount-policy",
      label: "Set discount policy",
      icon: "Settings2",
      method: "PUT",
      path: "/discount-policy",
      result: "detail",
      inputs: [
        {
          name: "mode",
          label: "Stacking mode",
          type: "select",
          required: true,
          options: ["MOST_FAVORABLE", "STATUTORY_THEN_PROMO", "PROMO_THEN_STATUTORY", "STATUTORY_ONLY"],
        },
      ],
      description: "Set how discounts stack/cascade.",
    },
    {
      id: "create-loyalty-tier",
      label: "New loyalty tier",
      icon: "Award",
      method: "POST",
      path: "/loyalty/tiers",
      result: "detail",
      inputs: [
        { name: "code", label: "Code", type: "text", required: true },
        { name: "name", label: "Name", type: "text", required: true },
        { name: "minPurchaseValue", label: "Min purchase value", type: "number", required: true },
        { name: "discountRuleId", label: "Discount rule id", type: "text" },
      ],
      description: "Define a loyalty / repeat-purchase tier.",
    },
  ],
};

// Exported for later stories' functions as the manifest grows through US2–US4.
export { CUSTOMERS_SOURCE, LOYALTY_TIERS_SOURCE, customerLabels, tierLabels };
