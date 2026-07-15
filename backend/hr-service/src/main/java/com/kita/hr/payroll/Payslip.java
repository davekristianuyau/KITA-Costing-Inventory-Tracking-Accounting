package com.kita.hr.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** One employee's computed result for a run. Figures are immutable once the run is FINALIZED. */
@Entity
@Table(name = "payslip")
public class Payslip {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "payroll_run_id", nullable = false)
  private UUID payrollRunId;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal gross;

  @Column(name = "total_deductions", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalDeductions;

  @Column(name = "total_employer_contrib", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalEmployerContrib;

  @Column(name = "net_pay", nullable = false, precision = 19, scale = 2)
  private BigDecimal netPay;

  protected Payslip() {}

  public Payslip(
      UUID payrollRunId,
      UUID employeeId,
      BigDecimal gross,
      BigDecimal totalDeductions,
      BigDecimal totalEmployerContrib,
      BigDecimal netPay) {
    this.payrollRunId = payrollRunId;
    this.employeeId = employeeId;
    this.gross = gross;
    this.totalDeductions = totalDeductions;
    this.totalEmployerContrib = totalEmployerContrib;
    this.netPay = netPay;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPayrollRunId() {
    return payrollRunId;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  public BigDecimal getGross() {
    return gross;
  }

  public BigDecimal getTotalDeductions() {
    return totalDeductions;
  }

  public BigDecimal getTotalEmployerContrib() {
    return totalEmployerContrib;
  }

  public BigDecimal getNetPay() {
    return netPay;
  }
}
