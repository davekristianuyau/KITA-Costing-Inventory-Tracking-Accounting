package com.kita.operations.procurement;

import com.kita.operations.inventory.StockLocation;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "goods_receipt")
public class GoodsReceipt {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(nullable = false)
  private String supplierRef;

  @ManyToOne(optional = false)
  private StockLocation location;

  @Column(nullable = false)
  private Instant receivedAt = Instant.now();

  @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ReceiptLine> lines = new ArrayList<>();

  protected GoodsReceipt() {}

  public GoodsReceipt(String supplierRef, StockLocation location) {
    this.supplierRef = supplierRef;
    this.location = location;
  }

  public void addLine(ReceiptLine line) {
    line.setReceipt(this);
    lines.add(line);
  }

  public UUID getId() {
    return id;
  }

  public String getSupplierRef() {
    return supplierRef;
  }

  public StockLocation getLocation() {
    return location;
  }

  public List<ReceiptLine> getLines() {
    return lines;
  }
}
