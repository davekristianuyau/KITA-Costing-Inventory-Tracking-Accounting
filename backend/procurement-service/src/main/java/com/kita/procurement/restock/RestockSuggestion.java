package com.kita.procurement.restock;

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

/** A proposed replenishment order for one supplier, consolidating every item they are preferred for. */
@Entity
@Table(name = "restock_suggestion")
public class RestockSuggestion {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "supplier_id", nullable = false)
  private UUID supplierId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RestockStatus status = RestockStatus.OPEN;

  @Column(name = "generated_at", nullable = false)
  private Instant generatedAt = Instant.now();

  @Column(name = "converted_po_id")
  private UUID convertedPoId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected RestockSuggestion() {}

  public RestockSuggestion(UUID supplierId) {
    this.supplierId = supplierId;
    this.generatedAt = Instant.now();
  }

  public void markConverted(UUID poId) {
    this.status = RestockStatus.CONVERTED;
    this.convertedPoId = poId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSupplierId() {
    return supplierId;
  }

  public RestockStatus getStatus() {
    return status;
  }

  public void setStatus(RestockStatus status) {
    this.status = status;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public UUID getConvertedPoId() {
    return convertedPoId;
  }
}
