package com.kita.procurement.restock;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure restock sizing (no Spring/DB), so the rule is exhaustively unit-testable.
 *
 * <p>{@code suggested = max(target − onHand, 0)}, rounded <b>up</b> to a multiple of the supplier's
 * minimum order quantity. Rounding up (not merely raising to the minimum) means a supplier who only
 * sells in cases of 12 gets a whole number of cases.
 */
public final class RestockCalculator {

  private RestockCalculator() {}

  /**
   * @param minOrderQty the supplier's order multiple; null or non-positive means no constraint
   * @return the quantity to order, or zero when nothing is needed
   */
  public static BigDecimal suggestedQty(
      BigDecimal onHand, BigDecimal targetLevel, BigDecimal minOrderQty) {
    BigDecimal shortfall = targetLevel.subtract(onHand);
    if (shortfall.signum() <= 0) {
      return BigDecimal.ZERO;
    }
    if (minOrderQty == null || minOrderQty.signum() <= 0) {
      return shortfall;
    }
    // Round the shortfall up to the next whole multiple of the order quantity.
    BigDecimal multiples = shortfall.divide(minOrderQty, 0, RoundingMode.CEILING);
    return multiples.multiply(minOrderQty);
  }

  /** True when the item is at or below its reorder point and therefore needs replenishing. */
  public static boolean needsRestock(BigDecimal onHand, BigDecimal reorderPoint) {
    return onHand.compareTo(reorderPoint) <= 0;
  }
}
