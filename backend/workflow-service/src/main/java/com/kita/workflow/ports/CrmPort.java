package com.kita.workflow.ports;

/**
 * Boundary to crm-service (FR-005). MVP needs only customer validation; create/update arrive with US6.
 */
public interface CrmPort {

  /** True if the customer exists and is active. */
  boolean customerActive(String customerId);
}
