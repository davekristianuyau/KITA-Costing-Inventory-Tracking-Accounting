package com.kita.crm.discount;

import com.kita.crm.common.EffectiveDated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    this.code = code;
    this.origin = origin;
    this.computation = computation;
    this.value = value;
    this.vatTreatment = vatTreatment == null ? VatTreatment.NONE : vatTreatment;
    this.priority = priority;
    this.effectiveDate = effectiveDate;
  }

  /** This rule as a cascade tier. */
  public CascadingEngine.Tier toTier() {
    return new CascadingEngine.Tier(code, origin, computation, value, priority);
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

  public int getPriority() {
    return priority;
  }

  @Override
  public LocalDate effectiveDate() {
    return effectiveDate;
  }
}
