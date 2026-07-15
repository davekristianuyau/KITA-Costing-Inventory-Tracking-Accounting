package com.kita.hr.payroll;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * FR-005: a run is scoped to a pay period AND a set of eligible employees. An explicit employee set
 * must actually restrict who is paid; an empty/absent set means everyone eligible.
 */
class PayrollRunScopeIT extends AbstractHrIT {

  @Autowired private EmployeeService employees;
  @Autowired private PayrollRunService payroll;

  private static final LocalDate START = LocalDate.of(2026, 1, 1);
  private static final LocalDate END = LocalDate.of(2026, 1, 31);

  private UUID employee(String no) {
    UUID id =
        employees
            .create(
                new CreateEmployeeRequest(
                    no, "Sc", "Ope", null, null, null, EmploymentType.REGULAR, null,
                    LocalDate.of(2025, 1, 1), null, null, null, null),
                "setup")
            .getId();
    employees.addCompensation(
        id, new CompensationRequest(START, new BigDecimal("30000.00"), PayFrequency.MONTHLY), "setup");
    return id;
  }

  private UUID run(List<UUID> employeeIds, LocalDate start, LocalDate end) {
    UUID runId =
        payroll
            .create(
                new CreatePayrollRunRequest(
                    new PayPeriodRequest(PayFrequency.MONTHLY, start, end, end),
                    RunType.REGULAR,
                    null,
                    employeeIds),
                "po")
            .getId();
    payroll.compute(runId, "po");
    return runId;
  }

  @Test
  void explicitEmployeeSetRestrictsWhoIsPaid() {
    UUID a = employee("SC-1");
    UUID b = employee("SC-2");
    employee("SC-3");

    UUID runId = run(List.of(a, b), START, END);

    List<UUID> paid = payroll.payslipsForRun(runId).stream().map(PayslipResponse::employeeId).toList();
    assertThat(paid).containsExactlyInAnyOrder(a, b);
  }

  @Test
  void absentEmployeeSetPaysEveryoneEligible() {
    UUID a = employee("SC-4");
    UUID b = employee("SC-5");

    UUID runId = run(null, START, END);

    List<UUID> paid = payroll.payslipsForRun(runId).stream().map(PayslipResponse::employeeId).toList();
    assertThat(paid).containsExactlyInAnyOrder(a, b);
  }

  @Test
  void emptyEmployeeSetPaysEveryoneEligible() {
    UUID a = employee("SC-6");

    UUID runId = run(List.of(), START, END);

    assertThat(payroll.payslipsForRun(runId)).hasSize(1);
    assertThat(payroll.payslipsForRun(runId).get(0).employeeId()).isEqualTo(a);
  }

  @Test
  void scopedRunSurvivesRecompute() {
    UUID a = employee("SC-7");
    employee("SC-8");

    UUID runId = run(List.of(a), START, END);
    payroll.compute(runId, "po"); // recompute must keep the scope, not widen it

    assertThat(payroll.payslipsForRun(runId)).hasSize(1);
    assertThat(payroll.payslipsForRun(runId).get(0).employeeId()).isEqualTo(a);
  }
}
