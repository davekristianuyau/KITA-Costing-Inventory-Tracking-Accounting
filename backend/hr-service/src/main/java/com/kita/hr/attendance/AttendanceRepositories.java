package com.kita.hr.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositories for the attendance module (grouped; each is a top-level interface). */
public final class AttendanceRepositories {
  private AttendanceRepositories() {}
}

interface WorkScheduleRepository extends JpaRepository<WorkSchedule, UUID> {
  List<WorkSchedule> findByEmployeeId(UUID employeeId);
}

interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {
  List<AttendanceRecord> findByEmployeeIdAndWorkDateBetween(UUID employeeId, LocalDate start, LocalDate end);
}

interface HolidayCalendarRepository extends JpaRepository<HolidayCalendar, UUID> {
  List<HolidayCalendar> findByHolidayDateBetween(LocalDate start, LocalDate end);
}

interface PremiumRuleRepository extends JpaRepository<PremiumRule, UUID> {
  List<PremiumRule> findByEffectiveDateLessThanEqual(LocalDate date);
}
