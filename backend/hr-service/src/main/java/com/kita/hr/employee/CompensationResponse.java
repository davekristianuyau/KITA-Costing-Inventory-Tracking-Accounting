package com.kita.hr.employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Compensation record view returned by the API. */
public record CompensationResponse(
    UUID id, LocalDate effectiveDate, BigDecimal basicPay, PayFrequency payFrequency) {

  public static CompensationResponse from(CompensationRecord c) {
    return new CompensationResponse(
        c.getId(), c.getEffectiveDate(), c.getBasicPay(), c.getPayFrequency());
  }
}
