package com.kita.crm.discount;

import com.kita.crm.common.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure cascading discount math (no Spring/DB), so every case is exhaustively unit-testable.
 *
 * <p>Each tier applies to what the previous tier left, rounded per tier, so the breakdown always
 * reconciles: {@code base − Σ amountRemoved = finalPrice} to the cent (SC-002). The result is never
 * negative; a tier that would overshoot is capped and flagged (SC-005).
 */
public final class CascadingEngine {

  /** Flag raised when a tier was capped so the price could not go below zero. */
  public static final String FLAG_CAPPED = "CAPPED";

  private CascadingEngine() {}

  /** One tier to apply, in {@code priority} order. */
  public record Tier(
      String code,
      DiscountOrigin origin,
      DiscountComputationKind kind,
      BigDecimal value,
      int priority) {}

  /** What a tier removed, and the running base it was applied to. */
  public record BreakdownLine(
      String tierCode, DiscountOrigin origin, BigDecimal baseApplied, BigDecimal amountRemoved) {}

  public record Result(BigDecimal finalPrice, List<BreakdownLine> breakdown, List<String> flags) {

    /** Total removed across every tier — {@code base − totalRemoved} must equal {@code finalPrice}. */
    public BigDecimal totalRemoved() {
      return Money.sum(breakdown.stream().map(BreakdownLine::amountRemoved).toList());
    }
  }

  /**
   * Fold the tiers over {@code base} in priority order.
   *
   * <p>Ordering is by {@code priority} then {@code code}, so the outcome is independent of the order
   * tiers were supplied in (FR-006).
   */
  public static Result apply(BigDecimal base, List<Tier> tiers) {
    List<Tier> ordered = new ArrayList<>(tiers);
    ordered.sort(Comparator.comparingInt(Tier::priority).thenComparing(Tier::code));

    BigDecimal remaining = Money.round(base);
    List<BreakdownLine> breakdown = new ArrayList<>();
    List<String> flags = new ArrayList<>();

    for (Tier t : ordered) {
      BigDecimal amount =
          t.kind() == DiscountComputationKind.PERCENT
              ? Money.round(remaining.multiply(t.value()))
              : Money.round(t.value());
      if (amount.compareTo(remaining) > 0) {
        amount = remaining; // never take the price below zero
        if (!flags.contains(FLAG_CAPPED)) {
          flags.add(FLAG_CAPPED);
        }
      }
      if (amount.signum() <= 0) {
        continue; // a zero-value tier contributes no line
      }
      breakdown.add(new BreakdownLine(t.code(), t.origin(), remaining, amount));
      remaining = Money.round(remaining.subtract(amount));
    }
    return new Result(remaining, breakdown, flags);
  }
}
