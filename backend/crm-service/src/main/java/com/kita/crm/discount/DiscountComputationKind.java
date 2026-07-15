package com.kita.crm.discount;

/** How a tier's amount is derived: a percentage of the running base, or a flat amount. */
public enum DiscountComputationKind {
  PERCENT,
  FIXED
}
