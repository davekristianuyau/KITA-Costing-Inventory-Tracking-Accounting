package com.kita.operations.api;

import com.kita.operations.bom.BomType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class BomDtos {
  private BomDtos() {}

  public record BomComponentRequest(
      @NotNull UUID componentItemId, @NotNull BigDecimal quantity, String uom) {}

  public record BomCreateRequest(
      @NotNull UUID parentItemId,
      @NotNull BomType type,
      BigDecimal outputQuantity,
      @NotNull List<BomComponentRequest> components) {}

  public record BomResponse(UUID id, UUID parentItemId, BomType type, BigDecimal outputQuantity) {}

  public record ComponentRequirementResponse(
      UUID componentItemId, BigDecimal requiredQuantity, String uom) {}
}
