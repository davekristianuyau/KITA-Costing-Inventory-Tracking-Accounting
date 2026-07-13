package com.kita.hr.payroll.dto;

import com.kita.hr.payroll.RunStatus;
import java.util.List;
import java.util.UUID;

public record ComputeResultResponse(
    UUID runId, RunStatus status, int payslipCount, List<String> flagged) {}
