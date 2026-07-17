package com.kita.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Request/response bodies for production builds (contracts/workflow-api.md). */
public final class BuildDtos {

  private BuildDtos() {}

  public record BuildRequest(@NotBlank String itemId, @NotNull @Positive BigDecimal quantity) {}

  public record BuildResponse(String buildId, BigDecimal produced) {}
}
