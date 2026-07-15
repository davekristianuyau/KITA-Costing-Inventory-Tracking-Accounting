package com.kita.hr.payroll.dto;

import com.kita.hr.payroll.RunType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** {@code type} defaults to REGULAR; {@code employeeIds} scopes the run, empty/absent = all eligible. */
public record CreatePayrollRunRequest(
    @Valid @NotNull PayPeriodRequest period,
    RunType type,
    UUID adjustsRunId,
    List<UUID> employeeIds) {}
