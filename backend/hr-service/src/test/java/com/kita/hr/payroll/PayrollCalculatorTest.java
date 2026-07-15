package com.kita.hr.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.hr.employee.PayFrequency;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** T020: unit tests for gross/pro-ration math (no Spring, no Docker). */
class PayrollCalculatorTest {

  private static final LocalDate START = LocalDate.of(2026, 1, 1);
  private static final LocalDate END = LocalDate.of(2026, 1, 31);
  private static final BigDecimal BASIC = new BigDecimal("30000.00");

  @Test
  void fullMonthlyPeriodPaysFullBasic() {
    assertThat(grossFullMonth(PayFrequency.MONTHLY)).isEqualByComparingTo("30000.00");
  }

  @Test
  void semiMonthlyPeriodPaysHalf() {
    assertThat(grossFullMonth(PayFrequency.SEMI_MONTHLY)).isEqualByComparingTo("15000.00");
  }

  @Test
  void proratesForMidPeriodHire() {
    // hired Jan 17 → active Jan 17..31 = 15 of 31 days → 30000 * 15/31 = 14516.13
    BigDecimal gross =
        PayrollCalculator.grossBasic(
            BASIC, PayFrequency.MONTHLY, START, END, LocalDate.of(2026, 1, 17), null);
    assertThat(gross).isEqualByComparingTo("14516.13");
  }

  @Test
  void zeroWhenSeparatedBeforePeriod() {
    BigDecimal gross =
        PayrollCalculator.grossBasic(
            BASIC, PayFrequency.MONTHLY, START, END, LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31));
    assertThat(gross).isEqualByComparingTo("0.00");
  }

  private BigDecimal grossFullMonth(PayFrequency freq) {
    return PayrollCalculator.grossBasic(BASIC, freq, START, END, LocalDate.of(2025, 1, 1), null);
  }
}
