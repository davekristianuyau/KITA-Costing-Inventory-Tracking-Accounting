package com.kita.crm.discount;

import com.kita.crm.common.EffectiveDated;
import com.kita.crm.entitlement.EntitlementKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A versioned, effective-dated discount rule — the engine's unit of configuration. PERCENT values
 * are fractions (0.25 = 25%); FIXED values are amounts.
 */
@Entity
@Table(name = "discount_rule")
public class DiscountRule implements EffectiveDated {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(nullable = false)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DiscountOrigin origin;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DiscountComputationKind computation;

  @Column(nullable = false, precision = 12, scale = 6)
  private BigDecimal value;

  @Enumerated(EnumType.STRING)
  @Column(name = "vat_treatment", nullable = false)
  private VatTreatment vatTreatment = VatTreatment.NONE;

  @Column(name = "vat_rate", precision = 6, scale = 4)
  private BigDecimal vatRate;

  /** Which entitlement this statutory rule belongs to; null for non-statutory rules. */
  @Enumerated(EnumType.STRING)
  @Column(name = "entitlement_kind")
  private EntitlementKind entitlementKind;

  @Column(nullable = false)
  private int priority = 100;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  protected DiscountRule() {}

  public DiscountRule(
      String code,
      DiscountOrigin origin,
      DiscountComputationKind computation,
      BigDecimal value,
      VatTreatment vatTreatment,
      int priority,
      LocalDate effectiveDate) {
    this(code, origin, computation, value, vatTreatment, null, null, priority, effectiveDate);
  }

  public DiscountRule(
      String code,
      DiscountOrigin origin,
      DiscountComputationKind computation,
      BigDecimal value,
      VatTreatment vatTreatment,
      BigDecimal vatRate,
      EntitlementKind entitlementKind,
      int priority,
      LocalDate effectiveDate) {
    this.code = code;
    this.origin = origin;
    this.computation = computation;
    this.value = value;
    this.vatTreatment = vatTreatment == null ? VatTreatment.NONE : vatTreatment;
    this.vatRate = vatRate;
    this.entitlementKind = entitlementKind;
    this.priority = priority;
    this.effectiveDate = effectiveDate;
  }

  /** This rule as a cascade tier. */
  public CascadingEngine.Tier toTier() {
    return new CascadingEngine.Tier(code, origin, computation, value, priority);
  }

  /**
   * The VAT-exemption step this rule implies, if any (e.g. PH senior/PWD strip VAT before their 20%).
   *
   * <p>Modelled as its own PERCENT tier so it reconciles like any other line and composes correctly
   * whatever ran before it: removing VAT from a running amount {@code X} is {@code X × r/(1+r)}, so
   * the fraction works on the amount actually in hand rather than on the original base.
   */
  public Optional<CascadingEngine.Tier> vatExemptionTier() {
    if (vatTreatment != VatTreatment.VAT_EXEMPT || vatRate == null || vatRate.signum() <= 0) {
      return Optional.empty();
    }
    BigDecimal fraction = vatRate.divide(BigDecimal.ONE.add(vatRate), 12, RoundingMode.HALF_UP);
    return Optional.of(
        new CascadingEngine.Tier(
            code + "_VAT_EXEMPT",
            origin,
            DiscountComputationKind.PERCENT,
            fraction,
            priority - 1)); // strips VAT immediately before the statutory percentage
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public DiscountOrigin getOrigin() {
    return origin;
  }

  public DiscountComputationKind getComputation() {
    return computation;
  }

  public BigDecimal getValue() {
    return value;
  }

  public VatTreatment getVatTreatment() {
    return vatTreatment;
  }

  public BigDecimal getVatRate() {
    return vatRate;
  }

  public EntitlementKind getEntitlementKind() {
    return entitlementKind;
  }

  public int getPriority() {
    return priority;
  }

  @Override
  public LocalDate effectiveDate() {
    return effectiveDate;
  }
}
