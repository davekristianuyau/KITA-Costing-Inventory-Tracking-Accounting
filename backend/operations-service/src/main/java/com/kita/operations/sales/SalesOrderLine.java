package com.kita.operations.sales;

import com.kita.operations.catalog.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A line on a sales order. Quantities are stored in the item's base unit of measure. */
@Entity
@Table(name = "sales_order_line")
public class SalesOrderLine {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  private SalesOrder order;

  @ManyToOne(optional = false)
  private Item item;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal quantity;

  @Column(nullable = false, precision = 38, scale = 4)
  private BigDecimal unitPrice;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal reservedQty = BigDecimal.ZERO;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal fulfilledQty = BigDecimal.ZERO;

  protected SalesOrderLine() {}

  public SalesOrderLine(Item item, BigDecimal quantity, BigDecimal unitPrice) {
    this.item = item;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
  }

  public UUID getId() {
    return id;
  }

  public SalesOrder getOrder() {
    return order;
  }

  public void setOrder(SalesOrder order) {
    this.order = order;
  }

  public Item getItem() {
    return item;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getReservedQty() {
    return reservedQty;
  }

  public void setReservedQty(BigDecimal reservedQty) {
    this.reservedQty = reservedQty;
  }

  public BigDecimal getFulfilledQty() {
    return fulfilledQty;
  }

  public void setFulfilledQty(BigDecimal fulfilledQty) {
    this.fulfilledQty = fulfilledQty;
  }
}
