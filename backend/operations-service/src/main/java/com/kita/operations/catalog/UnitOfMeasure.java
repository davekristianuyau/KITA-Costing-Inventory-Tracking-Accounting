package com.kita.operations.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A unit of measure (e.g., kg, g, pcs, tray, m). */
@Entity
@Table(name = "unit_of_measure")
public class UnitOfMeasure {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(nullable = false, unique = true)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UomFamily family;

  protected UnitOfMeasure() {}

  public UnitOfMeasure(String code, UomFamily family) {
    this.code = code;
    this.family = family;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public UomFamily getFamily() {
    return family;
  }
}
