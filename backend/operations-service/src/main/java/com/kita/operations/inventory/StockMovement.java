package com.kita.operations.inventory;

import com.kita.operations.catalog.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Immutable ledger entry recording a stock change (base UoM, signed quantity, cost applied). */
@Entity
@Table(name = "stock_movement")
public class StockMovement {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  private Item item;

  @ManyToOne(optional = false)
  private StockLocation location;

  @ManyToOne private Lot lot;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MovementType type;

  /** Signed quantity in the item's base UoM (positive = increase, negative = decrease). */
  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal quantity;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal unitCost = BigDecimal.ZERO;

  private String reason;

  private String sourceType;

  private String sourceId;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant occurredAt;

  protected StockMovement() {}

  public StockMovement(
      Item item,
      StockLocation location,
      Lot lot,
      MovementType type,
      BigDecimal quantity,
      BigDecimal unitCost,
      String reason,
      String sourceType,
      String sourceId) {
    this.item = item;
    this.location = location;
    this.lot = lot;
    this.type = type;
    this.quantity = quantity;
    this.unitCost = unitCost == null ? BigDecimal.ZERO : unitCost;
    this.reason = reason;
    this.sourceType = sourceType;
    this.sourceId = sourceId;
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

  public MovementType getType() {
    return type;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitCost() {
    return unitCost;
  }

  public String getReason() {
    return reason;
  }

  public String getSourceType() {
    return sourceType;
  }

  public String getSourceId() {
    return sourceId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
