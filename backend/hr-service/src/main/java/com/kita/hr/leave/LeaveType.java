package com.kita.hr.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A category of leave with its pay treatment and accrual policy (rate, period, optional cap). */
@Entity
@Table(name = "leave_type")
public class LeaveType {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "pay_treatment", nullable = false)
  private PayTreatment payTreatment;

  @Column(name = "accrual_rate", nullable = false, precision = 6, scale = 2)
  private BigDecimal accrualRate;

  @Enumerated(EnumType.STRING)
  @Column(name = "accrual_period", nullable = false)
  private AccrualPeriod accrualPeriod;

  @Column(name = "accrual_cap", precision = 6, scale = 2)
  private BigDecimal accrualCap;

  @Column(name = "allow_negative", nullable = false)
  private boolean allowNegative;

  protected LeaveType() {}

  public LeaveType(
      String code,
      String name,
      PayTreatment payTreatment,
      BigDecimal accrualRate,
      AccrualPeriod accrualPeriod,
      BigDecimal accrualCap,
      boolean allowNegative) {
    this.code = code;
    this.name = name;
    this.payTreatment = payTreatment;
    this.accrualRate = accrualRate;
    this.accrualPeriod = accrualPeriod;
    this.accrualCap = accrualCap;
    this.allowNegative = allowNegative;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public PayTreatment getPayTreatment() {
    return payTreatment;
  }

  public BigDecimal getAccrualRate() {
    return accrualRate;
  }

  public AccrualPeriod getAccrualPeriod() {
    return accrualPeriod;
  }

  public BigDecimal getAccrualCap() {
    return accrualCap;
  }

  public boolean isAllowNegative() {
    return allowNegative;
  }
}
