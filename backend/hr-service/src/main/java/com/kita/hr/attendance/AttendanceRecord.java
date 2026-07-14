package com.kita.hr.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A raw daily time record (clock in/out) for an employee — the input to worked-time computation. */
@Entity
@Table(name = "attendance_record")
public class AttendanceRecord {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Column(name = "work_date", nullable = false)
  private LocalDate workDate;

  @Column(name = "time_in", nullable = false)
  private LocalTime timeIn;

  @Column(name = "time_out", nullable = false)
  private LocalTime timeOut;

  @Column private String source;

  protected AttendanceRecord() {}

  public AttendanceRecord(
      UUID employeeId, LocalDate workDate, LocalTime timeIn, LocalTime timeOut, String source) {
    this.employeeId = employeeId;
    this.workDate = workDate;
    this.timeIn = timeIn;
    this.timeOut = timeOut;
    this.source = source;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  public LocalDate getWorkDate() {
    return workDate;
  }

  public LocalTime getTimeIn() {
    return timeIn;
  }

  public LocalTime getTimeOut() {
    return timeOut;
  }
}
