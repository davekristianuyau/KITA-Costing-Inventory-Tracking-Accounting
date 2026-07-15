package com.kita.hr.deduction;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.hr.employee.CompensationRequest;
import com.kita.hr.employee.CreateEmployeeRequest;
import com.kita.hr.employee.EmployeeService;
import com.kita.hr.employee.EmploymentType;
import com.kita.hr.employee.PayFrequency;
import com.kita.hr.payroll.PayrollRunService;
import com.kita.hr.payroll.RunType;
import com.kita.hr.payroll.dto.ComputeResultResponse;
import com.kita.hr.payroll.dto.CreatePayrollRunRequest;
import com.kita.hr.payroll.dto.PayPeriodRequest;
import com.kita.hr.support.AbstractHrIT;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec Edge Case: a statutory table with no range covering an employee's base must flag the
 * employee, not silently zero-rate them.
 *
 * <p>NOTE: deduction_rule is intentionally not truncated between tests (it holds the PH seed), so
 * the gap rule created here is removed explicitly — otherwise it would leak into every other test.
 */
class UnmatchedStatutoryRangeIT extends AbstractHrIT {

  @Autowired private EmployeeService employees;
  @Autowired private PayrollRunService payroll;
  @Autowired private DeductionService deductions;
  @Autowired private DeductionRuleRepository rules;
  @Autowired private DeductionRuleRowRepository rows;

  private UUID gapRuleId;

  @AfterEach
  void removeGapRule() {
    if (gapRuleId != null) {
      rows.findByRuleId(gapRuleId).forEach(rows::delete);
      rules.findById(gapRuleId).ifPresent(rules::delete);
      gapRuleId = null;
    }
  }

  @Test
  void employeeOutsideEveryStatutoryRangeIsFlaggedNotZeroRated() {
    // A TABLE rule that only covers 0..1000 — a 30k salary falls in no row.
    DeductionRule gap =
        new DeductionRule(
            "GAP_TEST", DeductionKind.STATUTORY, Computation.TABLE, DeductionBase.GROSS,
            "GapAgency", null, null, null, null, null, LocalDate.of(2024, 1, 1));
    gapRuleId =
        deductions
            .createRule(
                gap,
                List.of(
                    new DeductionRuleRow(
                        null, new BigDecimal("0"), new BigDecimal("1000"),
                        new BigDecimal("10.00"), new BigDecimal("10.00"), null, null, null)))
            .getId();

    UUID id =
        employees
            .create(
                new CreateEmployeeRequest(
                    "GAP-1", "Ga", "Ap", null, null, null, EmploymentType.REGULAR, null,
                    LocalDate.of(2025, 1, 1), null, null, null, null),
                "setup")
            .getId();
    employees.addCompensation(
        id,
        new CompensationRequest(
            LocalDate.of(2026, 1, 1), new BigDecimal("30000.00"), PayFrequency.MONTHLY),
        "setup");

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
                    List.of(id)),
                "po")
            .getId();
    ComputeResultResponse result = payroll.compute(runId, "po");

    assertThat(result.flagged()).anySatisfy(f -> assertThat(f).contains("GAP-1", "GAP_TEST"));
    assertThat(result.payslipCount()).isZero();
    assertThat(payroll.payslipsForRun(runId)).isEmpty();
  }
}
