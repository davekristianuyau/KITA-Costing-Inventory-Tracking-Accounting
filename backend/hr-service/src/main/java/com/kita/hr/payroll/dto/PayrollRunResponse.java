package com.kita.hr.payroll.dto;

import com.kita.hr.payroll.PayrollRun;
import com.kita.hr.payroll.RunStatus;
import com.kita.hr.payroll.RunType;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollRunResponse(
    UUID id,
    RunStatus status,
    RunType type,
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate payDate) {

  public static PayrollRunResponse from(PayrollRun r) {
    return new PayrollRunResponse(
        r.getId(),
        r.getStatus(),
        r.getType(),
        r.getPayPeriod().getStartDate(),
        r.getPayPeriod().getEndDate(),
        r.getPayPeriod().getPayDate());
  }
}
