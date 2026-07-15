package com.kita.procurement.receiving.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/** Record a delivery against a sent purchase order. */
public record RecordReceiptRequest(@NotEmpty @Valid List<ReceiptLineRequest> lines) {

  public record ReceiptLineRequest(
      @NotBlank String itemRef, @NotNull @Positive BigDecimal qtyReceived) {}
}
