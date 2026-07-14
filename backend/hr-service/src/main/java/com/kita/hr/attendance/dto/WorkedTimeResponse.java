package com.kita.hr.attendance.dto;

import com.kita.hr.attendance.AttendanceService;
import java.math.BigDecimal;

public record WorkedTimeResponse(
    boolean scheduled,
    boolean incomplete,
    BigDecimal regularHours,
    BigDecimal overtimeHours,
    int tardinessMinutes,
    int undertimeMinutes,
    BigDecimal nightHours,
    BigDecimal holidayHours,
    int daysWorked) {

  public static WorkedTimeResponse from(AttendanceService.WorkedTimeResult r) {
    if (r.workedTime() == null) {
      BigDecimal z = new BigDecimal("0.00");
      return new WorkedTimeResponse(r.scheduled(), r.incomplete(), z, z, 0, 0, z, z, 0);
    }
    var wt = r.workedTime();
    return new WorkedTimeResponse(
        r.scheduled(),
        r.incomplete(),
        wt.regularHours(),
        wt.overtimeHours(),
        wt.tardinessMinutes(),
        wt.undertimeMinutes(),
        wt.nightHours(),
        wt.holidayHours(),
        wt.daysWorked());
  }
}
