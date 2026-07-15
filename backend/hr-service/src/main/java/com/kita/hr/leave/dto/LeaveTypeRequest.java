package com.kita.hr.leave.dto;

import com.kita.hr.leave.AccrualPeriod;
import com.kita.hr.leave.PayTreatment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Request to define a leave type + accrual policy. */
public record LeaveTypeRequest(
    @NotBlank String code,
    @NotBlank String name,
    @NotNull PayTreatment payTreatment,
    @NotNull BigDecimal accrualRate,
    @NotNull AccrualPeriod accrualPeriod,
    BigDecimal accrualCap,
    boolean allowNegative) {}
