package com.kita.hr.payroll.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PayslipResponse(
    UUID id,
    UUID employeeId,
    BigDecimal gross,
    BigDecimal totalDeductions,
    BigDecimal totalEmployerContrib,
    BigDecimal netPay,
    List<PayComponentResponse> components) {}
