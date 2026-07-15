package com.kita.hr.leave;

/** Lifecycle of a leave request: FILED → APPROVED | REJECTED | CANCELLED. */
public enum LeaveStatus {
  FILED,
  APPROVED,
  REJECTED,
  CANCELLED
}
