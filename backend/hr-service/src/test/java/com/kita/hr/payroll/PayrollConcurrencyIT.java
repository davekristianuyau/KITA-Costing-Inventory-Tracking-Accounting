package com.kita.hr.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.hr.common.ConflictException;
import com.kita.hr.employee.CompensationRequest;
import com.kita.hr.employee.CreateEmployeeRequest;
import com.kita.hr.employee.EmployeeService;
import com.kita.hr.employee.EmploymentType;
import com.kita.hr.employee.PayFrequency;
import com.kita.hr.payroll.dto.CreatePayrollRunRequest;
import com.kita.hr.payroll.dto.PayPeriodRequest;
import com.kita.hr.support.AbstractHrIT;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T021: a run cannot be finalized twice, even under concurrent finalize (no double-pay). */
class PayrollConcurrencyIT extends AbstractHrIT {

  @Autowired private EmployeeService employeeService;
  @Autowired private PayrollRunService payroll;

  @Test
  void concurrentFinalizeAllowsExactlyOneSuccess() throws Exception {
    UUID empId =
        employeeService
            .create(
                new CreateEmployeeRequest(
                    "E-C1", "C", "One", null, null, null, EmploymentType.REGULAR, null,
                    LocalDate.of(2025, 1, 1), null, null, null, null),
                "setup")
            .getId();
    employeeService.addCompensation(
        empId,
        new CompensationRequest(
            LocalDate.of(2026, 1, 1), new BigDecimal("30000.00"), PayFrequency.MONTHLY),
        "setup");

    CreatePayrollRunRequest req =
        new CreatePayrollRunRequest(
            new PayPeriodRequest(
                PayFrequency.MONTHLY,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 1, 31)),
            RunType.REGULAR,
            null,
            null);
    UUID runId = payroll.create(req, "setup").getId();
    payroll.compute(runId, "setup");

    ExecutorService pool = Executors.newFixedThreadPool(2);
    AtomicInteger ok = new AtomicInteger();
    AtomicInteger conflict = new AtomicInteger();
    Callable<Void> finalizeTask =
        () -> {
          try {
            payroll.finalizeRun(runId, "t");
            ok.incrementAndGet();
          } catch (ConflictException ex) {
            conflict.incrementAndGet();
          }
          return null;
        };
    Future<Void> f1 = pool.submit(finalizeTask);
    Future<Void> f2 = pool.submit(finalizeTask);
    f1.get();
    f2.get();
    pool.shutdown();

    assertThat(ok.get()).isEqualTo(1);
    assertThat(conflict.get()).isEqualTo(1);
    assertThat(payroll.get(runId).getStatus()).isEqualTo(RunStatus.FINALIZED);
  }
}
