package com.kita.crm.discount.dto;

import com.kita.crm.discount.DiscountComputationKind;
import com.kita.crm.discount.DiscountOrigin;
import com.kita.crm.discount.DiscountRule;
import com.kita.crm.discount.VatTreatment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Create/version an effective-dated discount rule. */
public record DiscountRuleDto(
    @NotBlank String code,
    @NotNull DiscountOrigin origin,
    @NotNull DiscountComputationKind computation,
    @NotNull @PositiveOrZero BigDecimal value,
    VatTreatment vatTreatment,
    Integer priority,
    @NotNull LocalDate effectiveDate) {

  public DiscountRule toEntity() {
    return new DiscountRule(
        code, origin, computation, value,
        vatTreatment == null ? VatTreatment.NONE : vatTreatment,
        priority == null ? 100 : priority,
        effectiveDate);
  }

  public record Response(
      UUID id,
      String code,
      DiscountOrigin origin,
      DiscountComputationKind computation,
      BigDecimal value,
      VatTreatment vatTreatment,
      int priority,
      LocalDate effectiveDate) {

    public static Response from(DiscountRule r) {
      return new Response(
          r.getId(), r.getCode(), r.getOrigin(), r.getComputation(), r.getValue(),
          r.getVatTreatment(), r.getPriority(), r.effectiveDate());
    }
  }
}
