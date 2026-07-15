package com.kita.crm.loyalty.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Qualifying activity for a tier evaluation. Purchase history is owned by operations-service, so the
 * caller supplies the measured activity rather than crm-service querying orders.
 */
public record EvaluateLoyaltyRequest(
    @NotNull @PositiveOrZero Integer purchaseCount,
    @NotNull @PositiveOrZero BigDecimal purchaseValue) {}
