package com.kita.operations.sales;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A customer's sales order and its lines; drives reservations and fulfillment. */
@Entity
@Table(name = "sales_order")
public class SalesOrder {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(nullable = false)
  private String customerRef;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status = OrderStatus.DRAFT;

  @Column(nullable = false)
  private Instant orderedAt = Instant.now();

  private Instant confirmedAt;

  private Instant fulfilledAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<SalesOrderLine> lines = new ArrayList<>();

  protected SalesOrder() {}

  public SalesOrder(String customerRef) {
    this.customerRef = customerRef;
  }

  public void addLine(SalesOrderLine line) {
    line.setOrder(this);
    this.lines.add(line);
  }

  public UUID getId() {
    return id;
  }

  public String getCustomerRef() {
    return customerRef;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public void setStatus(OrderStatus status) {
    this.status = status;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public void setConfirmedAt(Instant confirmedAt) {
    this.confirmedAt = confirmedAt;
  }

  public Instant getFulfilledAt() {
    return fulfilledAt;
  }

  public void setFulfilledAt(Instant fulfilledAt) {
    this.fulfilledAt = fulfilledAt;
  }

  public List<SalesOrderLine> getLines() {
    return lines;
  }
}
