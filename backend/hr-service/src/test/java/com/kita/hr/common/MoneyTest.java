package com.kita.hr.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit tests for money rounding/sum (no Spring, no Docker). */
class MoneyTest {

  @Test
  void roundsHalfUpToTwoDecimals() {
    assertThat(Money.round(new BigDecimal("712.505"))).isEqualByComparingTo("712.51");
    assertThat(Money.round(new BigDecimal("712.504"))).isEqualByComparingTo("712.50");
  }

  @Test
  void sumRoundsToCent() {
    assertThat(Money.sum(List.of(new BigDecimal("250.00"), new BigDecimal("37.50"))))
        .isEqualByComparingTo("287.50");
  }

  @Test
  void percentOfRoundsToCent() {
    assertThat(Money.percentOf(new BigDecimal("1000.00"), new BigDecimal("25")))
        .isEqualByComparingTo("250.00");
    assertThat(Money.percentOf(new BigDecimal("750.00"), new BigDecimal("5")))
        .isEqualByComparingTo("37.50");
  }
}
