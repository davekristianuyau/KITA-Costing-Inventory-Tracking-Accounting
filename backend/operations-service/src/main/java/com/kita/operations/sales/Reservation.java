package com.kita.operations.sales;

import com.kita.operations.catalog.Item;
import com.kita.operations.inventory.Lot;
import com.kita.operations.inventory.StockLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A hold on specific stock (item/location/lot) for a confirmed order line. */
@Entity
@Table(name = "reservation")
public class Reservation {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  private SalesOrderLine orderLine;

  @ManyToOne(optional = false)
  private Item item;

  @ManyToOne(optional = false)
  private StockLocation location;

  @ManyToOne private Lot lot;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal quantity;

  protected Reservation() {}

  public Reservation(
      SalesOrderLine orderLine, Item item, StockLocation location, Lot lot, BigDecimal quantity) {
    this.orderLine = orderLine;
    this.item = item;
    this.location = location;
    this.lot = lot;
    this.quantity = quantity;
  }

  public UUID getId() {
    return id;
  }

  public SalesOrderLine getOrderLine() {
    return orderLine;
  }

  public Item getItem() {
    return item;
  }

  public StockLocation getLocation() {
    return location;
  }

  public Lot getLot() {
    return lot;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }
}
