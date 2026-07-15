package com.kita.crm.common.security;

/**
 * Coarse roles for CRM actions (FR-016). Real authentication is performed by the gateway.
 *
 * <p>{@code CRM_ADMIN} manages customers, entitlements, rules, and policy; {@code SALES} reads
 * customers and computes discounts for the sales flow.
 */
public enum Role {
  CRM_ADMIN,
  SALES
}
