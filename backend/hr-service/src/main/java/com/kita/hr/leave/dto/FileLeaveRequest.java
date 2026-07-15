package com.kita.hr.leave.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Request to file a leave for a date range. */
public record FileLeaveRequest(
    @NotNull UUID employeeId,
    @NotNull UUID leaveTypeId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull @Positive BigDecimal duration,
    String reason) {}
