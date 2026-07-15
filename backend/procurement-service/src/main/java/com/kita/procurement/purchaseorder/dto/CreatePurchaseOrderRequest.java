package com.kita.procurement.purchaseorder.dto;

import com.kita.procurement.purchaseorder.PurchaseOrderOrigin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Create a purchase order. {@code poNo} is generated when omitted; origin defaults to MANUAL. */
public record CreatePurchaseOrderRequest(
    String poNo,
    @NotNull UUID supplierId,
    PurchaseOrderOrigin origin,
    @NotEmpty @Valid List<LineRequest> lines) {

  /**
   * {@code agreedPrice} is optional: when omitted the supplier's catalog price is used and then
   * frozen onto the line, so a later catalog change never re-prices this order.
   */
  public record LineRequest(
      @NotBlank String itemRef,
      @NotNull @Positive BigDecimal qtyOrdered,
      @PositiveOrZero BigDecimal agreedPrice) {}
}
