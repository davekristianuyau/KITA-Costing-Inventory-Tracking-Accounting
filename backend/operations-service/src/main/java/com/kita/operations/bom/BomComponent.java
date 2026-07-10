package com.kita.operations.bom;

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

@Entity
@Table(name = "bom_component")
public class BomComponent {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  private BillOfMaterials bom;

  @ManyToOne(optional = false)
  private Item componentItem;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal quantity;

  @Column(nullable = false)
  private String uom;

  protected BomComponent() {}

  public BomComponent(Item componentItem, BigDecimal quantity, String uom) {
    this.componentItem = componentItem;
    this.quantity = quantity;
    this.uom = uom;
  }

  public UUID getId() {
    return id;
  }

  public BillOfMaterials getBom() {
    return bom;
  }

  public void setBom(BillOfMaterials bom) {
    this.bom = bom;
  }

  public Item getComponentItem() {
    return componentItem;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public String getUom() {
    return uom;
  }
}
