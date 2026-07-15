package com.kita.hr.attendance;

import com.kita.hr.common.EffectiveDated;
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

/** An effective-dated premium multiplier for overtime, rest-day, holiday, or night-differential pay. */
@Entity
@Table(name = "premium_rule")
public class PremiumRule implements EffectiveDated {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PremiumKind kind;

  @Column(nullable = false, precision = 6, scale = 4)
  private BigDecimal multiplier;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  protected PremiumRule() {}

  public PremiumRule(PremiumKind kind, BigDecimal multiplier, LocalDate effectiveDate) {
    this.kind = kind;
    this.multiplier = multiplier;
    this.effectiveDate = effectiveDate;
  }

  public PremiumKind getKind() {
    return kind;
  }

  public BigDecimal getMultiplier() {
    return multiplier;
  }

  @Override
  public LocalDate effectiveDate() {
    return effectiveDate;
  }
}
