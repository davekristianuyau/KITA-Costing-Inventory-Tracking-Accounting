package com.kita.workflow.api.dto;

import com.kita.workflow.ports.ProcurementPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/** Request/response bodies for goods receiving (contracts/workflow-api.md). */
public final class ReceivingDtos {

  private ReceivingDtos() {}

  public record LineRequest(
      @NotBlank String itemId, @NotNull @Positive BigDecimal quantityReceived) {

    public ProcurementPort.ReceiptLine toReceiptLine() {
      return new ProcurementPort.ReceiptLine(itemId, quantityReceived);
    }
  }

  public record RecordRequest(@NotEmpty @Valid List<LineRequest> lines) {

    public List<ProcurementPort.ReceiptLine> toReceiptLines() {
      return lines.stream().map(LineRequest::toReceiptLine).toList();
    }
  }

  public record RecordResponse(String pendingReceiptId, String state) {}

  public record ConfirmResponse(String receiptId, String poStatus) {}
}
