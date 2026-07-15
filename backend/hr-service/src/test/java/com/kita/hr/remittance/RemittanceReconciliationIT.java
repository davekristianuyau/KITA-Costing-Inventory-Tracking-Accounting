package com.kita.hr.remittance;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.hr.employee.CompensationRequest;
import com.kita.hr.employee.CreateEmployeeRequest;
import com.kita.hr.employee.EmployeeService;
import com.kita.hr.employee.EmploymentType;
import com.kita.hr.employee.PayFrequency;
import com.kita.hr.payroll.PayComponentCategory;
import com.kita.hr.payroll.PayrollRunService;
import com.kita.hr.payroll.RunType;
import com.kita.hr.payroll.dto.CreatePayrollRunRequest;
import com.kita.hr.payroll.dto.PayComponentResponse;
import com.kita.hr.payroll.dto.PayPeriodRequest;
import com.kita.hr.payroll.dto.PayslipResponse;
import com.kita.hr.support.AbstractHrIT;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T053 (SC-003): each agency's remittance total equals the sum of that agency's components across
 * every payslip in the run.
 */
class RemittanceReconciliationIT extends AbstractHrIT {

  @Autowired private EmployeeService employees;
  @Autowired private PayrollRunService payroll;
  @Autowired private RemittanceService remittances;

  private UUID employee(String no, String basic) {
    UUID id =
        employees
            .create(
                new CreateEmployeeRequest(
                    no, "Re", "Mit", null, null, null, EmploymentType.REGULAR, null,
                    LocalDate.of(2025, 1, 1), null, null, null, null),
                "setup")
            .getId();
    employees.addCompensation(
        id,
        new CompensationRequest(LocalDate.of(2026, 1, 1), new BigDecimal(basic), PayFrequency.MONTHLY),
        "setup");
    return id;
  }

  @Test
  void agencyTotalsEqualSumOfComponentsAcrossPayslips() {
    // Different salaries so the employees land in different SSS/BIR brackets.
    employee("RC-1", "30000.00");
    employee("RC-2", "18000.00");
    employee("RC-3", "45000.00");

    UUID runId =
        payroll
            .create(
                new CreatePayrollRunRequest(
                    new PayPeriodRequest(
                        PayFrequency.MONTHLY,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 1, 31)),
                    RunType.REGULAR,
                    null,
                    null),
                "po")
            .getId();
    payroll.compute(runId, "po");
    payroll.finalizeRun(runId, "po");

    // Expected: fold every statutory/tax/employer component across all payslips, by agency.
    Map<String, BigDecimal> expectedEmployee = new HashMap<>();
    Map<String, BigDecimal> expectedEmployer = new HashMap<>();
    for (PayslipResponse slip : payroll.payslipsForRun(runId)) {
      for (PayComponentResponse c : slip.components()) {
        String agency = c.label(); // DeductionCalculator puts the rule's agency in the label
        if (c.category() == PayComponentCategory.STATUTORY_DEDUCTION
            || c.category() == PayComponentCategory.TAX) {
          expectedEmployee.merge(agency, c.amount(), BigDecimal::add);
        } else if (c.category() == PayComponentCategory.EMPLOYER_CONTRIB) {
          expectedEmployer.merge(agency, c.amount(), BigDecimal::add);
        }
      }
    }
    assertThat(expectedEmployee).isNotEmpty();

    RemittanceService.Summary summary = remittances.forRun(runId);
    assertThat(summary.runId()).isEqualTo(runId);
    assertThat(summary.agencies()).hasSize(4); // SSS, PhilHealth, Pag-IBIG, BIR

    BigDecimal grand = BigDecimal.ZERO;
    for (RemittanceService.AgencyLine line : summary.agencies()) {
      BigDecimal wantEmployee = expectedEmployee.getOrDefault(line.agency(), BigDecimal.ZERO);
      BigDecimal wantEmployer = expectedEmployer.getOrDefault(line.agency(), BigDecimal.ZERO);
      assertThat(line.employeeTotal())
          .as("employee remittance for %s", line.agency())
          .isEqualByComparingTo(wantEmployee);
      assertThat(line.employerTotal())
          .as("employer remittance for %s", line.agency())
          .isEqualByComparingTo(wantEmployer);
      assertThat(line.total()).isEqualByComparingTo(wantEmployee.add(wantEmployer));
      grand = grand.add(line.total());
    }
    assertThat(summary.grandTotal()).isEqualByComparingTo(grand);
  }

  @Test
  void agenciesAreReportedEvenAcrossDifferentBrackets() {
    employee("RC-10", "18000.00"); // SSS row 1 → employee 900 / employer 1900
    employee("RC-11", "30000.00"); // SSS row 3 → employee 1350 / employer 2850

    UUID runId =
        payroll
            .create(
                new CreatePayrollRunRequest(
                    new PayPeriodRequest(
                        PayFrequency.MONTHLY,
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 2, 28)),
                    RunType.REGULAR,
                    null,
                    null),
                "po")
            .getId();
    payroll.compute(runId, "po");

    RemittanceService.Summary summary = remittances.forRun(runId);
    RemittanceService.AgencyLine sss =
        summary.agencies().stream()
            .filter(a -> a.agency().equals("SSS"))
            .findFirst()
            .orElseThrow();
    assertThat(sss.employeeTotal()).isEqualByComparingTo("2250.00"); // 900 + 1350
    assertThat(sss.employerTotal()).isEqualByComparingTo("4750.00"); // 1900 + 2850
  }
}
