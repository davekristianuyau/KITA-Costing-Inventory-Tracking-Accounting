package com.kita.hr.deduction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateLoanRequest(
    @NotNull @Positive BigDecimal principal,
    @NotNull @Positive BigDecimal installmentAmount,
    @NotNull @Positive Integer installmentsTotal) {}
