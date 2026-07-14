package com.kita.hr.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A leave filing for a date range that a manager approves or rejects. */
@Entity
@Table(name = "leave_request")
public class LeaveRequest {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Column(name = "leave_type_id", nullable = false)
  private UUID leaveTypeId;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal duration;

  @Column private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LeaveStatus status = LeaveStatus.FILED;

  @Column(name = "decided_by")
  private String decidedBy;

  @Column(name = "decided_at")
  private Instant decidedAt;

  protected LeaveRequest() {}

  public LeaveRequest(
      UUID employeeId,
      UUID leaveTypeId,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal duration,
      String reason) {
    this.employeeId = employeeId;
    this.leaveTypeId = leaveTypeId;
    this.startDate = startDate;
    this.endDate = endDate;
    this.duration = duration;
    this.reason = reason;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  public UUID getLeaveTypeId() {
    return leaveTypeId;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public BigDecimal getDuration() {
    return duration;
  }

  public String getReason() {
    return reason;
  }

  public LeaveStatus getStatus() {
    return status;
  }

  /** Record the manager's decision, moving the request out of FILED. */
  public void decide(boolean approved, String decidedBy) {
    this.status = approved ? LeaveStatus.APPROVED : LeaveStatus.REJECTED;
    this.decidedBy = decidedBy;
    this.decidedAt = Instant.now();
  }

  public String getDecidedBy() {
    return decidedBy;
  }
}
