package com.kita.procurement.restock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** T033: restock sizing — reach the target, rounded up to the supplier's order multiple. */
class RestockCalculatorTest {

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }

  @Test
  void sizesToTheShortfallWhenThereIsNoMinimum() {
    assertThat(RestockCalculator.suggestedQty(bd("2"), bd("10"), null)).isEqualByComparingTo("8");
    assertThat(RestockCalculator.suggestedQty(bd("2"), bd("10"), BigDecimal.ZERO))
        .isEqualByComparingTo("8");
  }

  /** A shortfall of 8 from a supplier selling in cases of 12 means one case, not 8 loose units. */
  @Test
  void roundsUpToTheOrderMultiple() {
    assertThat(RestockCalculator.suggestedQty(bd("2"), bd("10"), bd("12"))).isEqualByComparingTo("12");
  }

  /** A shortfall of 13 needs two cases of 12, not one. */
  @Test
  void roundsUpToAWholeNumberOfMultiples() {
    assertThat(RestockCalculator.suggestedQty(bd("0"), bd("13"), bd("12"))).isEqualByComparingTo("24");
  }

  @Test
  void anExactMultipleIsNotRoundedUpFurther() {
    assertThat(RestockCalculator.suggestedQty(bd("0"), bd("24"), bd("12"))).isEqualByComparingTo("24");
  }

  @Test
  void suggestsNothingWhenStockIsAtOrAboveTarget() {
    assertThat(RestockCalculator.suggestedQty(bd("10"), bd("10"), bd("5"))).isEqualByComparingTo("0");
    assertThat(RestockCalculator.suggestedQty(bd("15"), bd("10"), bd("5"))).isEqualByComparingTo("0");
  }

  @Test
  void needsRestockAtOrBelowTheReorderPoint() {
    assertThat(RestockCalculator.needsRestock(bd("5"), bd("5"))).isTrue(); // at the point
    assertThat(RestockCalculator.needsRestock(bd("4"), bd("5"))).isTrue();
    assertThat(RestockCalculator.needsRestock(bd("6"), bd("5"))).isFalse();
  }

  @Test
  void handlesFractionalQuantities() {
    assertThat(RestockCalculator.suggestedQty(bd("0.5"), bd("10"), bd("2.5")))
        .isEqualByComparingTo("10.0"); // shortfall 9.5 → 4 × 2.5
  }
}
