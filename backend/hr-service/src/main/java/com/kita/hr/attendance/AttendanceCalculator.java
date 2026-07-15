package com.kita.hr.attendance;

import com.kita.hr.common.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Pure time & attendance math (no Spring/DB): turns raw daily time records into worked time against a
 * schedule, and computes premium pay (overtime, holiday, night differential). Same-day shifts only in
 * this increment (time_out ≥ time_in). Hours are computed to 2 decimals.
 */
public final class AttendanceCalculator {

  private AttendanceCalculator() {}

  /** Minimal schedule inputs the calculator needs. */
  public record ScheduleSpec(
      LocalTime shiftStart,
      LocalTime shiftEnd,
      int breakMinutes,
      BigDecimal standardDailyHours,
      LocalTime nightStart,
      LocalTime nightEnd) {}

  /** One raw daily time record. */
  public record DtrEntry(LocalDate date, LocalTime in, LocalTime out) {}

  /** Computed worked time for a period. */
  public record WorkedTime(
      BigDecimal regularHours,
      BigDecimal overtimeHours,
      int tardinessMinutes,
      int undertimeMinutes,
      BigDecimal nightHours,
      BigDecimal holidayHours,
      int daysWorked) {}

  /** Premium pay amounts derived from worked time. */
  public record PremiumPay(
      BigDecimal overtimePay, BigDecimal holidayPay, BigDecimal nightDiffPay, BigDecimal total) {}

  public static WorkedTime computeWorkedTime(
      ScheduleSpec s, List<DtrEntry> entries, Map<LocalDate, ?> holidays) {
    BigDecimal regular = BigDecimal.ZERO;
    BigDecimal overtime = BigDecimal.ZERO;
    BigDecimal night = BigDecimal.ZERO;
    BigDecimal holiday = BigDecimal.ZERO;
    int tardiness = 0;
    int undertime = 0;
    int days = 0;

    for (DtrEntry e : entries) {
      int grossMin = minutes(e.in(), e.out());
      int workedMin = Math.max(0, grossMin - s.breakMinutes());
      BigDecimal workedHours = hours(workedMin);
      BigDecimal dayRegular = workedHours.min(s.standardDailyHours());
      BigDecimal dayOvertime = workedHours.subtract(dayRegular).max(BigDecimal.ZERO);

      regular = regular.add(dayRegular);
      overtime = overtime.add(dayOvertime);
      tardiness += Math.max(0, minutesBetween(s.shiftStart(), e.in()));
      undertime += Math.max(0, minutesBetween(e.out(), s.shiftEnd()));
      night = night.add(hours(nightOverlapMinutes(e.in(), e.out(), s.nightStart(), s.nightEnd())));
      if (holidays.containsKey(e.date())) {
        holiday = holiday.add(dayRegular);
      }
      days++;
    }
    return new WorkedTime(
        scale(regular), scale(overtime), tardiness, undertime, scale(night), scale(holiday), days);
  }

  public static PremiumPay computePremiums(
      WorkedTime wt,
      BigDecimal hourlyRate,
      BigDecimal overtimeMultiplier,
      BigDecimal holidayMultiplier,
      BigDecimal nightDiffMultiplier) {
    BigDecimal ot = Money.round(wt.overtimeHours().multiply(hourlyRate).multiply(overtimeMultiplier));
    BigDecimal hol = Money.round(wt.holidayHours().multiply(hourlyRate).multiply(holidayMultiplier));
    BigDecimal nd = Money.round(wt.nightHours().multiply(hourlyRate).multiply(nightDiffMultiplier));
    return new PremiumPay(ot, hol, nd, Money.sum(List.of(ot, hol, nd)));
  }

  // --- helpers ---

  private static int minutes(LocalTime in, LocalTime out) {
    return Math.max(0, out.toSecondOfDay() / 60 - in.toSecondOfDay() / 60);
  }

  private static int minutesBetween(LocalTime from, LocalTime to) {
    return to.toSecondOfDay() / 60 - from.toSecondOfDay() / 60;
  }

  private static BigDecimal hours(int min) {
    return new BigDecimal(min).divide(new BigDecimal(60), 4, RoundingMode.HALF_UP);
  }

  private static BigDecimal scale(BigDecimal v) {
    return v.setScale(2, RoundingMode.HALF_UP);
  }

  /** Minutes of [in,out] that fall in the night window, which may wrap past midnight. */
  private static int nightOverlapMinutes(LocalTime in, LocalTime out, LocalTime ns, LocalTime ne) {
    if (ns == null || ne == null) {
      return 0;
    }
    int inM = in.toSecondOfDay() / 60;
    int outM = out.toSecondOfDay() / 60;
    int total = 0;
    if (ns.isBefore(ne)) {
      total += overlap(inM, outM, ns.toSecondOfDay() / 60, ne.toSecondOfDay() / 60);
    } else {
      // wraps midnight: [ns, 24:00) and [00:00, ne)
      total += overlap(inM, outM, ns.toSecondOfDay() / 60, 24 * 60);
      total += overlap(inM, outM, 0, ne.toSecondOfDay() / 60);
    }
    return total;
  }

  private static int overlap(int aStart, int aEnd, int bStart, int bEnd) {
    return Math.max(0, Math.min(aEnd, bEnd) - Math.max(aStart, bStart));
  }
}
