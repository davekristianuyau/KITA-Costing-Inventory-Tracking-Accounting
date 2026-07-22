package com.kita.hr.api;

import com.kita.hr.common.ForbiddenException;
import com.kita.hr.common.security.CallerContext;
import com.kita.hr.common.security.Role;
import com.kita.hr.payroll.PayrollRunService;
import com.kita.hr.payroll.dto.ComputeResultResponse;
import com.kita.hr.payroll.dto.CreatePayrollRunRequest;
import com.kita.hr.payroll.dto.PayrollRunResponse;
import com.kita.hr.payroll.dto.PayslipResponse;
import com.kita.hr.payroll.dto.RegisterResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PayrollController {

  private final PayrollRunService service;
  private final CallerContext caller;

  public PayrollController(PayrollRunService service, CallerContext caller) {
    this.service = service;
    this.caller = caller;
  }

  @PostMapping("/api/hr/payroll/runs")
  @ResponseStatus(HttpStatus.CREATED)
  public PayrollRunResponse create(@Valid @RequestBody CreatePayrollRunRequest req) {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER);
    return PayrollRunResponse.from(service.create(req, actor()));
  }

  @GetMapping("/api/hr/payroll/runs")
  public List<PayrollRunResponse> listRuns() {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER);
    return service.list().stream().map(PayrollRunResponse::from).toList();
  }

  @GetMapping("/api/hr/payroll/runs/{id}")
  public PayrollRunResponse getRun(@PathVariable UUID id) {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER);
    return PayrollRunResponse.from(service.get(id));
  }

  @PostMapping("/api/hr/payroll/runs/{id}/compute")
  public ComputeResultResponse compute(@PathVariable UUID id) {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER);
    return service.compute(id, actor());
  }

  @PostMapping("/api/hr/payroll/runs/{id}/finalize")
  public PayrollRunResponse finalizeRun(@PathVariable UUID id) {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER);
    return PayrollRunResponse.from(service.finalizeRun(id, actor()));
  }

  @PostMapping("/api/hr/payroll/runs/{id}/cancel")
  public PayrollRunResponse cancel(@PathVariable UUID id) {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER);
    return PayrollRunResponse.from(service.cancel(id, actor()));
  }

  @GetMapping("/api/hr/payroll/runs/{id}/register")
  public RegisterResponse register(@PathVariable UUID id) {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER);
    return service.register(id);
  }

  @GetMapping("/api/hr/payslips")
  public List<PayslipResponse> payslips(
      @RequestParam(required = false) UUID runId,
      @RequestParam(required = false) UUID employeeId) {
    return service.payslips(runId, scopeToCaller(employeeId));
  }

  /**
   * Privileged roles may read any employee's payslips; an EMPLOYEE_SELF caller is pinned to their
   * own — an absent employeeId defaults to theirs, and another employee's is refused.
   */
  private UUID scopeToCaller(UUID requested) {
    if (caller.hasAnyRole(Role.HR_ADMIN, Role.PAYROLL_OFFICER)) {
      return requested;
    }
    if (caller.hasAnyRole(Role.EMPLOYEE_SELF)) {
      UUID own =
          caller
              .employeeId()
              .orElseThrow(() -> new ForbiddenException("caller has no employee identity"));
      if (requested != null && !requested.equals(own)) {
        throw new ForbiddenException("not allowed to read another employee's payslips");
      }
      return own;
    }
    throw new ForbiddenException("not allowed to read payslips");
  }

  private String actor() {
    return caller.employeeId().map(UUID::toString).orElse("system");
  }
}
