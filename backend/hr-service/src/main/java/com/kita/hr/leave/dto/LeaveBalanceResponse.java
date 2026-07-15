package com.kita.hr.leave.dto;

import com.kita.hr.leave.LeaveBalance;
import java.math.BigDecimal;
import java.util.UUID;

public record LeaveBalanceResponse(UUID employeeId, UUID leaveTypeId, BigDecimal balance) {

  public static LeaveBalanceResponse from(LeaveBalance b) {
    return new LeaveBalanceResponse(b.getEmployeeId(), b.getLeaveTypeId(), b.getBalance());
  }
}
