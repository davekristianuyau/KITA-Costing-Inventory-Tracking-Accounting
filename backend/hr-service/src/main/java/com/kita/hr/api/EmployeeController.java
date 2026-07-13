package com.kita.hr.api;

import com.kita.hr.common.ForbiddenException;
import com.kita.hr.common.security.CallerContext;
import com.kita.hr.common.security.Role;
import com.kita.hr.employee.CompensationRequest;
import com.kita.hr.employee.CompensationResponse;
import com.kita.hr.employee.CreateEmployeeRequest;
import com.kita.hr.employee.EmployeeResponse;
import com.kita.hr.employee.EmployeeService;
import com.kita.hr.employee.UpdateEmployeeRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    return EmployeeResponse.from(service.get(id));
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
