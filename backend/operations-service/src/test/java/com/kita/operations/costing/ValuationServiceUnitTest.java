package com.kita.operations.costing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** T045: AVCO weighted-average recompute (pure, no Docker). */
class ValuationServiceUnitTest {

  @Test
  void weightedAverageAcrossReceipts() {
    // first receipt: 10 @ 5 into empty stock -> avg 5
    BigDecimal avg1 =
        ValuationService.averageCost(BigDecimal.ZERO, null, new BigDecimal("10"), new BigDecimal("5"));
    assertThat(avg1).isEqualByComparingTo("5");

    // second receipt: 10 @ 7 on top of 10 @ 5 -> (50 + 70) / 20 = 6
    BigDecimal avg2 =
        ValuationService.averageCost(
            new BigDecimal("10"), avg1, new BigDecimal("10"), new BigDecimal("7"));
    assertThat(avg2).isEqualByComparingTo("6");
  }
}
