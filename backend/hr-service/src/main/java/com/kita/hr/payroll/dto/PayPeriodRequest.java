package com.kita.hr.payroll.dto;

import com.kita.hr.employee.PayFrequency;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PayPeriodRequest(
    @NotNull PayFrequency frequency,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull LocalDate payDate) {}
