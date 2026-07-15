package com.kita.hr.deduction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A row of a TABLE or BRACKET deduction rule. */
@Entity
@Table(name = "deduction_rule_row")
public class DeductionRuleRow {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "rule_id", nullable = false)
  private UUID ruleId;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal low;

  @Column(precision = 19, scale = 2)
  private BigDecimal high;

  @Column(name = "employee_amount", precision = 19, scale = 2)
  private BigDecimal employeeAmount;

  @Column(name = "employer_amount", precision = 19, scale = 2)
  private BigDecimal employerAmount;

  @Column(name = "base_tax", precision = 19, scale = 2)
  private BigDecimal baseTax;

  @Column(name = "rate_on_excess", precision = 9, scale = 6)
  private BigDecimal rateOnExcess;

  @Column(name = "excess_over", precision = 19, scale = 2)
  private BigDecimal excessOver;

  protected DeductionRuleRow() {}

  public DeductionRuleRow(
      UUID ruleId,
      BigDecimal low,
      BigDecimal high,
      BigDecimal employeeAmount,
      BigDecimal employerAmount,
      BigDecimal baseTax,
      BigDecimal rateOnExcess,
      BigDecimal excessOver) {
    this.ruleId = ruleId;
    this.low = low;
    this.high = high;
    this.employeeAmount = employeeAmount;
    this.employerAmount = employerAmount;
    this.baseTax = baseTax;
    this.rateOnExcess = rateOnExcess;
    this.excessOver = excessOver;
  }

  public UUID getRuleId() {
    return ruleId;
  }

  public BigDecimal getLow() {
    return low;
  }

  public BigDecimal getHigh() {
    return high;
  }

  public BigDecimal getEmployeeAmount() {
    return employeeAmount;
  }

  public BigDecimal getEmployerAmount() {
    return employerAmount;
  }

  public BigDecimal getBaseTax() {
    return baseTax;
  }

  public BigDecimal getRateOnExcess() {
    return rateOnExcess;
  }

  public BigDecimal getExcessOver() {
    return excessOver;
  }

  public boolean contains(BigDecimal value) {
    boolean aboveLow = value.compareTo(low) >= 0;
    boolean belowHigh = high == null || value.compareTo(high) <= 0;
    return aboveLow && belowHigh;
  }
}
