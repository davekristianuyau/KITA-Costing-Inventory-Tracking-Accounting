package com.kita.hr.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.hr.common.ConflictException;
import com.kita.hr.employee.CompensationRequest;
import com.kita.hr.employee.CreateEmployeeRequest;
import com.kita.hr.employee.EmployeeService;
import com.kita.hr.employee.EmploymentType;
import com.kita.hr.employee.PayFrequency;
import com.kita.hr.payroll.dto.CreatePayrollRunRequest;
import com.kita.hr.payroll.dto.PayPeriodRequest;
import com.kita.hr.payroll.dto.PayslipResponse;
import com.kita.hr.support.AbstractHrIT;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T059 (FR-011): corrections go through an ADJUSTMENT run that references a FINALIZED run; the
 * original is never mutated.
 */
class AdjustmentRunIT extends AbstractHrIT {

  @Autowired private EmployeeService employees;
  @Autowired private PayrollRunService payroll;

  private static final LocalDate START = LocalDate.of(2026, 1, 1);
  private static final LocalDate END = LocalDate.of(2026, 1, 31);

  private UUID employee(String no) {
    UUID id =
        employees
            .create(
                new CreateEmployeeRequest(
                    no, "Ad", "Just", null, null, null, EmploymentType.REGULAR, null,
                    LocalDate.of(2025, 1, 1), null, null, null, null),
                "setup")
            .getId();
    employees.addCompensation(
        id,
        new CompensationRequest(START, new BigDecimal("30000.00"), PayFrequency.MONTHLY),
        "setup");
    return id;
  }

  private UUID createRun(RunType type, UUID adjustsRunId) {
    return payroll
        .create(
            new CreatePayrollRunRequest(
                new PayPeriodRequest(PayFrequency.MONTHLY, START, END, END), type, adjustsRunId, null),
            "po")
        .getId();
  }

  private UUID finalizedRegularRun() {
    UUID runId = createRun(RunType.REGULAR, null);
    payroll.compute(runId, "po");
    payroll.finalizeRun(runId, "po");
    return runId;
  }

  @Test
  void adjustmentRunOnFinalizedRunComputesAndFinalizes() {
    employee("ADJ-1");
    UUID original = finalizedRegularRun();

    UUID adjustment = createRun(RunType.ADJUSTMENT, original);
    payroll.compute(adjustment, "po");
    PayrollRun finalized = payroll.finalizeRun(adjustment, "po");

    assertThat(finalized.getStatus()).isEqualTo(RunStatus.FINALIZED);
    assertThat(finalized.getType()).isEqualTo(RunType.ADJUSTMENT);
    assertThat(finalized.getAdjustsRunId()).isEqualTo(original);
    assertThat(payroll.payslipsForRun(adjustment)).hasSize(1);
  }

  @Test
  void adjustmentReferencingUnfinalizedRunIsRejected() {
    employee("ADJ-2");
    UUID draft = createRun(RunType.REGULAR, null);

    assertThatThrownBy(() -> createRun(RunType.ADJUSTMENT, draft))
        .isInstanceOf(ConflictException.class);

    payroll.compute(draft, "po"); // COMPUTED is still not FINALIZED
    assertThatThrownBy(() -> createRun(RunType.ADJUSTMENT, draft))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void adjustmentWithoutReferenceIsRejected() {
    employee("ADJ-3");
    assertThatThrownBy(() -> createRun(RunType.ADJUSTMENT, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void finalizedOriginalIsImmutableAndUnaffectedByItsAdjustment() {
    employee("ADJ-4");
    UUID original = finalizedRegularRun();
    PayslipResponse before = payroll.payslipsForRun(original).get(0);

    // A finalized run can be neither recomputed nor re-finalized nor cancelled.
    assertThatThrownBy(() -> payroll.compute(original, "po")).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> payroll.finalizeRun(original, "po"))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> payroll.cancel(original, "po")).isInstanceOf(ConflictException.class);

    // Running its adjustment leaves the original's figures byte-for-byte intact.
    UUID adjustment = createRun(RunType.ADJUSTMENT, original);
    payroll.compute(adjustment, "po");
    payroll.finalizeRun(adjustment, "po");

    PayslipResponse after = payroll.payslipsForRun(original).get(0);
    assertThat(after.id()).isEqualTo(before.id());
    assertThat(after.gross()).isEqualByComparingTo(before.gross());
    assertThat(after.totalDeductions()).isEqualByComparingTo(before.totalDeductions());
    assertThat(after.netPay()).isEqualByComparingTo(before.netPay());
  }
}
