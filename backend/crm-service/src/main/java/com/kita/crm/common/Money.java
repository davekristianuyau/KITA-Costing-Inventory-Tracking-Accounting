package com.kita.crm.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Exact-decimal money helpers. All monetary amounts are rounded HALF_UP to the currency minor unit
 * (2 decimals) per line so that payslip lines and register totals reconcile to the cent. Never use
 * binary floating point for money (Constitution III).
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

  /** Percentage of a base (e.g., 25 → 25%), rounded to money scale. */
  public static BigDecimal percentOf(BigDecimal base, BigDecimal percent) {
    return round(base.multiply(percent).movePointLeft(2));
  }
}
