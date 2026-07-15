package com.kita.crm.discount;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T018/T019: the cascading fold — golden values, reconciliation, determinism, and caps. */
class CascadingEngineTest {

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }

  private static CascadingEngine.Tier percent(String code, String value, int priority) {
    return new CascadingEngine.Tier(
        code, DiscountOrigin.PROMOTIONAL, DiscountComputationKind.PERCENT, bd(value), priority);
  }

  private static CascadingEngine.Tier fixed(String code, String value, int priority) {
    return new CascadingEngine.Tier(
        code, DiscountOrigin.PROMOTIONAL, DiscountComputationKind.FIXED, bd(value), priority);
  }

  /** SC-001: base 1000, ‑25% then ‑5% → 712.50, breakdown 250.00 then 37.50. */
  @Test
  void goldenExampleFromTheContract() {
    CascadingEngine.Result r =
        CascadingEngine.apply(bd("1000"), List.of(percent("P25", "0.25", 1), percent("P5", "0.05", 2)));

    assertThat(r.finalPrice()).isEqualByComparingTo("712.50");
    assertThat(r.breakdown()).hasSize(2);

    assertThat(r.breakdown().get(0).tierCode()).isEqualTo("P25");
    assertThat(r.breakdown().get(0).baseApplied()).isEqualByComparingTo("1000.00");
    assertThat(r.breakdown().get(0).amountRemoved()).isEqualByComparingTo("250.00");

    // The second tier applies to what the first left (750), not to the original base.
    assertThat(r.breakdown().get(1).tierCode()).isEqualTo("P5");
    assertThat(r.breakdown().get(1).baseApplied()).isEqualByComparingTo("750.00");
    assertThat(r.breakdown().get(1).amountRemoved()).isEqualByComparingTo("37.50");

    assertThat(r.flags()).isEmpty();
  }

  /** SC-002: base − Σ(removed) = final, to the cent. */
  @Test
  void breakdownAlwaysReconcilesToTheFinalPrice() {
    BigDecimal base = bd("1000");
    CascadingEngine.Result r =
        CascadingEngine.apply(base, List.of(percent("P25", "0.25", 1), percent("P5", "0.05", 2)));
    assertThat(base.subtract(r.totalRemoved())).isEqualByComparingTo(r.finalPrice());
  }

  /** Rounding happens per tier; a third of 100 must not leak fractions of a cent. */
  @Test
  void reconcilesUnderPerTierRounding() {
    BigDecimal base = bd("100.00");
    CascadingEngine.Result r =
        CascadingEngine.apply(
            base, List.of(percent("A", "0.3333", 1), percent("B", "0.1111", 2), percent("C", "0.07", 3)));

    assertThat(base.subtract(r.totalRemoved())).isEqualByComparingTo(r.finalPrice());
    assertThat(r.finalPrice().scale()).isEqualTo(2);
    r.breakdown().forEach(l -> assertThat(l.amountRemoved().scale()).isEqualTo(2));
  }

  /** FR-006: the result must not depend on the order tiers happen to be supplied in. */
  @Test
  void orderingIsDeterministicByPriorityNotInputOrder() {
    CascadingEngine.Result forward =
        CascadingEngine.apply(bd("1000"), List.of(percent("P25", "0.25", 1), percent("P5", "0.05", 2)));
    CascadingEngine.Result reversed =
        CascadingEngine.apply(bd("1000"), List.of(percent("P5", "0.05", 2), percent("P25", "0.25", 1)));

    assertThat(reversed.finalPrice()).isEqualByComparingTo(forward.finalPrice());
    assertThat(reversed.breakdown().stream().map(CascadingEngine.BreakdownLine::tierCode).toList())
        .containsExactly("P25", "P5");
  }

  /** SC-005: a discount larger than the base caps at zero and is flagged, never negative. */
  @Test
  void discountExceedingTheBaseIsCappedAtZeroAndFlagged() {
    CascadingEngine.Result r = CascadingEngine.apply(bd("100"), List.of(fixed("BIG", "250", 1)));

    assertThat(r.finalPrice()).isEqualByComparingTo("0.00");
    assertThat(r.flags()).containsExactly(CascadingEngine.FLAG_CAPPED);
    assertThat(r.breakdown().get(0).amountRemoved()).isEqualByComparingTo("100.00");
    assertThat(bd("100").subtract(r.totalRemoved())).isEqualByComparingTo(r.finalPrice());
  }

  @Test
  void tiersAfterTheBaseIsExhaustedContributeNothing() {
    CascadingEngine.Result r =
        CascadingEngine.apply(bd("100"), List.of(fixed("BIG", "250", 1), percent("P10", "0.10", 2)));

    assertThat(r.finalPrice()).isEqualByComparingTo("0.00");
    assertThat(r.breakdown()).hasSize(1); // the exhausted second tier adds no line
    assertThat(r.flags()).containsExactly(CascadingEngine.FLAG_CAPPED);
  }

  @Test
  void noTiersLeavesTheBaseUntouchedWithAnEmptyBreakdown() {
    CascadingEngine.Result r = CascadingEngine.apply(bd("1000"), List.of());

    assertThat(r.finalPrice()).isEqualByComparingTo("1000.00");
    assertThat(r.breakdown()).isEmpty();
    assertThat(r.flags()).isEmpty();
  }

  @Test
  void fixedTierRemovesItsFlatAmount() {
    CascadingEngine.Result r = CascadingEngine.apply(bd("1000"), List.of(fixed("F100", "100", 1)));
    assertThat(r.finalPrice()).isEqualByComparingTo("900.00");
    assertThat(r.breakdown().get(0).amountRemoved()).isEqualByComparingTo("100.00");
  }

  @Test
  void zeroValueTierIsOmittedFromTheBreakdown() {
    CascadingEngine.Result r =
        CascadingEngine.apply(bd("1000"), List.of(percent("ZERO", "0", 1), percent("P10", "0.10", 2)));
    assertThat(r.breakdown()).hasSize(1);
    assertThat(r.finalPrice()).isEqualByComparingTo("900.00");
  }
}
