package com.kita.workflow.ports.http;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Maps an hr-service employee <em>position</em> to the back-office role tokens the workflow authorizes
 * against (018). hr-service stores no back-office roles, so this mapping is the deployment's explicit
 * statement of "a Sales Clerk may act as SALES". Values are comma-separated role tokens:
 *
 * <pre>
 * workflow.hr.position-roles:
 *   sales-clerk: SALES
 *   cashier: CASHIER
 *   warehouse-staff: WAREHOUSE_STAFF
 *   purchasing-officer: PROCUREMENT_STAFF
 * </pre>
 *
 * <p><b>Keys must not contain spaces.</b> Spring's relaxed binding silently drops a map key with a
 * space in it, which matters here because real HR positions read "Sales Clerk". Both the configured key
 * and the employee's position are therefore {@link #normalize}d — upper-cased with every non-alphanumeric
 * character removed — so {@code sales-clerk}, {@code SALES_CLERK} and {@code "Sales Clerk"} all match.
 *
 * <p>Empty by default and fail-closed: an unmapped position grants no roles, so a governed action is
 * refused rather than silently permitted.
 */
@Component
@ConfigurationProperties(prefix = "workflow.hr")
public class HrPositionRoles {

  private final Map<String, String> positionRoles = new LinkedHashMap<>();

  public Map<String, String> getPositionRoles() {
    return positionRoles;
  }

  /** Upper-case, alphanumerics only — makes matching immune to spacing/punctuation differences. */
  public static String normalize(String position) {
    return position == null ? "" : position.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
  }

  /** Position→roles, keyed by the normalized position. */
  public Map<String, String> map() {
    Map<String, String> normalized = new LinkedHashMap<>();
    positionRoles.forEach((position, roles) -> normalized.put(normalize(position), roles));
    return normalized;
  }
}
