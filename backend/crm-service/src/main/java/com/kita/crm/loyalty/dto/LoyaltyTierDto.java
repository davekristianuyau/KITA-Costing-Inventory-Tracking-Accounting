package com.kita.crm.loyalty.dto;

import com.kita.crm.loyalty.LoyaltyTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/** Define a loyalty tier: its qualifying criteria and the discount rule it contributes. */
public record LoyaltyTierDto(
    @NotBlank String code,
    @NotBlank String name,
    @PositiveOrZero Integer minPurchaseCount,
    @PositiveOrZero BigDecimal minPurchaseValue,
    @Positive Integer periodDays,
    @NotNull UUID discountRuleId) {

  public LoyaltyTier toEntity() {
    return new LoyaltyTier(code, name, minPurchaseCount, minPurchaseValue, periodDays, discountRuleId);
  }

  public record Response(
      UUID id,
      String code,
      String name,
      Integer minPurchaseCount,
      BigDecimal minPurchaseValue,
      Integer periodDays,
      UUID discountRuleId) {

    public static Response from(LoyaltyTier t) {
      return new Response(
          t.getId(), t.getCode(), t.getName(), t.getMinPurchaseCount(), t.getMinPurchaseValue(),
          t.getPeriodDays(), t.getDiscountRuleId());
    }
  }
}
