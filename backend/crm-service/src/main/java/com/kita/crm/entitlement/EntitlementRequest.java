package com.kita.crm.entitlement;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Request to record a government-mandated entitlement plus its supporting ID reference. */
public record EntitlementRequest(
    @NotNull EntitlementKind kind,
    String supportingIdRef,
    @NotNull LocalDate validFrom,
    LocalDate validTo) {}
