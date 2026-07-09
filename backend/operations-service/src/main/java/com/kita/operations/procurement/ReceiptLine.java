package com.kita.operations.procurement;

import com.kita.operations.catalog.Item;
import com.kita.operations.inventory.Lot;
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
@Table(name = "receipt_line")
public class ReceiptLine {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  private GoodsReceipt receipt;

  @ManyToOne(optional = false)
  private Item item;

  @ManyToOne private Lot lot;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal quantity;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal unitCost;

  protected ReceiptLine() {}

  public ReceiptLine(Item item, Lot lot, BigDecimal quantity, BigDecimal unitCost) {
    this.item = item;
    this.lot = lot;
    this.quantity = quantity;
    this.unitCost = unitCost;
  }

  public void setReceipt(GoodsReceipt receipt) {
    this.receipt = receipt;
  }

  public UUID getId() {
    return id;
  }

  public Item getItem() {
    return item;
  }

  public Lot getLot() {
    return lot;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitCost() {
    return unitCost;
  }
}
