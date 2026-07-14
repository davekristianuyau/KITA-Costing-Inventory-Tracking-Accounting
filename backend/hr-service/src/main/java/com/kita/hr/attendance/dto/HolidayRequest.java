package com.kita.hr.attendance.dto;

import com.kita.hr.attendance.HolidayType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record HolidayRequest(
    @NotNull LocalDate holidayDate,
    @NotNull String name,
    @NotNull HolidayType type,
    @NotNull BigDecimal payMultiplier) {}
