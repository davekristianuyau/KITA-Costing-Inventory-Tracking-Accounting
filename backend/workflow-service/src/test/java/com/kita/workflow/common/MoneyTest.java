package com.kita.workflow.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit tests for money rounding/extend/sum (no Spring, no Docker) — FR-020, Constitution II. */
class MoneyTest {

  @Test
  void roundsHalfUpToTwoDecimals() {
    assertThat(Money.round(new BigDecimal("125.005"))).isEqualByComparingTo("125.01");
    assertThat(Money.round(new BigDecimal("125.004"))).isEqualByComparingTo("125.00");
  }

  @Test
  void extendMultipliesThenRounds() {
    // 3 × 12.345 = 37.035 → 37.04 (half-up rounding edge)
    assertThat(Money.extend(new BigDecimal("3"), new BigDecimal("12.345")))
        .isEqualByComparingTo("37.04");
  }

  @Test
  void sumOfLineExtensionsReconcilesToCent() {
    BigDecimal total =
        Money.sum(
            List.of(
                Money.extend(new BigDecimal("10"), new BigDecimal("125.00")),
                Money.extend(new BigDecimal("3"), new BigDecimal("12.345"))));
    // 1250.00 + 37.04 = 1287.04
    assertThat(total).isEqualByComparingTo("1287.04");
  }

  @Test
  void zeroIsAtScale() {
    assertThat(Money.zero()).isEqualByComparingTo("0.00");
  }
}
