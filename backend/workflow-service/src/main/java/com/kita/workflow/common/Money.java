package com.kita.workflow.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Exact-decimal money helpers. Amounts are rounded HALF_UP to the currency minor unit (2 decimals) so
 * that computed totals reconcile to the cent. Never use binary floating point for money (Constitution
 * III, FR-020).
 */
public final class Money {

  public static final int SCALE = 2;

  private Money() {}

  /** Round an amount to the money scale (half-up). */
  public static BigDecimal round(BigDecimal amount) {
    return amount == null ? null : amount.setScale(SCALE, RoundingMode.HALF_UP);
  }

  /** Zero at money scale. */
  public static BigDecimal zero() {
    return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
  }

  /** quantity × unitPrice, rounded to money scale. */
  public static BigDecimal extend(BigDecimal quantity, BigDecimal unitPrice) {
    return round(quantity.multiply(unitPrice));
  }

  /** Sum, returned at money scale. */
  public static BigDecimal sum(Iterable<BigDecimal> amounts) {
    BigDecimal total = BigDecimal.ZERO;
    for (BigDecimal a : amounts) {
      if (a != null) {
        total = total.add(a);
      }
    }
    return round(total);
  }
}
