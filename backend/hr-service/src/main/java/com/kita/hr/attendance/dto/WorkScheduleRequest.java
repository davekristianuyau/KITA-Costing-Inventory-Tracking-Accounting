package com.kita.hr.attendance.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record WorkScheduleRequest(
    @NotNull UUID employeeId,
    @NotNull LocalDate effectiveDate,
    @NotNull LocalTime shiftStart,
    @NotNull LocalTime shiftEnd,
    int breakMinutes,
    @NotNull BigDecimal standardDailyHours,
    LocalTime nightStart,
    LocalTime nightEnd) {}
