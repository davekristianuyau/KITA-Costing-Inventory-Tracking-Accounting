package com.kita.hr.attendance;

import com.kita.hr.common.EffectiveDated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** An employee's expected working shift and night window, used to judge attendance. */
@Entity
@Table(name = "work_schedule")
public class WorkSchedule implements EffectiveDated {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Column(name = "shift_start", nullable = false)
  private LocalTime shiftStart;

  @Column(name = "shift_end", nullable = false)
  private LocalTime shiftEnd;

  @Column(name = "break_minutes", nullable = false)
  private int breakMinutes;

  @Column(name = "standard_daily_hours", nullable = false, precision = 5, scale = 2)
  private BigDecimal standardDailyHours;

  @Column(name = "night_start")
  private LocalTime nightStart;

  @Column(name = "night_end")
  private LocalTime nightEnd;

  protected WorkSchedule() {}

  public WorkSchedule(
      UUID employeeId,
      LocalDate effectiveDate,
      LocalTime shiftStart,
      LocalTime shiftEnd,
      int breakMinutes,
      BigDecimal standardDailyHours,
      LocalTime nightStart,
      LocalTime nightEnd) {
    this.employeeId = employeeId;
    this.effectiveDate = effectiveDate;
    this.shiftStart = shiftStart;
    this.shiftEnd = shiftEnd;
    this.breakMinutes = breakMinutes;
    this.standardDailyHours = standardDailyHours;
    this.nightStart = nightStart;
    this.nightEnd = nightEnd;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  @Override
  public LocalDate effectiveDate() {
    return effectiveDate;
  }

  public LocalTime getShiftStart() {
    return shiftStart;
  }

  public LocalTime getShiftEnd() {
    return shiftEnd;
  }

  public int getBreakMinutes() {
    return breakMinutes;
  }

  public BigDecimal getStandardDailyHours() {
    return standardDailyHours;
  }

  public LocalTime getNightStart() {
    return nightStart;
  }

  public LocalTime getNightEnd() {
    return nightEnd;
  }
}
