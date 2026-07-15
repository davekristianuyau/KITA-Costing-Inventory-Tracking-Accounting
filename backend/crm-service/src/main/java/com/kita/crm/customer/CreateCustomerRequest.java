package com.kita.crm.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request to create a customer. */
public record CreateCustomerRequest(
    @NotBlank String customerCode,
    @NotNull CustomerType type,
    @NotBlank String name,
    String email,
    String phone,
    String address) {}
