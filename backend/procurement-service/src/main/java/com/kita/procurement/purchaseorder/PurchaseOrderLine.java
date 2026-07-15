package com.kita.procurement.purchaseorder;

import com.kita.procurement.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One ordered item. {@code agreedPrice} is what was agreed when the order was raised and is fixed
 * once the order is SENT, even if the supplier catalog later changes.
 */
@Entity
@Table(name = "purchase_order_line")
public class PurchaseOrderLine {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "purchase_order_id", nullable = false)
  private UUID purchaseOrderId;

  @Column(name = "item_ref", nullable = false)
  private String itemRef;

  @Column(name = "qty_ordered", nullable = false, precision = 19, scale = 4)
  private BigDecimal qtyOrdered;

  @Column(name = "agreed_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal agreedPrice;

  @Column(name = "qty_received", nullable = false, precision = 19, scale = 4)
  private BigDecimal qtyReceived = BigDecimal.ZERO;

  @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
  private BigDecimal lineTotal;

  protected PurchaseOrderLine() {}

  public PurchaseOrderLine(
      UUID purchaseOrderId, String itemRef, BigDecimal qtyOrdered, BigDecimal agreedPrice) {
    this.purchaseOrderId = purchaseOrderId;
    this.itemRef = itemRef;
    this.qtyOrdered = qtyOrdered;
    this.agreedPrice = agreedPrice;
    this.qtyReceived = BigDecimal.ZERO;
    this.lineTotal = Money.round(qtyOrdered.multiply(agreedPrice));
  }

  /** What is still owed on this line. */
  public BigDecimal qtyOutstanding() {
    return qtyOrdered.subtract(qtyReceived);
  }

  public boolean isFullyReceived() {
    return qtyReceived.compareTo(qtyOrdered) >= 0;
  }

  /** Record stock arriving against this line. The caller enforces the over-receipt policy. */
  public void receive(BigDecimal quantity) {
    this.qtyReceived = this.qtyReceived.add(quantity);
  }

  public UUID getId() {
    return id;
  }

  public UUID getPurchaseOrderId() {
    return purchaseOrderId;
  }

  public String getItemRef() {
    return itemRef;
  }

  public BigDecimal getQtyOrdered() {
    return qtyOrdered;
  }

  public BigDecimal getAgreedPrice() {
    return agreedPrice;
  }

  public BigDecimal getQtyReceived() {
    return qtyReceived;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }
}
