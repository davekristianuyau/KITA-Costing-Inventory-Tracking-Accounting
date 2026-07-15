package com.kita.procurement.receiving;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** One line of a delivery. {@code unitCost} is the PO's agreed price, not the current catalog price. */
@Entity
@Table(name = "goods_receipt_line")
public class GoodsReceiptLine {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "goods_receipt_id", nullable = false)
  private UUID goodsReceiptId;

  @Column(name = "po_line_id", nullable = false)
  private UUID poLineId;

  @Column(name = "item_ref", nullable = false)
  private String itemRef;

  @Column(name = "qty_received", nullable = false, precision = 19, scale = 4)
  private BigDecimal qtyReceived;

  @Column(name = "unit_cost", nullable = false, precision = 19, scale = 2)
  private BigDecimal unitCost;

  protected GoodsReceiptLine() {}

  public GoodsReceiptLine(
      UUID goodsReceiptId, UUID poLineId, String itemRef, BigDecimal qtyReceived, BigDecimal unitCost) {
    this.goodsReceiptId = goodsReceiptId;
    this.poLineId = poLineId;
    this.itemRef = itemRef;
    this.qtyReceived = qtyReceived;
    this.unitCost = unitCost;
  }

  public UUID getId() {
    return id;
  }

  public UUID getGoodsReceiptId() {
    return goodsReceiptId;
  }

  public UUID getPoLineId() {
    return poLineId;
  }

  public String getItemRef() {
    return itemRef;
  }

  public BigDecimal getQtyReceived() {
    return qtyReceived;
  }

  public BigDecimal getUnitCost() {
    return unitCost;
  }
}
