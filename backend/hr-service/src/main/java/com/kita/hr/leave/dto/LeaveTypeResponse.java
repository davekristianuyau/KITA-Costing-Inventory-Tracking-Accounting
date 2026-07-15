package com.kita.hr.leave.dto;

import com.kita.hr.leave.AccrualPeriod;
import com.kita.hr.leave.LeaveType;
import com.kita.hr.leave.PayTreatment;
import java.math.BigDecimal;
import java.util.UUID;

public record LeaveTypeResponse(
    UUID id,
    String code,
    String name,
    PayTreatment payTreatment,
    BigDecimal accrualRate,
    AccrualPeriod accrualPeriod,
    BigDecimal accrualCap,
    boolean allowNegative) {

  public static LeaveTypeResponse from(LeaveType t) {
    return new LeaveTypeResponse(
        t.getId(),
        t.getCode(),
        t.getName(),
        t.getPayTreatment(),
        t.getAccrualRate(),
        t.getAccrualPeriod(),
        t.getAccrualCap(),
        t.isAllowNegative());
  }
}
