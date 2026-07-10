package com.kita.operations.api;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public final class BuildDtos {
  private BuildDtos() {}

  public record BuildRequest(
      @NotNull UUID finishedItemId, @NotNull UUID locationId, @NotNull BigDecimal quantity) {}

  public record BuildResponse(UUID id, UUID finishedItemId, BigDecimal quantity, String status) {}
}
