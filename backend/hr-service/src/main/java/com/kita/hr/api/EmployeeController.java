package com.kita.hr.api;

import com.kita.hr.common.ForbiddenException;
import com.kita.hr.common.NotFoundException;
import com.kita.hr.common.security.CallerContext;
import com.kita.hr.common.security.Role;
import com.kita.hr.employee.CompensationRequest;
import com.kita.hr.employee.CompensationResponse;
import com.kita.hr.employee.CreateEmployeeRequest;
import com.kita.hr.employee.AccountLinkRequest;
import com.kita.hr.employee.Employee;
import com.kita.hr.employee.EmployeeRolesRequest;
import com.kita.hr.employee.EmployeeResponse;
import com.kita.hr.employee.EmployeeService;
import com.kita.hr.employee.StatusHistoryResponse;
import com.kita.hr.employee.UpdateEmployeeRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hr/employees")
public class EmployeeController {

  private final EmployeeService service;
  private final CallerContext caller;

  public EmployeeController(EmployeeService service, CallerContext caller) {
    this.service = service;
    this.caller = caller;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EmployeeResponse create(@Valid @RequestBody CreateEmployeeRequest req) {
    caller.require(Role.HR_ADMIN);
    return EmployeeResponse.from(service.create(req, actor()));
  }

  @GetMapping
  public List<EmployeeResponse> list() {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER, Role.MANAGER);
    return service.list().stream().map(EmployeeResponse::from).toList();
  }

  @GetMapping("/{id}")
  public EmployeeResponse get(@PathVariable UUID id) {
    authorizeRead(id);
    return EmployeeResponse.from(service.get(id), service.rolesOf(id));
  }

  /**
   * Resolve a login account to its employee, with status AND roles in one round trip (017 FR-001/FR-004)
   * — this is the call every governed action makes, so a second hop would be a per-action cost.
   *
   * <p>A 404 means "this account has no employee", which the caller MUST report distinctly from an
   * employee who exists but may not act (200 with a non-ACTIVE status) and from a permission refusal
   * (FR-005, SC-004). Service-to-service: the caller is the platform, not an end user.
   */
  /** Link an account to this employee (017 FR-008). OWNER only — linking decides who a login IS. */
  @PutMapping("/{id}/account")
  public EmployeeResponse linkAccount(
      @PathVariable UUID id, @Valid @RequestBody AccountLinkRequest req) {
    caller.require(Role.OWNER);
    Employee employee = service.linkAccount(id, req.accountUsername(), actor());
    return EmployeeResponse.from(employee, service.rolesOf(id));
  }

  /** Unlink; afterwards the account can perform no governed action (017 FR-008). OWNER only. */
  @DeleteMapping("/{id}/account")
  public EmployeeResponse unlinkAccount(@PathVariable UUID id) {
    caller.require(Role.OWNER);
    Employee employee = service.unlinkAccount(id, actor());
    return EmployeeResponse.from(employee, service.rolesOf(id));
  }

  /**
   * Replace this employee's roles (017 FR-014). OWNER only — granting privileges is the most powerful
   * operation in the system. The body is the full desired set, so a retry cannot double-grant.
   */
  @PutMapping("/{id}/roles")
  public EmployeeResponse setRoles(@PathVariable UUID id, @RequestBody EmployeeRolesRequest req) {
    caller.require(Role.OWNER);
    service.replaceRoles(id, req.roles(), actor());
    return EmployeeResponse.from(service.get(id), service.rolesOf(id));
  }

  @GetMapping("/by-account/{username}")
  public EmployeeResponse getByAccount(@PathVariable String username) {
    Employee employee =
        service
            .byAccount(username)
            .orElseThrow(() -> new NotFoundException("no employee linked to account " + username));
    return EmployeeResponse.from(employee, service.rolesOf(employee.getId()));
  }

  @PatchMapping("/{id}")
  public EmployeeResponse update(
      @PathVariable UUID id, @RequestBody UpdateEmployeeRequest req) {
    caller.require(Role.HR_ADMIN);
    return EmployeeResponse.from(service.update(id, req, actor()));
  }

  @PostMapping("/{id}/compensation")
  @ResponseStatus(HttpStatus.CREATED)
  public CompensationResponse addCompensation(
      @PathVariable UUID id, @Valid @RequestBody CompensationRequest req) {
    caller.require(Role.HR_ADMIN);
    return CompensationResponse.from(service.addCompensation(id, req, actor()));
  }

  @GetMapping("/{id}/compensation")
  public List<CompensationResponse> listCompensation(@PathVariable UUID id) {
    authorizeRead(id);
    return service.listCompensation(id).stream().map(CompensationResponse::from).toList();
  }

  @GetMapping("/{id}/status-history")
  public List<StatusHistoryResponse> statusHistory(@PathVariable UUID id) {
    authorizeRead(id);
    return service.statusHistory(id).stream().map(StatusHistoryResponse::from).toList();
  }

  /** Privileged roles may read anyone; an employee may read only their own record. */
  private void authorizeRead(UUID id) {
    if (caller.hasAnyRole(Role.HR_ADMIN, Role.PAYROLL_OFFICER, Role.MANAGER)) {
      return;
    }
    if (caller.hasAnyRole(Role.EMPLOYEE_SELF) && caller.employeeId().map(id::equals).orElse(false)) {
      return;
    }
    throw new ForbiddenException("not allowed to read this employee");
  }

  private String actor() {
    return caller.employeeId().map(UUID::toString).orElse("system");
  }
}
