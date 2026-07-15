package com.kita.procurement.supplier;

import java.util.UUID;

/** Supplier view (also the party-validation payload for operations-service). */
public record SupplierResponse(
    UUID id,
    String supplierCode,
    String name,
    String email,
    String phone,
    String address,
    String paymentTerms,
    String deliveryTerms,
    SupplierStatus status) {

  public static SupplierResponse from(Supplier s) {
    return new SupplierResponse(
        s.getId(),
        s.getSupplierCode(),
        s.getName(),
        s.getEmail(),
        s.getPhone(),
        s.getAddress(),
        s.getPaymentTerms(),
        s.getDeliveryTerms(),
        s.getStatus());
  }
}
