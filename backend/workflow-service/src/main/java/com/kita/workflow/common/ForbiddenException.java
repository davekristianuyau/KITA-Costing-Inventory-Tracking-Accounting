package com.kita.workflow.common;

/**
 * The acting employee's HR roles do not grant the requested action/kind (→ HTTP 403, outcome
 * REJECTED_NOT_PERMITTED). No downstream call is made.
 */
public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) {
    super(message);
  }
}
