package com.kita.crm.customer;

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

/** A customer of the client. Also the Party master that operations-service validates against. */
@Entity
@Table(name = "customer")
public class Customer {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "customer_code", nullable = false, unique = true)
  private String customerCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CustomerType type;

  @Column(nullable = false)
  private String name;

  @Column private String email;

  @Column private String phone;

  @Column private String address;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CustomerStatus status = CustomerStatus.ACTIVE;

  /** The tier last evaluated for this customer (US3); null until evaluated or if not qualifying. */
  @Column(name = "loyalty_tier_id")
  private UUID loyaltyTierId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;

  protected Customer() {}

  public Customer(String customerCode, CustomerType type, String name) {
    this.customerCode = customerCode;
    this.type = type;
    this.name = name;
  }

  public UUID getId() {
    return id;
  }

  public String getCustomerCode() {
    return customerCode;
  }

  public CustomerType getType() {
    return type;
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

  public CustomerStatus getStatus() {
    return status;
  }

  public void setStatus(CustomerStatus status) {
    this.status = status;
  }

  public UUID getLoyaltyTierId() {
    return loyaltyTierId;
  }

  public void setLoyaltyTierId(UUID loyaltyTierId) {
    this.loyaltyTierId = loyaltyTierId;
  }
}
