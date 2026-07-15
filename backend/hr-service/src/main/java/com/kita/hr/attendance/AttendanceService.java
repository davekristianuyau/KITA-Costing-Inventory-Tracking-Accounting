package com.kita.hr.attendance;

import com.kita.hr.common.EffectiveDated;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Computes worked time and premiums from stored schedules, attendance, holidays, and premium rules. */
@Service
public class AttendanceService {

  private static final Map<PremiumKind, BigDecimal> DEFAULT_MULTIPLIERS =
      Map.of(
          PremiumKind.OVERTIME, new BigDecimal("1.25"),
          PremiumKind.HOLIDAY, new BigDecimal("1.00"),
          PremiumKind.NIGHT_DIFF, new BigDecimal("0.10"));

  private final WorkScheduleRepository schedules;
  private final AttendanceRecordRepository attendance;
  private final HolidayCalendarRepository holidays;
  private final PremiumRuleRepository premiumRules;
  private final BigDecimal hoursPerMonth;

  public AttendanceService(
      WorkScheduleRepository schedules,
      AttendanceRecordRepository attendance,
      HolidayCalendarRepository holidays,
      PremiumRuleRepository premiumRules,
      @Value("${hr.payroll.hours-per-month:176}") BigDecimal hoursPerMonth) {
    this.schedules = schedules;
    this.attendance = attendance;
    this.holidays = holidays;
    this.premiumRules = premiumRules;
    this.hoursPerMonth = hoursPerMonth;
  }

  /** Worked time + whether the employee is attendance-scheduled and whether attendance is missing. */
  public record WorkedTimeResult(
      AttendanceCalculator.WorkedTime workedTime, boolean scheduled, boolean incomplete) {}

  /** Premium pay + the incomplete/scheduled flags used by payroll. */
  public record PremiumOutcome(AttendanceCalculator.PremiumPay pay, boolean scheduled, boolean incomplete) {}

  @Transactional
  public AttendanceRecord ingest(AttendanceRecord record) {
    return attendance.save(record);
  }

  @Transactional
  public WorkSchedule createSchedule(WorkSchedule s) {
    return schedules.save(s);
  }

  @Transactional
  public HolidayCalendar createHoliday(HolidayCalendar h) {
    return holidays.save(h);
  }

  @Transactional
  public PremiumRule createPremiumRule(PremiumRule r) {
    return premiumRules.save(r);
  }

  @Transactional(readOnly = true)
  public WorkedTimeResult computeWorkedTime(UUID employeeId, LocalDate start, LocalDate end) {
    Optional<WorkSchedule> schedule =
        EffectiveDated.effectiveAsOf(schedules.findByEmployeeId(employeeId), end);
    if (schedule.isEmpty()) {
      return new WorkedTimeResult(null, false, false);
    }
    List<AttendanceRecord> dtrs =
        attendance.findByEmployeeIdAndWorkDateBetween(employeeId, start, end);
    if (dtrs.isEmpty()) {
      return new WorkedTimeResult(null, true, true);
    }
    AttendanceCalculator.WorkedTime wt =
        AttendanceCalculator.computeWorkedTime(spec(schedule.get()), entries(dtrs), holidayMap(start, end));
    return new WorkedTimeResult(wt, true, false);
  }

  @Transactional(readOnly = true)
  public PremiumOutcome premiumsFor(
      UUID employeeId, BigDecimal monthlyBasic, LocalDate start, LocalDate end) {
    WorkedTimeResult r = computeWorkedTime(employeeId, start, end);
    if (!r.scheduled() || r.incomplete()) {
      return new PremiumOutcome(
          new AttendanceCalculator.PremiumPay(
              zero(), zero(), zero(), zero()),
          r.scheduled(),
          r.incomplete());
    }
    BigDecimal hourlyRate = monthlyBasic.divide(hoursPerMonth, 6, RoundingMode.HALF_UP);
    Map<PremiumKind, BigDecimal> m = effectiveMultipliers(end);
    AttendanceCalculator.PremiumPay pay =
        AttendanceCalculator.computePremiums(
            r.workedTime(),
            hourlyRate,
            m.get(PremiumKind.OVERTIME),
            m.get(PremiumKind.HOLIDAY),
            m.get(PremiumKind.NIGHT_DIFF));
    return new PremiumOutcome(pay, true, false);
  }

  private Map<PremiumKind, BigDecimal> effectiveMultipliers(LocalDate asOf) {
    Map<PremiumKind, PremiumRule> latest = new EnumMap<>(PremiumKind.class);
    for (PremiumRule r : premiumRules.findByEffectiveDateLessThanEqual(asOf)) {
      PremiumRule cur = latest.get(r.getKind());
      if (cur == null || r.effectiveDate().isAfter(cur.effectiveDate())) {
        latest.put(r.getKind(), r);
      }
    }
    Map<PremiumKind, BigDecimal> out = new EnumMap<>(DEFAULT_MULTIPLIERS);
    latest.forEach((k, v) -> out.put(k, v.getMultiplier()));
    return out;
  }

  private AttendanceCalculator.ScheduleSpec spec(WorkSchedule s) {
    return new AttendanceCalculator.ScheduleSpec(
        s.getShiftStart(), s.getShiftEnd(), s.getBreakMinutes(), s.getStandardDailyHours(),
        s.getNightStart(), s.getNightEnd());
  }

  private List<AttendanceCalculator.DtrEntry> entries(List<AttendanceRecord> dtrs) {
    List<AttendanceCalculator.DtrEntry> out = new ArrayList<>();
    for (AttendanceRecord d : dtrs) {
      out.add(new AttendanceCalculator.DtrEntry(d.getWorkDate(), d.getTimeIn(), d.getTimeOut()));
    }
    return out;
  }

  private Map<LocalDate, HolidayType> holidayMap(LocalDate start, LocalDate end) {
    Map<LocalDate, HolidayType> map = new java.util.HashMap<>();
    for (HolidayCalendar h : holidays.findByHolidayDateBetween(start, end)) {
      map.put(h.getHolidayDate(), h.getType());
    }
    return map;
  }

  private static BigDecimal zero() {
    return new BigDecimal("0.00");
  }
}
