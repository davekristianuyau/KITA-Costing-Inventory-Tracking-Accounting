package com.kita.hr.leave;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositories for the leave module (grouped; each is a top-level interface). */
public final class LeaveRepositories {
  private LeaveRepositories() {}
}

interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {
  boolean existsByCode(String code);
}

interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {
  Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeId(UUID employeeId, UUID leaveTypeId);

  List<LeaveBalance> findByEmployeeId(UUID employeeId);
}

interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
  List<LeaveRequest> findByEmployeeIdAndStatus(UUID employeeId, LeaveStatus status);

  // Approved leave overlapping [start, end] for an employee: existing.start <= end AND existing.end >= start.
  List<LeaveRequest> findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
      UUID employeeId, LeaveStatus status, LocalDate end, LocalDate start);
}
