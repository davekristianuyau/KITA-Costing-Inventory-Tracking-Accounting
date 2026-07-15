package com.kita.hr.employee;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Request to add an effective-dated compensation record. */
public record CompensationRequest(
    @NotNull LocalDate effectiveDate,
    @NotNull @Positive BigDecimal basicPay,
    @NotNull PayFrequency payFrequency) {}
