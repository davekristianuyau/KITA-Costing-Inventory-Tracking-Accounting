package com.kita.hr.api;

import com.kita.hr.common.security.CallerContext;
import com.kita.hr.common.security.Role;
import com.kita.hr.deduction.DeductionRule;
import com.kita.hr.deduction.DeductionRuleRow;
import com.kita.hr.deduction.DeductionService;
import com.kita.hr.deduction.Loan;
import com.kita.hr.deduction.LoanService;
import com.kita.hr.deduction.dto.CreateDeductionRuleRequest;
import com.kita.hr.deduction.dto.CreateLoanRequest;
import com.kita.hr.deduction.dto.DeductionRuleResponse;
import com.kita.hr.deduction.dto.LoanResponse;
import com.kita.hr.employee.EmployeeService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeductionController {

  private final DeductionService deductions;
  private final LoanService loans;
  private final EmployeeService employees;
  private final CallerContext caller;

  public DeductionController(
      DeductionService deductions,
      LoanService loans,
      EmployeeService employees,
      CallerContext caller) {
    this.deductions = deductions;
    this.loans = loans;
    this.employees = employees;
    this.caller = caller;
  }

  @GetMapping("/api/hr/deduction-rules")
  public List<DeductionRuleResponse> list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER);
    LocalDate date = asOf != null ? asOf : LocalDate.now();
    return deductions.effectiveRules(date).stream().map(DeductionRuleResponse::from).toList();
  }

  @PostMapping("/api/hr/deduction-rules")
  @ResponseStatus(HttpStatus.CREATED)
  public DeductionRuleResponse create(@Valid @RequestBody CreateDeductionRuleRequest req) {
    caller.require(Role.HR_ADMIN);
    DeductionRule rule =
        new DeductionRule(
            req.code(),
            req.kind(),
            req.computation(),
            req.base(),
            req.agency(),
            req.rate(),
            req.employerRate(),
            req.fixedAmount(),
            req.floor(),
            req.cap(),
            req.effectiveDate());
    List<DeductionRuleRow> rows = new ArrayList<>();
    if (req.rows() != null) {
      for (CreateDeductionRuleRequest.RowRequest r : req.rows()) {
        rows.add(
            new DeductionRuleRow(
                null, r.low(), r.high(), r.employeeAmount(), r.employerAmount(), r.baseTax(),
                r.rateOnExcess(), r.excessOver()));
      }
    }
    return DeductionRuleResponse.from(deductions.createRule(rule, rows));
  }

  @PostMapping("/api/hr/employees/{id}/loans")
  @ResponseStatus(HttpStatus.CREATED)
  public LoanResponse createLoan(@PathVariable UUID id, @Valid @RequestBody CreateLoanRequest req) {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER);
    employees.get(id); // 404 if the employee does not exist
    Loan loan =
        loans.create(
            new Loan(id, req.principal(), req.installmentAmount(), req.installmentsTotal()),
            actor());
    return LoanResponse.from(loan);
  }

  private String actor() {
    return caller.employeeId().map(UUID::toString).orElse("system");
  }
}
