package com.kita.hr.deduction;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.hr.employee.CompensationRequest;
import com.kita.hr.employee.CreateEmployeeRequest;
import com.kita.hr.employee.EmployeeService;
import com.kita.hr.employee.EmploymentType;
import com.kita.hr.employee.PayFrequency;
import com.kita.hr.payroll.PayrollRunService;
import com.kita.hr.payroll.RunType;
import com.kita.hr.payroll.dto.CreatePayrollRunRequest;
import com.kita.hr.payroll.dto.PayPeriodRequest;
import com.kita.hr.support.AbstractHrIT;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T031: a loan is drawn down by one installment per finalized run and settles after N runs. */
class LoanInstallmentIT extends AbstractHrIT {

  @Autowired private EmployeeService employees;
  @Autowired private LoanService loanService;
  @Autowired private LoanRepository loans;
  @Autowired private PayrollRunService payroll;

  @Test
  void loanSettlesAfterScheduledRuns() {
    UUID empId =
        employees
            .create(
                new CreateEmployeeRequest(
                    "E-L1", "L", "One", null, null, null, EmploymentType.REGULAR, null,
                    LocalDate.of(2025, 1, 1), null, null, null, null),
                "setup")
            .getId();
    employees.addCompensation(
        empId,
        new CompensationRequest(LocalDate.of(2026, 1, 1), new BigDecimal("30000.00"), PayFrequency.MONTHLY),
        "setup");
    UUID loanId =
        loanService
            .create(new Loan(empId, new BigDecimal("3000.00"), new BigDecimal("1000.00"), 3), "setup")
            .getId();

    runAndFinalize(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    assertThat(loans.findById(loanId).orElseThrow().getOutstandingBalance()).isEqualByComparingTo("2000.00");

    runAndFinalize(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    assertThat(loans.findById(loanId).orElseThrow().getOutstandingBalance()).isEqualByComparingTo("1000.00");

    runAndFinalize(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
    Loan settled = loans.findById(loanId).orElseThrow();
    assertThat(settled.getOutstandingBalance()).isEqualByComparingTo("0.00");
    assertThat(settled.getStatus()).isEqualTo(LoanStatus.SETTLED);
    assertThat(settled.getInstallmentsPaid()).isEqualTo(3);
  }

  private void runAndFinalize(LocalDate start, LocalDate end) {
    UUID runId =
        payroll
            .create(
                new CreatePayrollRunRequest(
                    new PayPeriodRequest(PayFrequency.MONTHLY, start, end, end), RunType.REGULAR, null, null),
                "t")
            .getId();
    payroll.compute(runId, "t");
    payroll.finalizeRun(runId, "t");
  }
}
