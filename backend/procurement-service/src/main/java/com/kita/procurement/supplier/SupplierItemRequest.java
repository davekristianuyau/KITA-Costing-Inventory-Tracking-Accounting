package com.kita.procurement.supplier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Add or update an item a supplier supplies. */
public record SupplierItemRequest(
    @NotBlank String itemRef,
    @NotNull @PositiveOrZero BigDecimal supplierPrice,
    @PositiveOrZero Integer leadTimeDays,
    @Positive BigDecimal minOrderQty,
    boolean preferred) {}
