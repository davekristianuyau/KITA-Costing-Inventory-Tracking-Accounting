package com.kita.hr.attendance;

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

/** A dated holiday with its pay multiplier. */
@Entity
@Table(name = "holiday_calendar")
public class HolidayCalendar {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "holiday_date", nullable = false, unique = true)
  private LocalDate holidayDate;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private HolidayType type;

  @Column(name = "pay_multiplier", nullable = false, precision = 5, scale = 2)
  private BigDecimal payMultiplier;

  protected HolidayCalendar() {}

  public HolidayCalendar(LocalDate holidayDate, String name, HolidayType type, BigDecimal payMultiplier) {
    this.holidayDate = holidayDate;
    this.name = name;
    this.type = type;
    this.payMultiplier = payMultiplier;
  }

  public LocalDate getHolidayDate() {
    return holidayDate;
  }

  public HolidayType getType() {
    return type;
  }

  public BigDecimal getPayMultiplier() {
    return payMultiplier;
  }
}
