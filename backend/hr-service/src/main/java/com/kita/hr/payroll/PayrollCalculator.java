package com.kita.hr.payroll;

import com.kita.hr.common.Money;
import com.kita.hr.employee.PayFrequency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure payroll math (no Spring/DB) so it is exhaustively unit-testable. For US2 gross is the
 * pro-rated basic pay; deductions/premiums are layered on in later stories.
 */
public final class PayrollCalculator {

  private static final BigDecimal HALF = new BigDecimal("0.5");

  private PayrollCalculator() {}

  /** Fraction of a monthly basic paid for one period of the given frequency. */
  public static BigDecimal periodFactor(PayFrequency frequency) {
    return frequency == PayFrequency.SEMI_MONTHLY ? HALF : BigDecimal.ONE;
  }

  public static long totalDays(LocalDate start, LocalDate end) {
    return ChronoUnit.DAYS.between(start, end) + 1;
  }

  /** Days the employee is active within [start, end], honoring hire/separation dates. */
  public static long activeDays(
      LocalDate start, LocalDate end, LocalDate hired, LocalDate separated) {
    LocalDate from = (hired != null && hired.isAfter(start)) ? hired : start;
    LocalDate to = (separated != null && separated.isBefore(end)) ? separated : end;
    if (to.isBefore(from)) {
      return 0;
    }
    return ChronoUnit.DAYS.between(from, to) + 1;
  }

  /** Pro-rated basic pay for the period, rounded to the cent. Zero if not active in the period. */
  public static BigDecimal grossBasic(
      BigDecimal monthlyBasic,
      PayFrequency periodFrequency,
      LocalDate start,
      LocalDate end,
      LocalDate hired,
      LocalDate separated) {
    long total = totalDays(start, end);
    long active = activeDays(start, end, hired, separated);
    if (active <= 0 || total <= 0) {
      return Money.zero();
    }
    BigDecimal periodBasic = monthlyBasic.multiply(periodFactor(periodFrequency));
    BigDecimal fraction =
        BigDecimal.valueOf(active).divide(BigDecimal.valueOf(total), 12, RoundingMode.HALF_UP);
    return Money.round(periodBasic.multiply(fraction));
  }
}
