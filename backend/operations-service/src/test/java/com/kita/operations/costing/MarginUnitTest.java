package com.kita.operations.costing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** T056: margin math (pure). */
class MarginUnitTest {

  @Test
  void profitAndPercent() {
    assertThat(CostingService.profit(new BigDecimal("100"), new BigDecimal("60")))
        .isEqualByComparingTo("40");
    assertThat(CostingService.profitPercent(new BigDecimal("100"), new BigDecimal("60")))
        .isEqualByComparingTo("0.4");
  }
}
