package com.kita.hr.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.hr.employee.CompensationRequest;
import com.kita.hr.employee.CreateEmployeeRequest;
import com.kita.hr.employee.EmployeeService;
import com.kita.hr.employee.EmploymentType;
import com.kita.hr.employee.PayFrequency;
import com.kita.hr.payroll.dto.ComputeResultResponse;
import com.kita.hr.payroll.dto.CreatePayrollRunRequest;
import com.kita.hr.payroll.dto.PayPeriodRequest;
import com.kita.hr.payroll.dto.PayslipResponse;
import com.kita.hr.payroll.dto.RegisterResponse;
import com.kita.hr.support.AbstractHrIT;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The two spec claims that had no task and so were never verified:
 *
 * <ul>
 *   <li><b>Edge case</b> — a run for a period with no eligible employees produces an empty,
 *       finalizable run with zero totals, not an error.
 *   <li><b>SC-001</b> — a 100-employee period computes, finalizes and reconciles in one session.
 * </ul>
 */
class PayrollScaleAndEdgeCaseIT extends AbstractHrIT {

  @Autowired private EmployeeService employees;
  @Autowired private PayrollRunService payroll;

  private static final LocalDate START = LocalDate.of(2026, 1, 1);
  private static final LocalDate END = LocalDate.of(2026, 1, 31);

  private UUID run() {
    return payroll
        .create(
            new CreatePayrollRunRequest(
                new PayPeriodRequest(PayFrequency.MONTHLY, START, END, END), RunType.REGULAR, null, null),
            "officer")
        .getId();
  }

  /** Spec edge case: no eligible employees is an empty run, never an error. */
  @Test
  void aRunWithNoEligibleEmployeesIsEmptyAndStillFinalizable() {
    UUID runId = run();

    ComputeResultResponse result = payroll.compute(runId, "officer");
    assertThat(result.payslipCount()).isZero();
    assertThat(result.flagged()).isEmpty();

    // The point of the edge case: it finalizes rather than blowing up.
    PayrollRun finalized = payroll.finalizeRun(runId, "officer");
    assertThat(finalized.getStatus()).isEqualTo(RunStatus.FINALIZED);

    RegisterResponse register = payroll.register(runId);
    assertThat(register.employeeCount()).isZero();
    assertThat(register.totalGross()).isEqualByComparingTo("0.00");
    assertThat(register.totalNet()).isEqualByComparingTo("0.00");
    assertThat(register.payslips()).isEmpty();
  }

  /** An employee separated before the period starts is not eligible, leaving the run empty. */
  @Test
  void anEmployeeSeparatedBeforeThePeriodDoesNotMakeTheRunNonEmpty() {
    UUID id =
        employees
            .create(
                new CreateEmployeeRequest(
                    "SEP-1", "Sep", "Arated", null, null, null, EmploymentType.REGULAR, null,
                    LocalDate.of(2024, 1, 1), null, null, null, null),
                "hr")
            .getId();
    employees.addCompensation(
        id, new CompensationRequest(LocalDate.of(2024, 1, 1), new BigDecimal("30000.00"), PayFrequency.MONTHLY), "hr");
    employees.update(
        id,
        new com.kita.hr.employee.UpdateEmployeeRequest(
            null, null, null, null, null, com.kita.hr.employee.EmployeeStatus.SEPARATED,
            LocalDate.of(2025, 12, 31), null, null, null, null),
        "hr");

    UUID runId = run();
    assertThat(payroll.compute(runId, "officer").payslipCount()).isZero();
    assertThat(payroll.finalizeRun(runId, "officer").getStatus()).isEqualTo(RunStatus.FINALIZED);
  }

  /**
   * SC-001: 100 employees compute, finalize and reconcile in a single session.
   *
   * <p>The timing bound is deliberately generous — this guards against an accidental O(n²) or an
   * N+1 blow-up, not against a slow CI box. The reconciliation assertion is the real contract.
   */
  @Test
  void aHundredEmployeePeriodComputesFinalizesAndReconciles() {
    for (int i = 0; i < 100; i++) {
      UUID id =
          employees
              .create(
                  new CreateEmployeeRequest(
                      "SC1-" + i, "Emp", String.valueOf(i), null, null, null,
                      EmploymentType.REGULAR, null, LocalDate.of(2025, 1, 1), null, null, null, null),
                  "hr")
              .getId();
      employees.addCompensation(
          id,
          new CompensationRequest(START, new BigDecimal("30000.00"), PayFrequency.MONTHLY),
          "hr");
    }

    UUID runId = run();
    Instant started = Instant.now();
    ComputeResultResponse result = payroll.compute(runId, "officer");
    payroll.finalizeRun(runId, "officer");
    Duration elapsed = Duration.between(started, Instant.now());

    assertThat(result.payslipCount()).isEqualTo(100);
    assertThat(result.flagged()).isEmpty();

    // SC-002 must still hold at scale: the register reconciles to the payslips, to the cent.
    RegisterResponse register = payroll.register(runId);
    assertThat(register.employeeCount()).isEqualTo(100);

    BigDecimal slipGross = BigDecimal.ZERO;
    BigDecimal slipNet = BigDecimal.ZERO;
    for (PayslipResponse s : register.payslips()) {
      slipGross = slipGross.add(s.gross());
      slipNet = slipNet.add(s.netPay());
    }
    assertThat(slipGross).isEqualByComparingTo(register.totalGross());
    assertThat(slipNet).isEqualByComparingTo(register.totalNet());
    assertThat(register.totalGross().subtract(register.totalDeductions()))
        .isEqualByComparingTo(register.totalNet());

    assertThat(elapsed)
        .as("100-employee run took %s; SC-001 expects a single interactive session", elapsed)
        .isLessThan(Duration.ofSeconds(60));
  }
}
