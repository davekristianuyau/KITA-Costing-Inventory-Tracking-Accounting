package com.kita.workflow.ports;

/** Boundary to crm-service (FR-005, FR-014). Validation (US2) plus customer maintenance (US6). */
public interface CrmPort {

  /** True if the customer exists and is active. */
  boolean customerActive(String customerId);

  /** Create a customer; returns the new id (immediately usable, SC-008). */
  String createCustomer(CustomerInput input);

  /** Update an existing customer. */
  void updateCustomer(String customerId, CustomerInput input);

  record CustomerInput(String name, boolean active) {}
}
