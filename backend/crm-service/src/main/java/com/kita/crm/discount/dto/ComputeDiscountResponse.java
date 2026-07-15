package com.kita.crm.discount.dto;

import com.kita.crm.discount.CascadingEngine;
import com.kita.crm.discount.DiscountComputationService;
import com.kita.crm.discount.DiscountOrigin;
import com.kita.crm.discount.StackingMode;
import java.math.BigDecimal;
import java.util.List;

public record ComputeDiscountResponse(
    BigDecimal baseTotal,
    BigDecimal finalPrice,
    StackingMode stackingMode,
    List<BreakdownLineResponse> breakdown,
    List<String> flags) {

  public record BreakdownLineResponse(
      String tierCode, DiscountOrigin origin, BigDecimal baseApplied, BigDecimal amountRemoved) {

    static BreakdownLineResponse from(CascadingEngine.BreakdownLine l) {
      return new BreakdownLineResponse(l.tierCode(), l.origin(), l.baseApplied(), l.amountRemoved());
    }
  }

  public static ComputeDiscountResponse from(DiscountComputationService.Computation c) {
    return new ComputeDiscountResponse(
        c.baseTotal(),
        c.finalPrice(),
        c.stackingMode(),
        c.breakdown().stream().map(BreakdownLineResponse::from).toList(),
        c.flags());
  }
}
