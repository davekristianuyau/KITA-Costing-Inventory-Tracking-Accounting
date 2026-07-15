package com.kita.procurement.purchaseorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** A purchase order. Transitions are guarded by {@link PurchaseOrderStateMachine}. */
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "po_no", nullable = false, unique = true)
  private String poNo;

  @Column(name = "supplier_id", nullable = false)
  private UUID supplierId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PurchaseOrderOrigin origin = PurchaseOrderOrigin.MANUAL;

  @Column(name = "order_total", nullable = false, precision = 19, scale = 2)
  private BigDecimal orderTotal = BigDecimal.ZERO;

  @Column(name = "created_by")
  private String createdBy;

  @Column(name = "approved_by")
  private String approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;

  protected PurchaseOrder() {}

  public PurchaseOrder(String poNo, UUID supplierId, PurchaseOrderOrigin origin, String createdBy) {
    this.poNo = poNo;
    this.supplierId = supplierId;
    this.origin = origin;
    this.createdBy = createdBy;
  }

  public void markApproved(String by) {
    this.approvedBy = by;
    this.approvedAt = Instant.now();
    this.status = PurchaseOrderStatus.APPROVED;
  }

  public void markSent() {
    this.sentAt = Instant.now();
    this.status = PurchaseOrderStatus.SENT;
  }

  public UUID getId() {
    return id;
  }

  public String getPoNo() {
    return poNo;
  }

  public UUID getSupplierId() {
    return supplierId;
  }

  public PurchaseOrderStatus getStatus() {
    return status;
  }

  public void setStatus(PurchaseOrderStatus status) {
    this.status = status;
  }

  public PurchaseOrderOrigin getOrigin() {
    return origin;
  }

  public BigDecimal getOrderTotal() {
    return orderTotal;
  }

  public void setOrderTotal(BigDecimal orderTotal) {
    this.orderTotal = orderTotal;
  }

  public String getApprovedBy() {
    return approvedBy;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }
}
