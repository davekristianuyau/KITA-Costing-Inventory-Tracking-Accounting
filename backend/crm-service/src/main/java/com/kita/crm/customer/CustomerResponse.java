package com.kita.crm.customer;

import java.util.UUID;

/** Customer view returned by the API (also the party-validation payload for operations-service). */
public record CustomerResponse(
    UUID id,
    String customerCode,
    CustomerType type,
    String name,
    String email,
    String phone,
    String address,
    CustomerStatus status,
    UUID loyaltyTierId) {

  public static CustomerResponse from(Customer c) {
    return new CustomerResponse(
        c.getId(),
        c.getCustomerCode(),
        c.getType(),
        c.getName(),
        c.getEmail(),
        c.getPhone(),
        c.getAddress(),
        c.getStatus(),
        c.getLoyaltyTierId());
  }
}
