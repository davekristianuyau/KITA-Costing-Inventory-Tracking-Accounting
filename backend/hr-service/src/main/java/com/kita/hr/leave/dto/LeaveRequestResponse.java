package com.kita.hr.leave.dto;

import com.kita.hr.leave.LeaveRequest;
import com.kita.hr.leave.LeaveStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestResponse(
    UUID id,
    UUID employeeId,
    UUID leaveTypeId,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal duration,
    LeaveStatus status) {

  public static LeaveRequestResponse from(LeaveRequest r) {
    return new LeaveRequestResponse(
        r.getId(),
        r.getEmployeeId(),
        r.getLeaveTypeId(),
        r.getStartDate(),
        r.getEndDate(),
        r.getDuration(),
        r.getStatus());
  }
}
