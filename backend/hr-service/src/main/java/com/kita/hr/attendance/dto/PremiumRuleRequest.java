package com.kita.hr.attendance.dto;

import com.kita.hr.attendance.PremiumKind;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PremiumRuleRequest(
    @NotNull PremiumKind kind, @NotNull BigDecimal multiplier, @NotNull LocalDate effectiveDate) {}
