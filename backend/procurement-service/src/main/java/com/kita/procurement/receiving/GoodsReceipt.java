package com.kita.procurement.receiving;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A delivery recorded against a purchase order. {@code postIdempotencyKey} is derived from the
 * receipt id, so retrying a post that timed out cannot double-count stock (FR-011).
 */
@Entity
@Table(name = "goods_receipt")
public class GoodsReceipt {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "purchase_order_id", nullable = false)
  private UUID purchaseOrderId;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt = Instant.now();

  @Column(name = "received_by")
  private String receivedBy;

  @Column(name = "posted_to_operations", nullable = false)
  private boolean postedToOperations;

  @Column(name = "post_idempotency_key", nullable = false, unique = true)
  private String postIdempotencyKey;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected GoodsReceipt() {}

  public GoodsReceipt(UUID purchaseOrderId, String receivedBy, String postIdempotencyKey) {
    this.purchaseOrderId = purchaseOrderId;
    this.receivedBy = receivedBy;
    this.postIdempotencyKey = postIdempotencyKey;
    this.receivedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getPurchaseOrderId() {
    return purchaseOrderId;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public String getReceivedBy() {
    return receivedBy;
  }

  public boolean isPostedToOperations() {
    return postedToOperations;
  }

  public void markPosted() {
    this.postedToOperations = true;
  }

  public String getPostIdempotencyKey() {
    return postIdempotencyKey;
  }
}
