package com.kita.hr.api;

import com.kita.hr.attendance.AttendanceRecord;
import com.kita.hr.attendance.AttendanceService;
import com.kita.hr.attendance.HolidayCalendar;
import com.kita.hr.attendance.PremiumRule;
import com.kita.hr.attendance.WorkSchedule;
import com.kita.hr.attendance.dto.DtrRequest;
import com.kita.hr.attendance.dto.HolidayRequest;
import com.kita.hr.attendance.dto.PremiumRuleRequest;
import com.kita.hr.attendance.dto.WorkScheduleRequest;
import com.kita.hr.attendance.dto.WorkedTimeResponse;
import com.kita.hr.common.security.CallerContext;
import com.kita.hr.common.security.Role;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttendanceController {

  private final AttendanceService attendance;
  private final CallerContext caller;

  public AttendanceController(AttendanceService attendance, CallerContext caller) {
    this.attendance = attendance;
    this.caller = caller;
  }

  @PostMapping("/api/hr/attendance")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void ingest(@Valid @RequestBody List<DtrRequest> records) {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER);
    for (DtrRequest r : records) {
      attendance.ingest(
          new AttendanceRecord(r.employeeId(), r.workDate(), r.timeIn(), r.timeOut(), r.source()));
    }
  }

  @GetMapping("/api/hr/attendance/worked-time")
  public WorkedTimeResponse workedTime(
      @RequestParam UUID employeeId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER, Role.MANAGER);
    return WorkedTimeResponse.from(attendance.computeWorkedTime(employeeId, start, end));
  }

  @PostMapping("/api/hr/work-schedules")
  @ResponseStatus(HttpStatus.CREATED)
  public void createSchedule(@Valid @RequestBody WorkScheduleRequest r) {
    caller.require(Role.HR_ADMIN);
    attendance.createSchedule(
        new WorkSchedule(
            r.employeeId(), r.effectiveDate(), r.shiftStart(), r.shiftEnd(), r.breakMinutes(),
            r.standardDailyHours(), r.nightStart(), r.nightEnd()));
  }

  @PostMapping("/api/hr/holidays")
  @ResponseStatus(HttpStatus.CREATED)
  public void createHoliday(@Valid @RequestBody HolidayRequest r) {
    caller.require(Role.HR_ADMIN);
    attendance.createHoliday(
        new HolidayCalendar(r.holidayDate(), r.name(), r.type(), r.payMultiplier()));
  }

  @PostMapping("/api/hr/premium-rules")
  @ResponseStatus(HttpStatus.CREATED)
  public void createPremiumRule(@Valid @RequestBody PremiumRuleRequest r) {
    caller.require(Role.HR_ADMIN);
    attendance.createPremiumRule(new PremiumRule(r.kind(), r.multiplier(), r.effectiveDate()));
  }
}
