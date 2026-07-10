package com.kita.operations.bom;

import com.kita.operations.catalog.Item;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "bill_of_materials")
public class BillOfMaterials {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  private Item parentItem;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BomType type;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal outputQuantity = BigDecimal.ONE;

  @Column(nullable = false)
  private boolean active = true;

  @OneToMany(mappedBy = "bom", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<BomComponent> components = new ArrayList<>();

  protected BillOfMaterials() {}

  public BillOfMaterials(Item parentItem, BomType type, BigDecimal outputQuantity) {
    this.parentItem = parentItem;
    this.type = type;
    if (outputQuantity != null) {
      this.outputQuantity = outputQuantity;
    }
  }

  public void addComponent(BomComponent c) {
    c.setBom(this);
    components.add(c);
  }

  public UUID getId() {
    return id;
  }

  public Item getParentItem() {
    return parentItem;
  }

  public BomType getType() {
    return type;
  }

  public BigDecimal getOutputQuantity() {
    return outputQuantity;
  }

  public boolean isActive() {
    return active;
  }

  public List<BomComponent> getComponents() {
    return components;
  }
}
