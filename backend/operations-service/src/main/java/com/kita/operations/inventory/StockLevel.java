package com.kita.operations.inventory;

import com.kita.operations.catalog.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Cached on-hand/reserved for an item at a location (and lot). Reconciles to the movement ledger. */
@Entity
@Table(
    name = "stock_level",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"item_id", "location_id", "lot_id"}))
public class StockLevel {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  private Item item;

  @ManyToOne(optional = false)
  private StockLocation location;

  @ManyToOne private Lot lot;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal onHand = BigDecimal.ZERO;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal reserved = BigDecimal.ZERO;

  protected StockLevel() {}

  public StockLevel(Item item, StockLocation location, Lot lot) {
    this.item = item;
    this.location = location;
    this.lot = lot;
  }

  public UUID getId() {
    return id;
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

  public BigDecimal getOnHand() {
    return onHand;
  }

  public void setOnHand(BigDecimal onHand) {
    this.onHand = onHand;
  }

  public BigDecimal getReserved() {
    return reserved;
  }

  public void setReserved(BigDecimal reserved) {
    this.reserved = reserved;
  }

  public BigDecimal getAvailable() {
    return onHand.subtract(reserved);
  }
}
