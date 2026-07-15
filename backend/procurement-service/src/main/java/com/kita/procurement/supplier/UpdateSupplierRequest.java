package com.kita.procurement.supplier;

/** Partial update of a supplier; null fields are left unchanged. */
public record UpdateSupplierRequest(
    String name,
    String email,
    String phone,
    String address,
    String paymentTerms,
    String deliveryTerms,
    SupplierStatus status) {}
