package com.kita.operations.production;

import com.kita.operations.catalog.Item;
import com.kita.operations.inventory.StockLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "build")
public class Build {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  private Item finishedItem;

  @ManyToOne(optional = false)
  private StockLocation location;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BuildStatus status;

  protected Build() {}

  public Build(Item finishedItem, StockLocation location, BigDecimal quantity, BuildStatus status) {
    this.finishedItem = finishedItem;
    this.location = location;
    this.quantity = quantity;
    this.status = status;
  }

  public UUID getId() {
    return id;
  }

  public Item getFinishedItem() {
    return finishedItem;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BuildStatus getStatus() {
    return status;
  }
}
