package com.kita.procurement.supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** A supplier. Also the Party master that operations-service validates purchases against. */
@Entity
@Table(name = "supplier")
public class Supplier {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "supplier_code", nullable = false, unique = true)
  private String supplierCode;

  @Column(nullable = false)
  private String name;

  @Column private String email;

  @Column private String phone;

  @Column private String address;

  @Column(name = "payment_terms")
  private String paymentTerms;

  @Column(name = "delivery_terms")
  private String deliveryTerms;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SupplierStatus status = SupplierStatus.ACTIVE;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;

  protected Supplier() {}

  public Supplier(String supplierCode, String name) {
    this.supplierCode = supplierCode;
    this.name = name;
  }

  public UUID getId() {
    return id;
  }

  public String getSupplierCode() {
    return supplierCode;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getPaymentTerms() {
    return paymentTerms;
  }

  public void setPaymentTerms(String paymentTerms) {
    this.paymentTerms = paymentTerms;
  }

  public String getDeliveryTerms() {
    return deliveryTerms;
  }

  public void setDeliveryTerms(String deliveryTerms) {
    this.deliveryTerms = deliveryTerms;
  }

  public SupplierStatus getStatus() {
    return status;
  }

  public void setStatus(SupplierStatus status) {
    this.status = status;
  }
}
