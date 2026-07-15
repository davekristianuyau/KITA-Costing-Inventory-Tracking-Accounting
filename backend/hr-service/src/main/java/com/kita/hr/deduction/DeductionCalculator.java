package com.kita.hr.deduction;

import com.kita.hr.common.Money;
import com.kita.hr.payroll.PayComponentCategory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure computation of statutory + tax deductions for a payslip, in the correct order: pre-tax
 * statutory contributions first (reducing the taxable base), then withholding tax on taxable income
 * (FR-013, base ordering). Employer contributions are computed but excluded from net.
 */
public final class DeductionCalculator {

  private DeductionCalculator() {}

  /** One itemized deduction/contribution line. */
  public record Line(PayComponentCategory category, String code, String label, BigDecimal amount, String basis) {}

  /** {@code unmatched} lists rule codes whose table/bracket had no row covering the base. */
  public record Outcome(
      List<Line> lines,
      BigDecimal totalEmployeeDeductions,
      BigDecimal totalEmployerContrib,
      BigDecimal taxableIncome,
      List<String> unmatched) {}

  /**
   * @param statutory effective STATUTORY rules (with rows), applied on GROSS/BASIC
   * @param taxRules effective TAX rules (with rows), applied on TAXABLE_INCOME
   */
  public static Outcome compute(
      List<RuleWithRows> statutory, List<RuleWithRows> taxRules, BigDecimal gross, BigDecimal basic) {
    List<Line> lines = new ArrayList<>();
    List<BigDecimal> employeeDeductions = new ArrayList<>();
    List<BigDecimal> employerContribs = new ArrayList<>();
    List<String> unmatched = new ArrayList<>();

    // Deterministic order (by code) so the cascade and totals are reproducible.
    List<RuleWithRows> statSorted = new ArrayList<>(statutory);
    statSorted.sort(Comparator.comparing(r -> r.rule().getCode()));

    for (RuleWithRows rw : statSorted) {
      BigDecimal base = baseValue(rw.rule().getBase(), gross, basic, gross);
      DeductionRuleEngine.Amounts a = DeductionRuleEngine.evaluate(rw.rule(), rw.rows(), base);
      if (!a.matched()) {
        unmatched.add(rw.rule().getCode());
        continue;
      }
      if (a.employee().signum() != 0) {
        lines.add(
            new Line(
                PayComponentCategory.STATUTORY_DEDUCTION,
                rw.rule().getCode(),
                rw.rule().getAgency(),
                a.employee(),
                "statutory"));
        employeeDeductions.add(a.employee());
      }
      if (a.employer().signum() != 0) {
        lines.add(
            new Line(
                PayComponentCategory.EMPLOYER_CONTRIB,
                rw.rule().getCode() + "_ER",
                rw.rule().getAgency(),
                a.employer(),
                "employer share"));
        employerContribs.add(a.employer());
      }
    }

    BigDecimal preTaxStatutory = Money.sum(employeeDeductions);
    BigDecimal taxable = Money.round(gross.subtract(preTaxStatutory));

    List<RuleWithRows> taxSorted = new ArrayList<>(taxRules);
    taxSorted.sort(Comparator.comparing(r -> r.rule().getCode()));
    for (RuleWithRows rw : taxSorted) {
      DeductionRuleEngine.Amounts a = DeductionRuleEngine.evaluate(rw.rule(), rw.rows(), taxable);
      if (!a.matched()) {
        unmatched.add(rw.rule().getCode());
        continue;
      }
      if (a.employee().signum() != 0) {
        lines.add(
            new Line(
                PayComponentCategory.TAX,
                rw.rule().getCode(),
                rw.rule().getAgency(),
                a.employee(),
                "on taxable income"));
        employeeDeductions.add(a.employee());
      }
    }

    return new Outcome(
        lines, Money.sum(employeeDeductions), Money.sum(employerContribs), taxable, unmatched);
  }

  private static BigDecimal baseValue(
      DeductionBase base, BigDecimal gross, BigDecimal basic, BigDecimal taxable) {
    return switch (base) {
      case GROSS -> gross;
      case BASIC -> basic;
      case TAXABLE_INCOME -> taxable;
    };
  }
}
