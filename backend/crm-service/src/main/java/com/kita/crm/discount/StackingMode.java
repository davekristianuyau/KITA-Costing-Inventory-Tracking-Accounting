package com.kita.crm.discount;

/** How statutory and promotional/loyalty tiers combine (FR-013). Default {@code MOST_FAVORABLE}. */
public enum StackingMode {
  /** Take whichever path yields the lower final price. Never silently applies both. */
  MOST_FAVORABLE,
  /** One continuous cascade: statutory tiers first, then promotional/loyalty. */
  STATUTORY_THEN_PROMO,
  /** One continuous cascade: promotional/loyalty first, then statutory. */
  PROMO_THEN_STATUTORY,
  /** Ignore promotional/loyalty tiers entirely. */
  STATUTORY_ONLY
}
