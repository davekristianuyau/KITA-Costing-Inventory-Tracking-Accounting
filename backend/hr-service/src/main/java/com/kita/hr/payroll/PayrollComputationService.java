package com.kita.hr.payroll;

import com.kita.hr.common.EffectiveDated;
import com.kita.hr.employee.CompensationRecord;
import com.kita.hr.employee.CompensationRecordRepository;
import com.kita.hr.employee.Employee;
import com.kita.hr.employee.EmployeeRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes payslips for a run. US2: gross = pro-rated basic; no deductions yet (net = gross).
 * Employees with no effective compensation or not active in the period are flagged and excluded
 * (FR-010) rather than producing an incorrect payslip.
 */
@Service
public class PayrollComputationService {

  private final EmployeeRepository employees;
  private final CompensationRecordRepository compensations;
  private final PayslipRepository payslips;
  private final PayComponentRepository components;

  public PayrollComputationService(
      EmployeeRepository employees,
      CompensationRecordRepository compensations,
      PayslipRepository payslips,
      PayComponentRepository components) {
    this.employees = employees;
    this.compensations = compensations;
    this.payslips = payslips;
    this.components = components;
  }

  /** Result of a compute pass: how many payslips were produced and which employees were flagged. */
  public record Outcome(int payslipCount, List<String> flagged) {}

  @Transactional
  public Outcome compute(PayrollRun run) {
    clearExisting(run.getId());

    PayPeriod period = run.getPayPeriod();
    List<String> flagged = new ArrayList<>();
    int count = 0;

    for (Employee e : employees.findAll()) {
      // Separated before the period starts → not payable this period.
      if (e.getDateSeparated() != null && e.getDateSeparated().isBefore(period.getStartDate())) {
        continue;
      }
      Optional<CompensationRecord> comp =
          EffectiveDated.effectiveAsOf(
              compensations.findByEmployeeIdOrderByEffectiveDateDesc(e.getId()),
              period.getEndDate());
      if (comp.isEmpty()) {
        flagged.add(e.getEmployeeNo() + ": no effective compensation");
        continue;
      }
      BigDecimal gross =
          PayrollCalculator.grossBasic(
              comp.get().getBasicPay(),
              period.getFrequency(),
              period.getStartDate(),
              period.getEndDate(),
              e.getDateHired(),
              e.getDateSeparated());
      if (gross.signum() <= 0) {
        flagged.add(e.getEmployeeNo() + ": not active in period");
        continue;
      }
      Payslip slip =
          payslips.save(
              new Payslip(
                  run.getId(),
                  e.getId(),
                  gross,
                  com.kita.hr.common.Money.zero(),
                  com.kita.hr.common.Money.zero(),
                  gross));
      components.save(
          new PayComponent(
              slip.getId(),
              PayComponentCategory.EARNING,
              "BASIC",
              "Basic pay",
              gross,
              "pro-rated basic"));
      count++;
    }
    return new Outcome(count, flagged);
  }

  private void clearExisting(java.util.UUID runId) {
    for (Payslip slip : payslips.findByPayrollRunId(runId)) {
      components.findByPayslipId(slip.getId()).forEach(components::delete);
    }
    payslips.deleteByPayrollRunId(runId);
  }
}
