package com.kita.workflow.authorization;

/**
 * How a role grant applies to an action: a normal {@code PERFORM} grant, or — for review-gated
 * actions — the {@code MAKER} who creates the record vs. the distinct {@code CHECKER} who confirms it
 * (FR-021).
 */
public enum AuthorizationKind {
  PERFORM,
  MAKER,
  CHECKER
}
