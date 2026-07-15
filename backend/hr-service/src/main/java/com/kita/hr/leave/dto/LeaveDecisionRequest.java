package com.kita.hr.leave.dto;

import jakarta.validation.constraints.NotNull;

/** Manager's approve/reject decision on a filed leave request. */
public record LeaveDecisionRequest(@NotNull Boolean approved, String decidedBy) {}
