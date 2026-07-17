package com.kita.workflow.api.dto;

import com.kita.workflow.ports.ProcurementPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/** Request/response bodies for the purchase-order lifecycle (contracts/workflow-api.md). */
public final class PurchaseOrderDtos {

  private PurchaseOrderDtos() {}

  public record LineRequest(
      @NotBlank String itemId,
      @NotNull @Positive BigDecimal quantity,
      @NotNull @Positive BigDecimal unitCost) {

    public ProcurementPort.PoLine toPoLine() {
      return new ProcurementPort.PoLine(itemId, quantity, unitCost);
    }
  }

  public record RaiseRequest(
      @NotBlank String supplierId, @NotEmpty @Valid List<LineRequest> lines) {

    public List<ProcurementPort.PoLine> toPoLines() {
      return lines.stream().map(LineRequest::toPoLine).toList();
    }
  }

  public record RaiseResponse(String purchaseOrderId, String status, BigDecimal total) {}

  public record StatusResponse(String status) {}
}
