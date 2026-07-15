package com.kita.procurement.common.security;

/**
 * Coarse roles for procurement actions (FR-016). Real authentication is performed by the gateway.
 *
 * <p>{@code PROCUREMENT_ADMIN} manages suppliers and raises POs; {@code APPROVER} is the authorized
 * approver an over-threshold order requires (FR-006); {@code RECEIVER} records goods receipts.
 */
public enum Role {
  PROCUREMENT_ADMIN,
  APPROVER,
  RECEIVER
}
