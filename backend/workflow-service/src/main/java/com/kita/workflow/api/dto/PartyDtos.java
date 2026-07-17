package com.kita.workflow.api.dto;

import com.kita.workflow.ports.CrmPort;
import com.kita.workflow.ports.ProcurementPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/** Request/response bodies for customer & supplier maintenance (contracts/workflow-api.md). */
public final class PartyDtos {

  private PartyDtos() {}

  public record CustomerRequest(@NotBlank String name, Boolean active) {

    public CrmPort.CustomerInput toInput() {
      return new CrmPort.CustomerInput(name, active == null || active);
    }
  }

  public record SupplierRequest(@NotBlank String name, Boolean active) {

    public ProcurementPort.SupplierInput toInput() {
      return new ProcurementPort.SupplierInput(name, active == null || active);
    }
  }

  public record SuppliedItemRequest(
      @NotBlank String itemId, @NotNull @Positive BigDecimal unitCost) {

    public ProcurementPort.SuppliedItem toSuppliedItem() {
      return new ProcurementPort.SuppliedItem(itemId, unitCost);
    }
  }

  public record SuppliedItemsRequest(@NotEmpty @Valid List<SuppliedItemRequest> items) {

    public List<ProcurementPort.SuppliedItem> toSuppliedItems() {
      return items.stream().map(SuppliedItemRequest::toSuppliedItem).toList();
    }
  }

  public record CustomerResponse(String customerId) {}

  public record SupplierResponse(String supplierId) {}
}
