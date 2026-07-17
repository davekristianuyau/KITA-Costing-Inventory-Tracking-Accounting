package com.kita.workflow.activity;

import com.kita.workflow.authorization.BackOfficeAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * One append-only row per back-office transition (data-model.md). Written for every terminal outcome —
 * success and both rejection kinds and retry exhaustion — so 100% of attempts are attributable. No
 * {@code UPDATE}/{@code DELETE}.
 */
@Entity
@Table(name = "back_office_activity")
public class ActivityRecord {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "actor_employee_id", nullable = false)
  private String actorEmployeeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BackOfficeAction action;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ActivityOutcome outcome;

  @Column private String reason;

  @Column(name = "target_ref")
  private String targetRef;

  @Column(name = "maker_employee_id")
  private String makerEmployeeId;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @CreationTimestamp
  @Column(name = "at", nullable = false, updatable = false)
  private Instant at;

  protected ActivityRecord() {}

  public ActivityRecord(
      String actorEmployeeId,
      BackOfficeAction action,
      ActivityOutcome outcome,
      String reason,
      String targetRef,
      String makerEmployeeId,
      String idempotencyKey,
      int retryCount) {
    this.actorEmployeeId = actorEmployeeId;
    this.action = action;
    this.outcome = outcome;
    this.reason = reason;
    this.targetRef = targetRef;
    this.makerEmployeeId = makerEmployeeId;
    this.idempotencyKey = idempotencyKey;
    this.retryCount = retryCount;
  }

  public UUID getId() {
    return id;
  }

  public String getActorEmployeeId() {
    return actorEmployeeId;
  }

  public BackOfficeAction getAction() {
    return action;
  }

  public ActivityOutcome getOutcome() {
    return outcome;
  }

  public String getReason() {
    return reason;
  }

  public String getTargetRef() {
    return targetRef;
  }

  public String getMakerEmployeeId() {
    return makerEmployeeId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public Instant getAt() {
    return at;
  }
}
