package com.kita.crm.entitlement;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Entitlement view. The supporting ID reference is NEVER returned — only whether one is on file,
 * which is all a caller needs to know why a statutory discount did or did not apply (FR-014/003).
 */
public record EntitlementResponse(
    UUID id,
    UUID customerId,
    EntitlementKind kind,
    boolean supportingIdOnFile,
    LocalDate validFrom,
    LocalDate validTo) {

  public static EntitlementResponse from(Entitlement e) {
    return new EntitlementResponse(
        e.getId(),
        e.getCustomerId(),
        e.getKind(),
        e.hasSupportingId(),
        e.getValidFrom(),
        e.getValidTo());
  }
}
