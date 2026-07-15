package com.kita.crm.customer;

/** Partial update of a customer; null fields are left unchanged. */
public record UpdateCustomerRequest(
    String name, String email, String phone, String address, CustomerStatus status) {}
