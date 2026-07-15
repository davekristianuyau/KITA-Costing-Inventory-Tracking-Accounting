package com.kita.procurement.supplier;

import jakarta.validation.constraints.NotBlank;

/** Request to create a supplier. */
public record CreateSupplierRequest(
    @NotBlank String supplierCode,
    @NotBlank String name,
    String email,
    String phone,
    String address,
    String paymentTerms,
    String deliveryTerms) {}
