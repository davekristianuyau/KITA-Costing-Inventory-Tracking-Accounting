package com.kita.hr.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.hr.employee.CompensationRequest;
import com.kita.hr.employee.CreateEmployeeRequest;
import com.kita.hr.employee.EmployeeService;
import com.kita.hr.employee.EmploymentType;
import com.kita.hr.employee.PayFrequency;
import com.kita.hr.leave.AccrualPeriod;
import com.kita.hr.leave.LeaveRequest;
import com.kita.hr.leave.LeaveService;
import com.kita.hr.leave.LeaveType;
import com.kita.hr.leave.PayTreatment;
import com.kita.hr.payroll.PayrollRunService;
import com.kita.hr.payroll.RunType;
import com.kita.hr.payroll.dto.CreatePayrollRunRequest;
import com.kita.hr.payroll.dto.PayPeriodRequest;
import com.kita.hr.support.AbstractHrIT;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * FR-023 / SC-007: every payroll finalize and leave approval is attributable to a user AND a
 * timestamp.
 *
 * <p>The service wrote these from the start, but nothing verified them — an audit trail nobody tests
 * is exactly what silently rots, and it is only consulted at the moment it must be right.
 */
class AuditTrailIT extends AbstractHrIT {

  @Autowired private EmployeeService employees;
  @Autowired private PayrollRunService payroll;
  @Autowired private LeaveService leave;
  @Autowired private AuditEventRepository audit;

  private static final LocalDate START = LocalDate.of(2026, 1, 1);
  private static final LocalDate END = LocalDate.of(2026, 1, 31);

  private UUID employee(String no) {
    UUID id =
        employees
            .create(
                new CreateEmployeeRequest(
                    no, "Au", "Dit", null, null, null, EmploymentType.REGULAR, null,
                    LocalDate.of(2025, 1, 1), null, null, null, null),
                "hr-alice")
            .getId();
    employees.addCompensation(
        id, new CompensationRequest(START, new BigDecimal("30000.00"), PayFrequency.MONTHLY), "hr-alice");
    return id;
  }

  private List<AuditEvent> eventsOf(String action) {
    return audit.findAll().stream().filter(e -> e.getAction().equals(action)).toList();
  }

  /** SC-007: a finalize names the officer who did it and when. */
  @Test
  void payrollFinalizeIsAttributableToAUserAndTimestamp() {
    employee("AU-1");
    Instant before = Instant.now().minusSeconds(1);

    UUID runId =
        payroll
            .create(
                new CreatePayrollRunRequest(
                    new PayPeriodRequest(PayFrequency.MONTHLY, START, END, END), RunType.REGULAR,
                    null, null),
                "officer-bob")
            .getId();
    payroll.compute(runId, "officer-bob");
    payroll.finalizeRun(runId, "officer-carol");

    List<AuditEvent> finalized = eventsOf("PAYROLL_RUN_FINALIZED");
    assertThat(finalized).hasSize(1);
    assertThat(finalized.get(0).getActor()).isEqualTo("officer-carol");
    assertThat(finalized.get(0).getEntityRef()).isEqualTo(runId.toString());
    assertThat(finalized.get(0).getAt()).isAfter(before).isBeforeOrEqualTo(Instant.now());

    // The whole run lifecycle is attributable, not just the finalize.
    assertThat(eventsOf("PAYROLL_RUN_CREATED").get(0).getActor()).isEqualTo("officer-bob");
    assertThat(eventsOf("PAYROLL_RUN_COMPUTED").get(0).getActor()).isEqualTo("officer-bob");
  }

  /** SC-007: a leave approval names the manager who decided it. */
  @Test
  void leaveApprovalIsAttributableToAUserAndTimestamp() {
    UUID emp = employee("AU-2");
    UUID type =
        leave
            .createType(
                new LeaveType(
                    "VL", "Vacation", PayTreatment.PAID, new BigDecimal("1.25"),
                    AccrualPeriod.MONTHLY, null, true),
                "hr-alice")
            .getId();
    LeaveRequest req =
        leave.file(
            new LeaveRequest(
                emp, type, LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 5), new BigDecimal("2"), null),
            emp.toString());
    Instant before = Instant.now().minusSeconds(1);

    leave.decide(req.getId(), true, "manager-dave");

    List<AuditEvent> approved = eventsOf("LEAVE_APPROVED");
    assertThat(approved).hasSize(1);
    assertThat(approved.get(0).getActor()).isEqualTo("manager-dave");
    assertThat(approved.get(0).getEntityRef()).isEqualTo(req.getId().toString());
    assertThat(approved.get(0).getAt()).isAfter(before);
  }

  /** A rejection is equally attributable — the trail must not only record happy paths. */
  @Test
  void leaveRejectionIsAlsoAttributable() {
    UUID emp = employee("AU-3");
    UUID type =
        leave
            .createType(
                new LeaveType(
                    "SL", "Sick", PayTreatment.PAID, new BigDecimal("1.25"), AccrualPeriod.MONTHLY,
                    null, true),
                "hr-alice")
            .getId();
    LeaveRequest req =
        leave.file(
            new LeaveRequest(
                emp, type, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), BigDecimal.ONE, null),
            emp.toString());

    leave.decide(req.getId(), false, "manager-erin");

    assertThat(eventsOf("LEAVE_REJECTED")).hasSize(1);
    assertThat(eventsOf("LEAVE_REJECTED").get(0).getActor()).isEqualTo("manager-erin");
  }

  /** FR-004: a statutory/tax identifier must never reach the audit trail. */
  @Test
  void auditDetailNeverCarriesAStatutoryIdentifier() {
    employees.create(
        new CreateEmployeeRequest(
            "AU-4", "Se", "Cret", null, null, null, EmploymentType.REGULAR, null,
            LocalDate.of(2025, 1, 1), "SSS-SECRET-123", null, null, "TIN-SECRET-456"),
        "hr-alice");

    assertThat(audit.findAll().toString())
        .doesNotContain("SSS-SECRET-123")
        .doesNotContain("TIN-SECRET-456");
  }

  /** Every event carries who and when — an unattributed row would defeat the trail's purpose. */
  @Test
  void everyRecordedEventCarriesAnActorAndTimestamp() {
    employee("AU-5");

    assertThat(audit.findAll()).isNotEmpty();
    assertThat(audit.findAll())
        .allSatisfy(
            e -> {
              assertThat(e.getActor()).as("actor for %s", e.getAction()).isNotBlank();
              assertThat(e.getAt()).as("timestamp for %s", e.getAction()).isNotNull();
              assertThat(e.getEntityRef()).as("entity for %s", e.getAction()).isNotBlank();
            });
  }
}
