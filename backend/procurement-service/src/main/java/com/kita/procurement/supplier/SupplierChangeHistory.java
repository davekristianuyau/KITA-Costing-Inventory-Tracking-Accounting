package com.kita.procurement.supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** An append-only record of one supplier or supplied-item change (FR-003). */
@Entity
@Table(name = "supplier_change_history")
public class SupplierChangeHistory {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "supplier_id", nullable = false)
  private UUID supplierId;

  /** Set when the change was to a supplied item rather than the supplier itself. */
  @Column(name = "item_ref")
  private String itemRef;

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

  protected SupplierChangeHistory() {}

  public SupplierChangeHistory(
      UUID supplierId, String itemRef, String field, String oldValue, String newValue, String actor) {
    this.supplierId = supplierId;
    this.itemRef = itemRef;
    this.field = field;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.actor = actor;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSupplierId() {
    return supplierId;
  }

  public String getItemRef() {
    return itemRef;
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
