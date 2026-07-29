package com.kita.workflow.common.security;

/**
 * Back-office role tokens, as assigned to an employee in the HR service (FR-002). workflow-service
 * resolves an employee's roles from HR and checks them against {@code authorization_mapping}; it never
 * trusts a self-asserted {@code X-Kita-Roles} header.
 */
public enum Role {
  SALES,
  CASHIER,
  SALES_MANAGER,
  WAREHOUSE_STAFF,
  WAREHOUSE_MANAGER,
  PROCUREMENT_STAFF,
  PROCUREMENT_APPROVER,
  PRODUCTION,
  CRM_ADMIN,
  /**
   * The highest-position administrator (017 FR-017). Held in the personnel record like any other
   * token, but read by every service as implying all of that service's roles.
   */
  OWNER
}
