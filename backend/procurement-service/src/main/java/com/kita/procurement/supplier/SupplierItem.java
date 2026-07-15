package com.kita.procurement.supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * An item a supplier can supply, at their price. {@code preferred} marks the default source used by
 * restock; at most one supplier may be preferred per item.
 */
@Entity
@Table(name = "supplier_item")
public class SupplierItem {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "supplier_id", nullable = false)
  private UUID supplierId;

  /** operations-service item id; this service never stores stock for it. */
  @Column(name = "item_ref", nullable = false)
  private String itemRef;

  @Column(name = "supplier_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal supplierPrice;

  @Column(name = "lead_time_days")
  private Integer leadTimeDays;

  @Column(name = "min_order_qty", precision = 19, scale = 4)
  private BigDecimal minOrderQty;

  @Column(nullable = false)
  private boolean preferred;

  protected SupplierItem() {}

  public SupplierItem(
      UUID supplierId,
      String itemRef,
      BigDecimal supplierPrice,
      Integer leadTimeDays,
      BigDecimal minOrderQty,
      boolean preferred) {
    this.supplierId = supplierId;
    this.itemRef = itemRef;
    this.supplierPrice = supplierPrice;
    this.leadTimeDays = leadTimeDays;
    this.minOrderQty = minOrderQty;
    this.preferred = preferred;
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

  public BigDecimal getSupplierPrice() {
    return supplierPrice;
  }

  public void setSupplierPrice(BigDecimal supplierPrice) {
    this.supplierPrice = supplierPrice;
  }

  public Integer getLeadTimeDays() {
    return leadTimeDays;
  }

  public void setLeadTimeDays(Integer leadTimeDays) {
    this.leadTimeDays = leadTimeDays;
  }

  public BigDecimal getMinOrderQty() {
    return minOrderQty;
  }

  public void setMinOrderQty(BigDecimal minOrderQty) {
    this.minOrderQty = minOrderQty;
  }

  public boolean isPreferred() {
    return preferred;
  }

  public void setPreferred(boolean preferred) {
    this.preferred = preferred;
  }
}
