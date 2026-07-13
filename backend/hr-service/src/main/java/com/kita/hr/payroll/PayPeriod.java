package com.kita.hr.payroll;

import com.kita.hr.employee.PayFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A bounded date range payroll is run for. */
@Entity
@Table(name = "pay_period")
public class PayPeriod {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PayFrequency frequency;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(name = "pay_date", nullable = false)
  private LocalDate payDate;

  protected PayPeriod() {}

  public PayPeriod(PayFrequency frequency, LocalDate startDate, LocalDate endDate, LocalDate payDate) {
    this.frequency = frequency;
    this.startDate = startDate;
    this.endDate = endDate;
    this.payDate = payDate;
  }

  public UUID getId() {
    return id;
  }

  public PayFrequency getFrequency() {
    return frequency;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public LocalDate getPayDate() {
    return payDate;
  }
}
