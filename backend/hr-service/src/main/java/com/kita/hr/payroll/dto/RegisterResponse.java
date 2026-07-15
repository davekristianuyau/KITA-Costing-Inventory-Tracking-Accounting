package com.kita.hr.payroll.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RegisterResponse(
    UUID runId,
    int employeeCount,
    BigDecimal totalGross,
    BigDecimal totalDeductions,
    BigDecimal totalEmployerContrib,
    BigDecimal totalNet,
    List<PayslipResponse> payslips) {}
