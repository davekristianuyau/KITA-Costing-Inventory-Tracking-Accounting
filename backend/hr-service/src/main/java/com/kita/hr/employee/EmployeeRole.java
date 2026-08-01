package com.kita.hr.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A back-office role token held by an employee (017 FR-014) — the storage hr-service never had.
 *
 * <p>The token is deliberately an <b>opaque String</b>, not an enum: it spans every service's
 * vocabulary (workflow's {@code SALES}/{@code PROCUREMENT_APPROVER}…, hr's own {@code HR_ADMIN}…,
 * crm's, procurement's, plus {@code OWNER}). Each service recognizes the subset it knows and ignores
 * the rest, so another service can add a role without redeploying hr, and an unrecognized token grants
 * nothing rather than erroring (FR-004).
 */
@Entity
@Table(
    name = "employee_role",
    uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "role"}))
public class EmployeeRole {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Column(name = "role", nullable = false)
  private String role;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt;

  /** The administrator who granted it — a privilege grant is never anonymous (FR-015). */
  @Column(name = "assigned_by", nullable = false)
  private String assignedBy;

  protected EmployeeRole() {}

  public EmployeeRole(UUID employeeId, String role, String assignedBy) {
    this.employeeId = employeeId;
    this.role = role;
    this.assignedBy = assignedBy;
    this.assignedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  public String getRole() {
    return role;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }

  public String getAssignedBy() {
    return assignedBy;
  }
}
