package com.kita.hr.deduction;

import com.kita.hr.common.EffectiveDated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A versioned, effective-dated deduction rule (statutory contribution, tax, or voluntary template). */
@Entity
@Table(name = "deduction_rule")
public class DeductionRule implements EffectiveDated {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(nullable = false)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DeductionKind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Computation computation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DeductionBase base;

  @Column private String agency;

  @Column(precision = 9, scale = 6)
  private BigDecimal rate;

  @Column(name = "employer_rate", precision = 9, scale = 6)
  private BigDecimal employerRate;

  @Column(name = "fixed_amount", precision = 19, scale = 2)
  private BigDecimal fixedAmount;

  @Column(precision = 19, scale = 2)
  private BigDecimal floor;

  @Column(precision = 19, scale = 2)
  private BigDecimal cap;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  protected DeductionRule() {}

  public DeductionRule(
      String code,
      DeductionKind kind,
      Computation computation,
      DeductionBase base,
      String agency,
      BigDecimal rate,
      BigDecimal employerRate,
      BigDecimal fixedAmount,
      BigDecimal floor,
      BigDecimal cap,
      LocalDate effectiveDate) {
    this.code = code;
    this.kind = kind;
    this.computation = computation;
    this.base = base;
    this.agency = agency;
    this.rate = rate;
    this.employerRate = employerRate;
    this.fixedAmount = fixedAmount;
    this.floor = floor;
    this.cap = cap;
    this.effectiveDate = effectiveDate;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public DeductionKind getKind() {
    return kind;
  }

  public Computation getComputation() {
    return computation;
  }

  public DeductionBase getBase() {
    return base;
  }

  public String getAgency() {
    return agency;
  }

  public BigDecimal getRate() {
    return rate;
  }

  public BigDecimal getEmployerRate() {
    return employerRate;
  }

  public BigDecimal getFixedAmount() {
    return fixedAmount;
  }

  public BigDecimal getFloor() {
    return floor;
  }

  public BigDecimal getCap() {
    return cap;
  }

  @Override
  public LocalDate effectiveDate() {
    return effectiveDate;
  }
}
