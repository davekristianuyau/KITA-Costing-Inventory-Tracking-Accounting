package com.kita.hr.deduction;

import com.kita.hr.common.AuditWriter;
import com.kita.hr.payroll.PayComponent;
import com.kita.hr.payroll.PayComponentRepository;
import com.kita.hr.payroll.Payslip;
import com.kita.hr.payroll.PayslipRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages employee loans and draws them down when a payroll run is finalized. */
@Service
public class LoanService {

  /** Prefix used on a pay component code to link a deduction line back to its loan. */
  public static final String LOAN_CODE_PREFIX = "LOAN:";

  private final LoanRepository loans;
  private final PayslipRepository payslips;
  private final PayComponentRepository components;
  private final AuditWriter audit;

  public LoanService(
      LoanRepository loans,
      PayslipRepository payslips,
      PayComponentRepository components,
      AuditWriter audit) {
    this.loans = loans;
    this.payslips = payslips;
    this.components = components;
    this.audit = audit;
  }

  @Transactional
  public Loan create(Loan loan, String actor) {
    Loan saved = loans.save(loan);
    audit.record(actor, "LOAN_CREATED", saved.getId().toString(), "principal=" + loan.getOutstandingBalance());
    return saved;
  }

  @Transactional(readOnly = true)
  public List<Loan> activeLoans(UUID employeeId) {
    return loans.findByEmployeeIdAndStatus(employeeId, LoanStatus.ACTIVE);
  }

  /** Apply loan installments carried on a finalized run's payslips (one draw-down per run). */
  @Transactional
  public void settleForRun(UUID runId) {
    for (Payslip slip : payslips.findByPayrollRunId(runId)) {
      for (PayComponent c : components.findByPayslipId(slip.getId())) {
        if (c.getCode() != null && c.getCode().startsWith(LOAN_CODE_PREFIX)) {
          UUID loanId = UUID.fromString(c.getCode().substring(LOAN_CODE_PREFIX.length()));
          loans
              .findById(loanId)
              .ifPresent(
                  loan -> {
                    loan.applyPayment(c.getAmount());
                    loans.save(loan);
                  });
        }
      }
    }
  }
}
