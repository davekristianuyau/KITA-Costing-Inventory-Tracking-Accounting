package com.kita.hr.api;

import com.kita.hr.common.security.CallerContext;
import com.kita.hr.common.security.Role;
import com.kita.hr.employee.Employee;
import com.kita.hr.employee.EmployeeService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who can act as whom (017 FR-008). A privileged read: an administrator reviewing access needs one
 * place that answers "which login belongs to which employee, and is that employee still active" —
 * otherwise the only way to audit it is to walk every employee record.
 */
@RestController
@RequestMapping("/api/hr/account-links")
public class AccountLinkController {

  private final EmployeeService service;
  private final CallerContext caller;

  public AccountLinkController(EmployeeService service, CallerContext caller) {
    this.service = service;
    this.caller = caller;
  }

  /** One row per employee that currently has a login. */
  public record AccountLinkResponse(
      UUID employeeId, String employeeNo, String name, String accountUsername, String status) {

    static AccountLinkResponse from(Employee e) {
      return new AccountLinkResponse(
          e.getId(),
          e.getEmployeeNo(),
          e.getFirstName() + " " + e.getLastName(),
          e.getAccountUsername(),
          e.getStatus().name());
    }
  }

  @GetMapping
  public List<AccountLinkResponse> list() {
    caller.require(Role.OWNER, Role.HR_ADMIN);
    return service.linkedEmployees().stream().map(AccountLinkResponse::from).toList();
  }
}
