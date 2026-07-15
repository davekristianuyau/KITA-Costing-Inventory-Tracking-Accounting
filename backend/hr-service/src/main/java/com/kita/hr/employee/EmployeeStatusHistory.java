package com.kita.hr.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** An append-only record of one employee status transition, effective-dated (FR-003). */
@Entity
@Table(name = "employee_status_history")
public class EmployeeStatusHistory {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status")
  private EmployeeStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EmployeeStatus status;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Column(name = "changed_by")
  private String changedBy;

  @CreationTimestamp
  @Column(name = "changed_at", nullable = false, updatable = false)
  private Instant changedAt;

  protected EmployeeStatusHistory() {}

  public EmployeeStatusHistory(
      UUID employeeId,
      EmployeeStatus previousStatus,
      EmployeeStatus status,
      LocalDate effectiveDate,
      String changedBy) {
    this.employeeId = employeeId;
    this.previousStatus = previousStatus;
    this.status = status;
    this.effectiveDate = effectiveDate;
    this.changedBy = changedBy;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  public EmployeeStatus getPreviousStatus() {
    return previousStatus;
  }

  public EmployeeStatus getStatus() {
    return status;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public String getChangedBy() {
    return changedBy;
  }
}
