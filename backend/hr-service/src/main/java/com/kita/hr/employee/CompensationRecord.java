package com.kita.hr.employee;

import com.kita.hr.common.EffectiveDated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** An effective-dated compensation entry for an employee. Prior entries are retained as history. */
@Entity
@Table(name = "compensation_record")
public class CompensationRecord implements EffectiveDated {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Column(name = "basic_pay", nullable = false, precision = 19, scale = 4)
  private BigDecimal basicPay;

  @Enumerated(EnumType.STRING)
  @Column(name = "pay_frequency", nullable = false)
  private PayFrequency payFrequency;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CompensationRecord() {}

  public CompensationRecord(
      Employee employee, LocalDate effectiveDate, BigDecimal basicPay, PayFrequency payFrequency) {
    this.employee = employee;
    this.effectiveDate = effectiveDate;
    this.basicPay = basicPay;
    this.payFrequency = payFrequency;
  }

  public UUID getId() {
    return id;
  }

  public Employee getEmployee() {
    return employee;
  }

  @Override
  public LocalDate effectiveDate() {
    return effectiveDate;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public BigDecimal getBasicPay() {
    return basicPay;
  }

  public PayFrequency getPayFrequency() {
    return payFrequency;
  }
}
