package com.kita.crm.discount.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** {@code customerId} null/absent = walk-in (no entitlements or loyalty). */
public record ComputeDiscountRequest(
    UUID customerId,
    @NotNull LocalDate saleDate,
    @NotEmpty @Valid List<LineItemRequest> lineItems) {

  public record LineItemRequest(
      String itemRef,
      @NotNull @Positive BigDecimal quantity,
      @NotNull @PositiveOrZero BigDecimal unitPrice) {}
}
