package com.kita.hr.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** A payroll computation for a pay period. State: DRAFT → COMPUTED → FINALIZED (or CANCELLED). */
@Entity
@Table(name = "payroll_run")
public class PayrollRun {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "pay_period_id", nullable = false)
  private PayPeriod payPeriod;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RunType type = RunType.REGULAR;

  @Column(name = "adjusts_run_id")
  private UUID adjustsRunId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RunStatus status = RunStatus.DRAFT;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "created_by")
  private String createdBy;

  @Column(name = "finalized_by")
  private String finalizedBy;

  @Column(name = "finalized_at")
  private Instant finalizedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PayrollRun() {}

  public PayrollRun(PayPeriod payPeriod, RunType type, UUID adjustsRunId, String idempotencyKey, String createdBy) {
    this.payPeriod = payPeriod;
    this.type = type;
    this.adjustsRunId = adjustsRunId;
    this.idempotencyKey = idempotencyKey;
    this.createdBy = createdBy;
  }

  public UUID getId() {
    return id;
  }

  public PayPeriod getPayPeriod() {
    return payPeriod;
  }

  public RunType getType() {
    return type;
  }

  public UUID getAdjustsRunId() {
    return adjustsRunId;
  }

  public RunStatus getStatus() {
    return status;
  }

  public void setStatus(RunStatus status) {
    this.status = status;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public Instant getFinalizedAt() {
    return finalizedAt;
  }

  public void markFinalized(String by) {
    this.finalizedBy = by;
    this.finalizedAt = Instant.now();
    this.status = RunStatus.FINALIZED;
  }
}
