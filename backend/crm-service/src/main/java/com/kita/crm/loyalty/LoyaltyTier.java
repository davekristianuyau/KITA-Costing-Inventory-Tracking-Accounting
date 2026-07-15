package com.kita.crm.loyalty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A repeat-customer tier: qualifying criteria plus the discount rule it contributes to the cascade.
 * A null criterion is simply not applied, so a tier can qualify on count, on value, or on both.
 */
@Entity
@Table(name = "loyalty_tier")
public class LoyaltyTier {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String name;

  @Column(name = "min_purchase_count")
  private Integer minPurchaseCount;

  @Column(name = "min_purchase_value", precision = 19, scale = 2)
  private BigDecimal minPurchaseValue;

  @Column(name = "period_days")
  private Integer periodDays;

  @Column(name = "discount_rule_id", nullable = false)
  private UUID discountRuleId;

  protected LoyaltyTier() {}

  public LoyaltyTier(
      String code,
      String name,
      Integer minPurchaseCount,
      BigDecimal minPurchaseValue,
      Integer periodDays,
      UUID discountRuleId) {
    this.code = code;
    this.name = name;
    this.minPurchaseCount = minPurchaseCount;
    this.minPurchaseValue = minPurchaseValue;
    this.periodDays = periodDays;
    this.discountRuleId = discountRuleId;
  }

  /** True when the supplied activity meets every criterion this tier actually specifies. */
  public boolean qualifies(int purchaseCount, BigDecimal purchaseValue) {
    if (minPurchaseCount != null && purchaseCount < minPurchaseCount) {
      return false;
    }
    return minPurchaseValue == null || purchaseValue.compareTo(minPurchaseValue) >= 0;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public Integer getMinPurchaseCount() {
    return minPurchaseCount;
  }

  public BigDecimal getMinPurchaseValue() {
    return minPurchaseValue;
  }

  public Integer getPeriodDays() {
    return periodDays;
  }

  public UUID getDiscountRuleId() {
    return discountRuleId;
  }
}
