package com.kita.hr.employee;

import java.time.LocalDate;

public record StatusHistoryResponse(
    EmployeeStatus previousStatus, EmployeeStatus status, LocalDate effectiveDate, String changedBy) {

  public static StatusHistoryResponse from(EmployeeStatusHistory h) {
    return new StatusHistoryResponse(
        h.getPreviousStatus(), h.getStatus(), h.getEffectiveDate(), h.getChangedBy());
  }
}
