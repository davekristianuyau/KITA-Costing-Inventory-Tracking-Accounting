package com.kita.workflow.actor;

import com.kita.workflow.common.ValidationException;
import com.kita.workflow.common.security.Role;
import com.kita.workflow.ports.HrPort;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates the acting employee against HR and resolves their roles (FR-001, FR-002). A separated,
 * inactive or unknown employee is rejected (422); roles come from the HR record, never the header.
 */
@Component
public class ActorResolver {

  private final HrPort hrPort;

  public ActorResolver(HrPort hrPort) {
    this.hrPort = hrPort;
  }

  public ResolvedActor resolve(String employeeId) {
    if (employeeId == null || employeeId.isBlank()) {
      throw new ValidationException("no acting employee (missing X-Kita-User)");
    }
    HrPort.EmployeeView employee =
        hrPort
            .getEmployee(employeeId)
            .filter(HrPort.EmployeeView::active)
            .orElseThrow(
                () -> new ValidationException("employee not active: " + employeeId));
    return new ResolvedActor(employeeId, mapRoles(employee.roles()));
  }

  private Set<Role> mapRoles(Set<String> tokens) {
    Set<Role> roles = EnumSet.noneOf(Role.class);
    if (tokens != null) {
      for (String token : tokens) {
        try {
          roles.add(Role.valueOf(token.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
          // unknown role token from HR: ignore, it grants nothing here
        }
      }
    }
    return roles;
  }
}
