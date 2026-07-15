package com.kita.crm.entitlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A customer's government-mandated discount eligibility. The statutory discount applies only when
 * the entitlement is valid for the sale date AND carries its supporting ID reference (FR-014); the
 * reference itself is stored but never logged or returned in the clear.
 */
@Entity
@Table(name = "entitlement")
public class Entitlement {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EntitlementKind kind;

  @Column(name = "supporting_id_ref")
  private String supportingIdRef;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_to")
  private LocalDate validTo;

  protected Entitlement() {}

  public Entitlement(
      UUID customerId,
      EntitlementKind kind,
      String supportingIdRef,
      LocalDate validFrom,
      LocalDate validTo) {
    this.customerId = customerId;
    this.kind = kind;
    this.supportingIdRef = supportingIdRef;
    this.validFrom = validFrom;
    this.validTo = validTo;
  }

  /** True when this entitlement covers {@code date} and can actually be honored. */
  public boolean isValidOn(LocalDate date) {
    if (date.isBefore(validFrom)) {
      return false;
    }
    return validTo == null || !date.isAfter(validTo);
  }

  /** A statutory discount is withheld without a supporting ID on file (FR-014). */
  public boolean hasSupportingId() {
    return supportingIdRef != null && !supportingIdRef.isBlank();
  }

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public EntitlementKind getKind() {
    return kind;
  }

  public String getSupportingIdRef() {
    return supportingIdRef;
  }

  public LocalDate getValidFrom() {
    return validFrom;
  }

  public LocalDate getValidTo() {
    return validTo;
  }
}
