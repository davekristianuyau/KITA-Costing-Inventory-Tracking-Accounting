package com.kita.operations.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Request/response DTOs for the sales API. */
public final class SalesDtos {
  private SalesDtos() {}

  public record SalesLineRequest(
      @NotNull UUID itemId, @NotNull BigDecimal quantity, String uom, @NotNull BigDecimal unitPrice) {}

  public record SalesOrderCreateRequest(
      @NotBlank String customerRef, @NotNull List<SalesLineRequest> lines) {}

  public record SalesLineResponse(
      UUID itemId,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal reservedQty,
      BigDecimal fulfilledQty) {}

  public record SalesOrderResponse(
      UUID id, String customerRef, String status, List<SalesLineResponse> lines) {}
}
