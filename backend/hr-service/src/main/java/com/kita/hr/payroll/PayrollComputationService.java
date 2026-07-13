package com.kita.hr.payroll;

import com.kita.hr.common.EffectiveDated;
import com.kita.hr.common.Money;
import com.kita.hr.deduction.DeductionCalculator;
import com.kita.hr.deduction.DeductionService;
import com.kita.hr.deduction.Loan;
import com.kita.hr.deduction.LoanService;
import com.kita.hr.employee.CompensationRecord;
import com.kita.hr.employee.CompensationRecordRepository;
import com.kita.hr.employee.Employee;
import com.kita.hr.employee.EmployeeRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes payslips for a run: gross = pro-rated basic; then statutory + tax deductions (via the
 * effective rule engine) and loan installments; net = gross − employee deductions. Employees with no
 * effective compensation, not active in the period, or whose net would fall below the floor are
 * flagged and excluded (FR-010/015) rather than producing an incorrect payslip.
 */
@Service
public class PayrollComputationService {

  private final EmployeeRepository employees;
  private final CompensationRecordRepository compensations;
  private final PayslipRepository payslips;
  private final PayComponentRepository components;
  private final DeductionService deductions;
  private final LoanService loans;
  private final BigDecimal netFloor;

  public PayrollComputationService(
      EmployeeRepository employees,
      CompensationRecordRepository compensations,
      PayslipRepository payslips,
      PayComponentRepository components,
      DeductionService deductions,
      LoanService loans,
      @Value("${hr.payroll.net-floor:0}") BigDecimal netFloor) {
    this.employees = employees;
    this.compensations = compensations;
    this.payslips = payslips;
    this.components = components;
    this.deductions = deductions;
    this.loans = loans;
    this.netFloor = netFloor;
  }

  public record Outcome(int payslipCount, List<String> flagged) {}

  @Transactional
  public Outcome compute(PayrollRun run) {
    clearExisting(run.getId());
    PayPeriod period = run.getPayPeriod();
    List<String> flagged = new ArrayList<>();
    int count = 0;

    for (Employee e : employees.findAll()) {
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

      // Deductions: statutory + tax (engine), then loan installments.
      DeductionCalculator.Outcome ded = deductions.compute(gross, gross, period.getEndDate());
      List<DeductionLine> lines = new ArrayList<>();
      lines.add(new DeductionLine(PayComponentCategory.EARNING, "BASIC", "Basic pay", gross, "pro-rated basic"));
      for (DeductionCalculator.Line l : ded.lines()) {
        lines.add(new DeductionLine(l.category(), l.code(), l.label(), l.amount(), l.basis()));
      }
      List<BigDecimal> loanAmounts = new ArrayList<>();
      for (Loan loan : loans.activeLoans(e.getId())) {
        BigDecimal inst = loan.currentInstallment();
        if (inst.signum() > 0) {
          lines.add(
              new DeductionLine(
                  PayComponentCategory.VOLUNTARY_DEDUCTION,
                  LoanService.LOAN_CODE_PREFIX + loan.getId(),
                  "Loan installment",
                  inst,
                  "loan"));
          loanAmounts.add(inst);
        }
      }

      BigDecimal totalEmployeeDeductions =
          Money.round(ded.totalEmployeeDeductions().add(Money.sum(loanAmounts)));
      BigDecimal totalEmployerContrib = ded.totalEmployerContrib();
      BigDecimal net = Money.round(gross.subtract(totalEmployeeDeductions));

      if (net.compareTo(netFloor) < 0) {
        flagged.add(e.getEmployeeNo() + ": net below floor (review deductions)");
        continue;
      }

      Payslip slip =
          payslips.save(
              new Payslip(run.getId(), e.getId(), gross, totalEmployeeDeductions, totalEmployerContrib, net));
      for (DeductionLine l : lines) {
        components.save(
            new PayComponent(slip.getId(), l.category(), l.code(), l.label(), l.amount(), l.basis()));
      }
      count++;
    }
    return new Outcome(count, flagged);
  }

  private record DeductionLine(
      PayComponentCategory category, String code, String label, BigDecimal amount, String basis) {}

  private void clearExisting(UUID runId) {
    for (Payslip slip : payslips.findByPayrollRunId(runId)) {
      components.findByPayslipId(slip.getId()).forEach(components::delete);
    }
    payslips.deleteByPayrollRunId(runId);
  }
}
