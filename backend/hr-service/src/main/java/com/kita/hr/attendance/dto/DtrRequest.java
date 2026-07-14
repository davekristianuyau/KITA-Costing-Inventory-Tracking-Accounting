package com.kita.hr.attendance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record DtrRequest(
    @NotNull UUID employeeId,
    @NotNull LocalDate workDate,
    @NotNull LocalTime timeIn,
    @NotNull LocalTime timeOut,
    String source) {}
