package com.kita.hr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T038/T039: pure unit tests for worked-time and premium computation. */
class AttendanceCalculatorTest {

  private static final AttendanceCalculator.ScheduleSpec SCHED =
      new AttendanceCalculator.ScheduleSpec(
          LocalTime.of(8, 0),
          LocalTime.of(17, 0),
          60,
          new BigDecimal("8.00"),
          LocalTime.of(22, 0),
          LocalTime.of(6, 0));

  @Test
  void splitsRegularOvertimeAndTardiness() {
    var entries =
        List.of(
            new AttendanceCalculator.DtrEntry(
                LocalDate.of(2026, 1, 5), LocalTime.of(8, 0), LocalTime.of(19, 0)), // 10h → 8 reg + 2 OT
            new AttendanceCalculator.DtrEntry(
                LocalDate.of(2026, 1, 6), LocalTime.of(8, 15), LocalTime.of(17, 0))); // 7.75h, 15m late
    var wt = AttendanceCalculator.computeWorkedTime(SCHED, entries, Map.of());
    assertThat(wt.regularHours()).isEqualByComparingTo("15.75");
    assertThat(wt.overtimeHours()).isEqualByComparingTo("2.00");
    assertThat(wt.tardinessMinutes()).isEqualTo(15);
    assertThat(wt.daysWorked()).isEqualTo(2);
  }

  @Test
  void countsHolidayAndNightHours() {
    var entries =
        List.of(
            new AttendanceCalculator.DtrEntry(
                LocalDate.of(2026, 1, 10), LocalTime.of(8, 0), LocalTime.of(17, 0)), // holiday, 8h
            new AttendanceCalculator.DtrEntry(
                LocalDate.of(2026, 1, 11), LocalTime.of(20, 0), LocalTime.of(23, 0))); // 1h in night
    var holidays = Map.of(LocalDate.of(2026, 1, 10), "REGULAR");
    var wt = AttendanceCalculator.computeWorkedTime(SCHED, entries, holidays);
    assertThat(wt.holidayHours()).isEqualByComparingTo("8.00");
    assertThat(wt.nightHours()).isEqualByComparingTo("1.00");
  }

  @Test
  void computesPremiumsFromRates() {
    var wt =
        new AttendanceCalculator.WorkedTime(
            new BigDecimal("160.00"),
            new BigDecimal("2.00"),
            0,
            0,
            new BigDecimal("1.00"),
            new BigDecimal("8.00"),
            20);
    var p =
        AttendanceCalculator.computePremiums(
            wt, new BigDecimal("100.00"), new BigDecimal("1.25"), new BigDecimal("1.00"),
            new BigDecimal("0.10"));
    assertThat(p.overtimePay()).isEqualByComparingTo("250.00"); // 2 * 100 * 1.25
    assertThat(p.holidayPay()).isEqualByComparingTo("800.00"); // 8 * 100 * 1.00
    assertThat(p.nightDiffPay()).isEqualByComparingTo("10.00"); // 1 * 100 * 0.10
    assertThat(p.total()).isEqualByComparingTo("1060.00");
  }
}
