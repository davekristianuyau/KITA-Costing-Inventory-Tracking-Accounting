package com.kita.hr.deduction;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.hr.payroll.PayComponentCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T030: golden-value test mirroring the Philippines seed (SSS/PhilHealth/Pag-IBIG/BIR) and verifying
 * base ordering (pre-tax statutory reduces the taxable base before withholding tax). Pure — no DB.
 */
class DeductionCalculatorGoldenTest {

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }

  private static final LocalDate D = LocalDate.of(2024, 1, 1);

  private RuleWithRows sss() {
    DeductionRule r =
        new DeductionRule("SSS", DeductionKind.STATUTORY, Computation.TABLE, DeductionBase.GROSS, "SSS",
            null, null, null, null, null, D);
    return new RuleWithRows(
        r,
        List.of(
            new DeductionRuleRow(null, bd("0"), bd("19999.99"), bd("900"), bd("1900"), null, null, null),
            new DeductionRuleRow(null, bd("20000"), bd("29999.99"), bd("1350"), bd("2850"), null, null, null),
            new DeductionRuleRow(null, bd("30000"), null, bd("1350"), bd("2850"), null, null, null)));
  }

  private RuleWithRows percent(String code, BigDecimal rate, BigDecimal cap, BigDecimal floor) {
    return new RuleWithRows(
        new DeductionRule(code, DeductionKind.STATUTORY, Computation.PERCENT, DeductionBase.BASIC, code,
            rate, rate, null, floor, cap, D),
        List.of());
  }

  private RuleWithRows bir() {
    DeductionRule r =
        new DeductionRule("BIR_WHT", DeductionKind.TAX, Computation.BRACKET, DeductionBase.TAXABLE_INCOME,
            "BIR", null, null, null, null, null, D);
    return new RuleWithRows(
        r,
        List.of(
            new DeductionRuleRow(null, bd("0"), bd("20833"), null, null, bd("0"), bd("0"), bd("0")),
            new DeductionRuleRow(null, bd("20833.01"), bd("33332"), null, null, bd("0"), bd("0.15"), bd("20833"))));
  }

  @Test
  void thirtyThousandGrossMatchesSeed() {
    List<RuleWithRows> statutory =
        List.of(sss(), percent("PHILHEALTH", bd("0.025"), bd("100000"), bd("10000")),
            percent("PAGIBIG", bd("0.02"), bd("10000"), null));
    DeductionCalculator.Outcome out =
        DeductionCalculator.compute(statutory, List.of(bir()), bd("30000.00"), bd("30000.00"));

    assertThat(out.taxableIncome()).isEqualByComparingTo("27700.00"); // 30000 - (1350+750+200)
    assertThat(out.totalEmployeeDeductions()).isEqualByComparingTo("3330.05"); // 2300 + 1030.05 tax
    assertThat(out.totalEmployerContrib()).isEqualByComparingTo("3800.00"); // 2850+750+200

    assertThat(amount(out, PayComponentCategory.STATUTORY_DEDUCTION, "SSS")).isEqualByComparingTo("1350.00");
    assertThat(amount(out, PayComponentCategory.STATUTORY_DEDUCTION, "PHILHEALTH")).isEqualByComparingTo("750.00");
    assertThat(amount(out, PayComponentCategory.STATUTORY_DEDUCTION, "PAGIBIG")).isEqualByComparingTo("200.00");
    assertThat(amount(out, PayComponentCategory.TAX, "BIR_WHT")).isEqualByComparingTo("1030.05");
  }

  private BigDecimal amount(DeductionCalculator.Outcome out, PayComponentCategory cat, String code) {
    return out.lines().stream()
        .filter(l -> l.category() == cat && l.code().equals(code))
        .findFirst()
        .orElseThrow()
        .amount();
  }
}
