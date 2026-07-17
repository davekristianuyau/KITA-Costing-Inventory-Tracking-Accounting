package com.kita.workflow.api.dto;

import com.kita.workflow.ports.OperationsPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/** Request/response bodies for the sales-order lifecycle (contracts/workflow-api.md). */
public final class SalesOrderDtos {

  private SalesOrderDtos() {}

  public record LineRequest(
      @NotBlank String itemId,
      @NotNull @Positive BigDecimal quantity,
      @NotNull @Positive BigDecimal unitPrice) {

    public OperationsPort.SalesLine toSalesLine() {
      return new OperationsPort.SalesLine(itemId, quantity, unitPrice);
    }
  }

  public record DraftRequest(
      @NotBlank String customerId, @NotEmpty @Valid List<LineRequest> lines) {

    public List<OperationsPort.SalesLine> toSalesLines() {
      return lines.stream().map(LineRequest::toSalesLine).toList();
    }
  }

  public record DraftResponse(String salesOrderId, String state, boolean reservedAll) {}

  public record StateResponse(String state) {}
}
