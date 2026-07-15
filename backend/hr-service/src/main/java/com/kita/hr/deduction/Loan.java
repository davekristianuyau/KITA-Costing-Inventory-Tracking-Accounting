package com.kita.hr.deduction;

import com.kita.hr.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** An employee loan/advance drawn down by one installment per finalized payroll run. */
@Entity
@Table(name = "loan")
public class Loan {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal principal;

  @Column(name = "installment_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal installmentAmount;

  @Column(name = "installments_total", nullable = false)
  private int installmentsTotal;

  @Column(name = "installments_paid", nullable = false)
  private int installmentsPaid;

  @Column(name = "outstanding_balance", nullable = false, precision = 19, scale = 2)
  private BigDecimal outstandingBalance;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LoanStatus status = LoanStatus.ACTIVE;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Loan() {}

  public Loan(UUID employeeId, BigDecimal principal, BigDecimal installmentAmount, int installmentsTotal) {
    this.employeeId = employeeId;
    this.principal = Money.round(principal);
    this.installmentAmount = Money.round(installmentAmount);
    this.installmentsTotal = installmentsTotal;
    this.installmentsPaid = 0;
    this.outstandingBalance = Money.round(principal);
    this.status = LoanStatus.ACTIVE;
  }

  /** Amount to deduct this run: the installment, capped at the outstanding balance. */
  public BigDecimal currentInstallment() {
    return installmentAmount.min(outstandingBalance);
  }

  /** Apply a deducted amount, settling the loan when the balance reaches zero. */
  public void applyPayment(BigDecimal amount) {
    this.outstandingBalance = Money.round(outstandingBalance.subtract(amount));
    this.installmentsPaid++;
    if (outstandingBalance.signum() <= 0) {
      this.outstandingBalance = Money.zero();
      this.status = LoanStatus.SETTLED;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  public BigDecimal getInstallmentAmount() {
    return installmentAmount;
  }

  public int getInstallmentsTotal() {
    return installmentsTotal;
  }

  public int getInstallmentsPaid() {
    return installmentsPaid;
  }

  public BigDecimal getOutstandingBalance() {
    return outstandingBalance;
  }

  public LoanStatus getStatus() {
    return status;
  }
}
