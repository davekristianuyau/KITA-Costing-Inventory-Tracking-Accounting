package com.kita.hr.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** An employee's running balance for one leave type; accrues per policy and draws down on approval. */
@Entity
@Table(name = "leave_balance")
public class LeaveBalance {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Column(name = "leave_type_id", nullable = false)
  private UUID leaveTypeId;

  @Column(nullable = false, precision = 7, scale = 2)
  private BigDecimal balance;

  protected LeaveBalance() {}

  public LeaveBalance(UUID employeeId, UUID leaveTypeId, BigDecimal balance) {
    this.employeeId = employeeId;
    this.leaveTypeId = leaveTypeId;
    this.balance = balance;
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

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal balance) {
    this.balance = balance;
  }
}
