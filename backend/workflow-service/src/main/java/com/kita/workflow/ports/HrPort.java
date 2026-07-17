package com.kita.workflow.ports;

import java.util.Optional;
import java.util.Set;

/**
 * Boundary to hr-service (FR-001, FR-002). Validates the acting employee and supplies the
 * authoritative roles assigned in HR. workflow-service never caches this (no duplicate master, FR-017).
 */
public interface HrPort {

  /**
   * @param id the employee's status and assigned back-office role tokens; empty if unknown
   */
  Optional<EmployeeView> getEmployee(String id);

  /** active=false or absent ⇒ the actor is rejected. Roles feed the {@code ActionAuthorizer}. */
  record EmployeeView(String id, boolean active, Set<String> roles) {}
}
