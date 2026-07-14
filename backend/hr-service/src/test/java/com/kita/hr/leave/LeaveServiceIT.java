package com.kita.hr.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.hr.common.ConflictException;
import com.kita.hr.employee.CompensationRequest;
import com.kita.hr.employee.CreateEmployeeRequest;
import com.kita.hr.employee.EmployeeService;
import com.kita.hr.employee.EmploymentType;
import com.kita.hr.employee.PayFrequency;
import com.kita.hr.payroll.PayrollRunService;
import com.kita.hr.payroll.RunType;
import com.kita.hr.payroll.dto.CreatePayrollRunRequest;
import com.kita.hr.payroll.dto.PayComponentResponse;
import com.kita.hr.payroll.dto.PayPeriodRequest;
import com.kita.hr.payroll.dto.PayslipResponse;
import com.kita.hr.support.AbstractHrIT;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T047: leave balance draw-down, overlap rejection, over-draw guard, and unpaid-leave pay reduction
 * (FR-018/019/020, SC).
 */
class LeaveServiceIT extends AbstractHrIT {

  @Autowired private EmployeeService employees;
  @Autowired private LeaveService leave;
  @Autowired private PayrollRunService payroll;

  private UUID employee(String no) {
    return employees
        .create(
            new CreateEmployeeRequest(
                no, "Le", "Ave", null, null, null, EmploymentType.REGULAR, null,
                LocalDate.of(2025, 1, 1), null, null, null, null),
            "setup")
        .getId();
  }

  @Test
  void approvedLeaveDrawsDownBalance() {
    UUID emp = employee("LV-1");
    UUID type =
        leave
            .createType(
                new LeaveType(
                    "VL", "Vacation", PayTreatment.PAID, new BigDecimal("1.25"),
                    AccrualPeriod.MONTHLY, null, false),
                "hr")
            .getId();
    leave.accrue(emp, type, 10, "hr"); // 12.50 days

    LeaveRequest req =
        leave.file(
            new LeaveRequest(
                emp, type, LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 6),
                new BigDecimal("3"), "trip"),
            emp.toString());
    leave.decide(req.getId(), true, "mgr");

    assertThat(leave.balance(emp, type).orElseThrow().getBalance())
        .isEqualByComparingTo("9.50");
  }

  @Test
  void overlappingApprovedLeaveIsRejected() {
    UUID emp = employee("LV-2");
    UUID type =
        leave
            .createType(
                new LeaveType(
                    "VL", "Vacation", PayTreatment.PAID, new BigDecimal("1.25"),
                    AccrualPeriod.MONTHLY, null, true),
                "hr")
            .getId();

    LeaveRequest first =
        leave.file(
            new LeaveRequest(
                emp, type, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12),
                new BigDecimal("3"), null),
            emp.toString());
    leave.decide(first.getId(), true, "mgr");

    assertThatThrownBy(
            () ->
                leave.file(
                    new LeaveRequest(
                        emp, type, LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 13),
                        new BigDecimal("3"), null),
                    emp.toString()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void overDrawWithoutNegativeAllowanceIsRejected() {
    UUID emp = employee("LV-3");
    UUID type =
        leave
            .createType(
                new LeaveType(
                    "VL", "Vacation", PayTreatment.PAID, new BigDecimal("1.25"),
                    AccrualPeriod.MONTHLY, null, false),
                "hr")
            .getId();
    leave.accrue(emp, type, 1, "hr"); // 1.25 days only

    LeaveRequest req =
        leave.file(
            new LeaveRequest(
                emp, type, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5),
                new BigDecimal("5"), null),
            emp.toString());

    assertThatThrownBy(() -> leave.decide(req.getId(), true, "mgr"))
        .isInstanceOf(ConflictException.class);
    // request stays FILED, balance untouched
    assertThat(leave.balance(emp, type).orElseThrow().getBalance())
        .isEqualByComparingTo("1.25");
  }

  @Test
  void approvedUnpaidLeaveReducesPeriodPay() {
    UUID emp = employee("LV-4");
    employees.addCompensation(
        emp,
        new CompensationRequest(
            LocalDate.of(2026, 1, 1), new BigDecimal("30000.00"), PayFrequency.MONTHLY),
        "hr");
    UUID lwop =
        leave
            .createType(
                new LeaveType(
                    "LWOP", "Leave w/o pay", PayTreatment.UNPAID, BigDecimal.ZERO,
                    AccrualPeriod.MONTHLY, null, true),
                "hr")
            .getId();

    // 5 unpaid days inside a 31-day January period.
    LeaveRequest req =
        leave.file(
            new LeaveRequest(
                emp, lwop, LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 10),
                new BigDecimal("5"), null),
            emp.toString());
    leave.decide(req.getId(), true, "mgr");

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

    PayslipResponse slip =
        payroll.payslipsForRun(runId).stream()
            .filter(s -> s.employeeId().equals(emp))
            .findFirst()
            .orElseThrow();

    // 30000 - (30000 * 5/31) = 30000 - 4838.71 = 25161.29
    assertThat(slip.gross()).isEqualByComparingTo("25161.29");
    BigDecimal basic =
        slip.components().stream()
            .filter(c -> c.code().equals("BASIC"))
            .map(PayComponentResponse::amount)
            .findFirst()
            .orElseThrow();
    assertThat(basic).isEqualByComparingTo("25161.29");
  }
}
