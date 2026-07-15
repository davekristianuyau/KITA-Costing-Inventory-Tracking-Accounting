package com.kita.crm.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** An append-only record of one customer attribute change (FR-003). */
@Entity
@Table(name = "customer_attribute_history")
public class CustomerAttributeHistory {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(nullable = false)
  private String field;

  @Column(name = "old_value")
  private String oldValue;

  @Column(name = "new_value")
  private String newValue;

  @Column private String actor;

  @CreationTimestamp
  @Column(name = "changed_at", nullable = false, updatable = false)
  private Instant changedAt;

  protected CustomerAttributeHistory() {}

  public CustomerAttributeHistory(
      UUID customerId, String field, String oldValue, String newValue, String actor) {
    this.customerId = customerId;
    this.field = field;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.actor = actor;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getField() {
    return field;
  }

  public String getOldValue() {
    return oldValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public String getActor() {
    return actor;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
