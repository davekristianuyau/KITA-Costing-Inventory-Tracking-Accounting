package com.kita.hr.deduction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T029: pure unit tests for each rule computation shape (TABLE/BRACKET/PERCENT/FIXED). */
class DeductionRuleEngineTest {

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }

  private static DeductionRule rule(
      Computation c, BigDecimal rate, BigDecimal erRate, BigDecimal fixed, BigDecimal floor, BigDecimal cap) {
    return new DeductionRule(
        "X", DeductionKind.STATUTORY, c, DeductionBase.GROSS, "A", rate, erRate, fixed, floor, cap,
        LocalDate.of(2024, 1, 1));
  }

  @Test
  void tablePicksMatchingRow() {
    List<DeductionRuleRow> rows =
        List.of(
            new DeductionRuleRow(null, bd("0"), bd("19999.99"), bd("900"), bd("1900"), null, null, null),
            new DeductionRuleRow(null, bd("20000"), bd("29999.99"), bd("1350"), bd("2850"), null, null, null));
    DeductionRuleEngine.Amounts a =
        DeductionRuleEngine.evaluate(rule(Computation.TABLE, null, null, null, null, null), rows, bd("25000"));
    assertThat(a.employee()).isEqualByComparingTo("1350.00");
    assertThat(a.employer()).isEqualByComparingTo("2850.00");
  }

  @Test
  void bracketAppliesBaseTaxPlusRateOnExcess() {
    List<DeductionRuleRow> rows =
        List.of(
            new DeductionRuleRow(null, bd("0"), bd("20833"), bd("0"), null, bd("0"), bd("0"), bd("0")),
            new DeductionRuleRow(null, bd("20833.01"), bd("33332"), null, null, bd("0"), bd("0.15"), bd("20833")));
    DeductionRuleEngine.Amounts a =
        DeductionRuleEngine.evaluate(rule(Computation.BRACKET, null, null, null, null, null), rows, bd("27700"));
    assertThat(a.employee()).isEqualByComparingTo("1030.05"); // 0.15 * (27700 - 20833)
  }

  @Test
  void percentAppliesFloorAndCap() {
    DeductionRule r = rule(Computation.PERCENT, bd("0.025"), bd("0.025"), null, bd("10000"), bd("100000"));
    assertThat(DeductionRuleEngine.evaluate(r, List.of(), bd("30000")).employee())
        .isEqualByComparingTo("750.00");
    assertThat(DeductionRuleEngine.evaluate(r, List.of(), bd("5000")).employee())
        .isEqualByComparingTo("250.00"); // floored to 10000
    assertThat(DeductionRuleEngine.evaluate(r, List.of(), bd("200000")).employee())
        .isEqualByComparingTo("2500.00"); // capped to 100000
  }

  @Test
  void fixedReturnsFixedAmount() {
    DeductionRule r = rule(Computation.FIXED, null, null, bd("100.00"), null, null);
    assertThat(DeductionRuleEngine.evaluate(r, List.of(), bd("99999")).employee())
        .isEqualByComparingTo("100.00");
  }
}
