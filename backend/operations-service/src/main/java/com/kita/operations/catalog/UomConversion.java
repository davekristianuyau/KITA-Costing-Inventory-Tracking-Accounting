package com.kita.operations.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Conversion factor within a UoM family: 1 fromUom = factor × toUom. */
@Entity
@Table(name = "uom_conversion")
public class UomConversion {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  private UnitOfMeasure fromUom;

  @ManyToOne(optional = false)
  private UnitOfMeasure toUom;

  @Column(nullable = false, precision = 38, scale = 12)
  private BigDecimal factor;

  protected UomConversion() {}

  public UomConversion(UnitOfMeasure fromUom, UnitOfMeasure toUom, BigDecimal factor) {
    this.fromUom = fromUom;
    this.toUom = toUom;
    this.factor = factor;
  }

  public UUID getId() {
    return id;
  }

  public UnitOfMeasure getFromUom() {
    return fromUom;
  }

  public UnitOfMeasure getToUom() {
    return toUom;
  }

  public BigDecimal getFactor() {
    return factor;
  }
}
