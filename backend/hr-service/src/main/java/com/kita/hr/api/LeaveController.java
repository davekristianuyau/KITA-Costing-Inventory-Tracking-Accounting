package com.kita.hr.api;

import com.kita.hr.common.security.CallerContext;
import com.kita.hr.common.security.Role;
import com.kita.hr.leave.LeaveRequest;
import com.kita.hr.leave.LeaveService;
import com.kita.hr.leave.LeaveStatus;
import com.kita.hr.leave.LeaveType;
import com.kita.hr.leave.dto.AccrueLeaveRequest;
import com.kita.hr.leave.dto.FileLeaveRequest;
import com.kita.hr.leave.dto.LeaveBalanceResponse;
import com.kita.hr.leave.dto.LeaveDecisionRequest;
import com.kita.hr.leave.dto.LeaveRequestResponse;
import com.kita.hr.leave.dto.LeaveTypeRequest;
import com.kita.hr.leave.dto.LeaveTypeResponse;
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
public class LeaveController {

  private final LeaveService leave;
  private final CallerContext caller;

  public LeaveController(LeaveService leave, CallerContext caller) {
    this.leave = leave;
    this.caller = caller;
  }

  @PostMapping("/api/hr/leave/types")
  @ResponseStatus(HttpStatus.CREATED)
  public LeaveTypeResponse createType(@Valid @RequestBody LeaveTypeRequest r) {
    caller.require(Role.HR_ADMIN);
    LeaveType type =
        new LeaveType(
            r.code(), r.name(), r.payTreatment(), r.accrualRate(), r.accrualPeriod(),
            r.accrualCap(), r.allowNegative());
    return LeaveTypeResponse.from(leave.createType(type, actor()));
  }

  @GetMapping("/api/hr/leave/types")
  public List<LeaveTypeResponse> listTypes() {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER, Role.MANAGER);
    return leave.listTypes().stream().map(LeaveTypeResponse::from).toList();
  }

  @PostMapping("/api/hr/leave/requests")
  @ResponseStatus(HttpStatus.CREATED)
  public LeaveRequestResponse file(@Valid @RequestBody FileLeaveRequest r) {
    // An employee files their own; HR/managers may file on behalf.
    caller.require(Role.HR_ADMIN, Role.MANAGER, Role.EMPLOYEE_SELF);
    LeaveRequest req =
        new LeaveRequest(
            r.employeeId(), r.leaveTypeId(), r.startDate(), r.endDate(), r.duration(), r.reason());
    return LeaveRequestResponse.from(leave.file(req, actor()));
  }

  @PostMapping("/api/hr/leave/requests/{id}/decision")
  public LeaveRequestResponse decide(
      @PathVariable UUID id, @Valid @RequestBody LeaveDecisionRequest r) {
    caller.require(Role.MANAGER, Role.HR_ADMIN);
    String decidedBy = r.decidedBy() != null ? r.decidedBy() : actor();
    return LeaveRequestResponse.from(leave.decide(id, r.approved(), decidedBy));
  }

  @GetMapping("/api/hr/leave/requests")
  public List<LeaveRequestResponse> listRequests(
      @RequestParam(required = false) UUID employeeId,
      @RequestParam(required = false) LeaveStatus status) {
    caller.require(Role.HR_ADMIN, Role.MANAGER, Role.PAYROLL_OFFICER, Role.EMPLOYEE_SELF);
    return leave.listRequests(employeeId, status).stream()
        .map(LeaveRequestResponse::from)
        .toList();
  }

  @GetMapping("/api/hr/leave/requests/{id}")
  public LeaveRequestResponse getRequest(@PathVariable UUID id) {
    caller.require(Role.HR_ADMIN, Role.MANAGER, Role.PAYROLL_OFFICER, Role.EMPLOYEE_SELF);
    return LeaveRequestResponse.from(leave.getRequest(id));
  }

  @GetMapping("/api/hr/leave/balances")
  public List<LeaveBalanceResponse> balances(@RequestParam UUID employeeId) {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER, Role.MANAGER, Role.EMPLOYEE_SELF);
    return leave.balancesFor(employeeId).stream().map(LeaveBalanceResponse::from).toList();
  }

  /** Run the leave type's accrual policy for an employee (FR-018). */
  @PostMapping("/api/hr/leave/accruals")
  public LeaveBalanceResponse accrue(@Valid @RequestBody AccrueLeaveRequest r) {
    caller.require(Role.HR_ADMIN);
    return LeaveBalanceResponse.from(
        leave.accrue(r.employeeId(), r.leaveTypeId(), r.periods(), actor()));
  }

  private String actor() {
    return caller.employeeId().map(UUID::toString).orElse("system");
  }
}
