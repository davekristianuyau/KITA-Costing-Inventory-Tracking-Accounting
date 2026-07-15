package com.kita.hr.deduction;

import com.kita.hr.common.Money;
import java.math.BigDecimal;
import java.util.List;

/**
 * Pure evaluation of a single deduction rule against a base amount (no Spring/DB), so every
 * computation shape (TABLE/BRACKET/PERCENT/FIXED) is exhaustively unit-testable.
 */
public final class DeductionRuleEngine {

  private DeductionRuleEngine() {}

  /**
   * Employee and employer amounts produced by a rule. {@code matched} is false when a TABLE/BRACKET
   * rule has no row covering the base — the employee must then be flagged, never silently
   * zero-rated (spec Edge Cases).
   */
  public record Amounts(BigDecimal employee, BigDecimal employer, boolean matched) {}

  public static Amounts evaluate(DeductionRule rule, List<DeductionRuleRow> rows, BigDecimal base) {
    return switch (rule.getComputation()) {
      case TABLE -> table(rows, base);
      case BRACKET -> bracket(rows, base);
      case PERCENT -> percent(rule, base);
      // PERCENT/FIXED are computed from the rule itself, so there is no range to miss.
      case FIXED -> new Amounts(Money.round(nz(rule.getFixedAmount())), Money.zero(), true);
    };
  }

  private static Amounts table(List<DeductionRuleRow> rows, BigDecimal base) {
    for (DeductionRuleRow r : rows) {
      if (r.contains(base)) {
        return new Amounts(
            Money.round(nz(r.getEmployeeAmount())), Money.round(nz(r.getEmployerAmount())), true);
      }
    }
    return new Amounts(Money.zero(), Money.zero(), false);
  }

  private static Amounts bracket(List<DeductionRuleRow> rows, BigDecimal base) {
    for (DeductionRuleRow r : rows) {
      if (r.contains(base)) {
        BigDecimal excess = base.subtract(nz(r.getExcessOver()));
        BigDecimal tax = nz(r.getBaseTax()).add(nz(r.getRateOnExcess()).multiply(excess));
        return new Amounts(Money.round(tax), Money.zero(), true);
      }
    }
    return new Amounts(Money.zero(), Money.zero(), false);
  }

  private static Amounts percent(DeductionRule rule, BigDecimal base) {
    BigDecimal eff = base;
    if (rule.getFloor() != null && eff.compareTo(rule.getFloor()) < 0) {
      eff = rule.getFloor();
    }
    if (rule.getCap() != null && eff.compareTo(rule.getCap()) > 0) {
      eff = rule.getCap();
    }
    BigDecimal employee = Money.round(eff.multiply(nz(rule.getRate())));
    BigDecimal employer =
        rule.getEmployerRate() == null
            ? Money.zero()
            : Money.round(eff.multiply(rule.getEmployerRate()));
    return new Amounts(employee, employer, true);
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
