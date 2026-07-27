package com.kita.workflow.ports.http;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Maps an hr-service employee <em>position</em> to the back-office role tokens the workflow authorizes
 * against (018). hr-service stores no back-office roles, so this mapping is the deployment's explicit
 * statement of "a Sales Clerk may act as SALES". Keys are upper-cased positions; values are
 * comma-separated role tokens:
 *
 * <pre>
 * workflow.hr.position-roles:
 *   "SALES CLERK": SALES
 *   "CASHIER": CASHIER
 *   "WAREHOUSE STAFF": WAREHOUSE_STAFF
 *   "PURCHASING OFFICER": PROCUREMENT_STAFF
 * </pre>
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

  /** Position→roles, keyed upper-case for case-insensitive lookup. */
  public Map<String, String> map() {
    Map<String, String> normalized = new LinkedHashMap<>();
    positionRoles.forEach((position, roles) -> normalized.put(position.toUpperCase(), roles));
    return normalized;
  }
}
