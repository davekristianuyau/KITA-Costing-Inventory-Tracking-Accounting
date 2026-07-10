package com.kita.operations.catalog;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** An inventory item: product, component, raw material, or kit. */
@Entity
@Table(name = "item")
public class Item {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(nullable = false, unique = true)
  private String sku;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ItemType type;

  @ManyToOne(optional = false)
  private UnitOfMeasure baseUom;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ValuationMethod valuationMethod = ValuationMethod.AVCO;

  @Column(nullable = false)
  private boolean perishable = false;

  /** Current unit cost (AVCO running average maintained here); null until first receipt. */
  @Column(precision = 38, scale = 6)
  private BigDecimal standardCost;

  @Column(nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;

  protected Item() {}

  public Item(String sku, String name, ItemType type, UnitOfMeasure baseUom) {
    this.sku = sku;
    this.name = name;
    this.type = type;
    this.baseUom = baseUom;
  }

  public UUID getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public ItemType getType() {
    return type;
  }

  public UnitOfMeasure getBaseUom() {
    return baseUom;
  }

  public ValuationMethod getValuationMethod() {
    return valuationMethod;
  }

  public void setValuationMethod(ValuationMethod valuationMethod) {
    this.valuationMethod = valuationMethod;
  }

  public boolean isPerishable() {
    return perishable;
  }

  public void setPerishable(boolean perishable) {
    this.perishable = perishable;
  }

  public BigDecimal getStandardCost() {
    return standardCost;
  }

  public void setStandardCost(BigDecimal standardCost) {
    this.standardCost = standardCost;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
