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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** An identifiable batch of an item, with optional expiry and its own unit cost (FIFO items). */
@Entity
@Table(
    name = "lot",
    uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "lot_code"}))
public class Lot {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  private Item item;

  @Column(nullable = false)
  private String lotCode;

  private LocalDate expiryDate;

  @Column(precision = 38, scale = 6)
  private BigDecimal unitCost;

  protected Lot() {}

  public Lot(Item item, String lotCode, LocalDate expiryDate, BigDecimal unitCost) {
    this.item = item;
    this.lotCode = lotCode;
    this.expiryDate = expiryDate;
    this.unitCost = unitCost;
  }

  public UUID getId() {
    return id;
  }

  public Item getItem() {
    return item;
  }

  public String getLotCode() {
    return lotCode;
  }

  public LocalDate getExpiryDate() {
    return expiryDate;
  }

  public BigDecimal getUnitCost() {
    return unitCost;
  }
}
