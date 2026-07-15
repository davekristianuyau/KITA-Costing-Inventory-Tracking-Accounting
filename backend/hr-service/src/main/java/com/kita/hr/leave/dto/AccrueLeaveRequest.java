package com.kita.hr.leave.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** Grant {@code periods} periods of a leave type's accrual policy to an employee (FR-018). */
public record AccrueLeaveRequest(
    @NotNull UUID employeeId, @NotNull UUID leaveTypeId, @NotNull @Positive Integer periods) {}
