package com.kita.crm.discount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** How statutory and promotional/loyalty tiers combine. The newest row is authoritative (FR-013). */
@Entity
@Table(name = "stacking_policy")
public class StackingPolicy {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private StackingMode mode = StackingMode.MOST_FAVORABLE;

  @Column(name = "updated_by")
  private String updatedBy;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected StackingPolicy() {}

  public StackingPolicy(StackingMode mode, String updatedBy) {
    this.mode = mode;
    this.updatedBy = updatedBy;
  }

  public UUID getId() {
    return id;
  }

  public StackingMode getMode() {
    return mode;
  }

  public void setMode(StackingMode mode) {
    this.mode = mode;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
