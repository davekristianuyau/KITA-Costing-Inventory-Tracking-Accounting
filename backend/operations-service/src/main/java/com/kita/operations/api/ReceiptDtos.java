package com.kita.operations.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReceiptDtos {
  private ReceiptDtos() {}

  public record ReceiptLineRequest(
      @NotNull UUID itemId,
      String lotCode,
      LocalDate expiryDate,
      @NotNull BigDecimal quantity,
      String uom,
      @NotNull BigDecimal unitCost) {}

  public record GoodsReceiptRequest(
      @NotBlank String supplierRef,
      @NotNull UUID locationId,
      @NotNull List<ReceiptLineRequest> lines) {}

  public record GoodsReceiptResponse(UUID id, String supplierRef, UUID locationId) {}
}
