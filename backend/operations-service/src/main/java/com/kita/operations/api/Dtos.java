package com.kita.operations.api;

import com.kita.operations.catalog.ItemType;
import com.kita.operations.catalog.UomFamily;
import com.kita.operations.catalog.ValuationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request/response DTOs for the operations API. Decimal values are JSON numbers (BigDecimal). */
public final class Dtos {
  private Dtos() {}

  public record ItemCreateRequest(
      @NotBlank String sku,
      @NotBlank String name,
      @NotNull ItemType type,
      @NotBlank String baseUom,
      ValuationMethod valuationMethod,
      Boolean perishable) {}

  public record ItemResponse(
      UUID id,
      String sku,
      String name,
      ItemType type,
      String baseUom,
      ValuationMethod valuationMethod,
      boolean perishable,
      BigDecimal standardCost) {}

  public record UomCreateRequest(@NotBlank String code, @NotNull UomFamily family) {}

  public record UomResponse(UUID id, String code, UomFamily family) {}

  public record ConversionCreateRequest(
      @NotBlank String fromUom, @NotBlank String toUom, @NotNull BigDecimal factor) {}

  public record LocationCreateRequest(@NotBlank String code, @NotBlank String name) {}

  public record LocationResponse(UUID id, String code, String name) {}

  public record AdjustmentRequest(
      @NotNull UUID itemId,
      @NotNull UUID locationId,
      UUID lotId,
      @NotNull BigDecimal quantity,
      String uom,
      @NotBlank String reason) {}

  public record AvailabilityResponse(
      UUID itemId,
      UUID locationId,
      UUID lotId,
      BigDecimal onHand,
      BigDecimal reserved,
      BigDecimal available) {}

  public record MovementResponse(
      UUID id,
      UUID itemId,
      UUID locationId,
      UUID lotId,
      String type,
      BigDecimal quantity,
      BigDecimal unitCost,
      String reason,
      String sourceType,
      String sourceId,
      Instant occurredAt) {}
}
