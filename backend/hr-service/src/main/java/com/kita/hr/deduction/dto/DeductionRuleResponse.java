package com.kita.hr.deduction.dto;

import com.kita.hr.deduction.Computation;
import com.kita.hr.deduction.DeductionBase;
import com.kita.hr.deduction.DeductionKind;
import com.kita.hr.deduction.DeductionRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DeductionRuleResponse(
    UUID id,
    String code,
    DeductionKind kind,
    Computation computation,
    DeductionBase base,
    String agency,
    BigDecimal rate,
    LocalDate effectiveDate) {

  public static DeductionRuleResponse from(DeductionRule r) {
    return new DeductionRuleResponse(
        r.getId(),
        r.getCode(),
        r.getKind(),
        r.getComputation(),
        r.getBase(),
        r.getAgency(),
        r.getRate(),
        r.effectiveDate());
  }
}
