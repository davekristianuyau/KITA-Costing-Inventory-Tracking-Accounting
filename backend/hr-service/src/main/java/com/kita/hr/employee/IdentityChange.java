package com.kita.hr.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Append-only audit of who may act as whom, and with what privileges (017 FR-009, FR-015).
 *
 * <p>Link changes and role changes share one table on purpose: "who can do what, and who decided that"
 * is a single question, and a reviewer should not have to join two logs to answer it. An earlier draft
 * recorded only link changes, which left role grants — the more sensitive half — with nowhere to write.
 */
@Entity
@Table(name = "identity_change")
public class IdentityChange {

  /** What happened. Link actions carry {@code accountUsername}; role actions carry {@code role}. */
  public enum Action {
    LINKED,
    UNLINKED,
    ROLE_GRANTED,
    ROLE_REVOKED
  }

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Column(name = "action", nullable = false)
  private String action;

  @Column(name = "account_username")
  private String accountUsername;

  @Column(name = "role")
  private String role;

  /** The acting administrator — never null, so no privilege change is ever anonymous. */
  @Column(name = "changed_by", nullable = false)
  private String changedBy;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  protected IdentityChange() {}

  private IdentityChange(
      UUID employeeId, Action action, String accountUsername, String role, String changedBy) {
    this.employeeId = employeeId;
    this.action = action.name();
    this.accountUsername = accountUsername;
    this.role = role;
    this.changedBy = changedBy;
    this.changedAt = Instant.now();
  }

  public static IdentityChange linked(UUID employeeId, String accountUsername, String changedBy) {
    return new IdentityChange(employeeId, Action.LINKED, accountUsername, null, changedBy);
  }

  public static IdentityChange unlinked(UUID employeeId, String accountUsername, String changedBy) {
    return new IdentityChange(employeeId, Action.UNLINKED, accountUsername, null, changedBy);
  }

  public static IdentityChange roleGranted(UUID employeeId, String role, String changedBy) {
    return new IdentityChange(employeeId, Action.ROLE_GRANTED, null, role, changedBy);
  }

  public static IdentityChange roleRevoked(UUID employeeId, String role, String changedBy) {
    return new IdentityChange(employeeId, Action.ROLE_REVOKED, null, role, changedBy);
  }

  public UUID getId() {
    return id;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  public String getAction() {
    return action;
  }

  public String getAccountUsername() {
    return accountUsername;
  }

  public String getRole() {
    return role;
  }

  public String getChangedBy() {
    return changedBy;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
