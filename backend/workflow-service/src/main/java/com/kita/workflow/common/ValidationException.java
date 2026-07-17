package com.kita.workflow.common;

/**
 * A business "no": invalid input, unknown/inactive party, oversell, over-receipt, short components,
 * self-review, or a separated actor (→ HTTP 422, outcome REJECTED_INVALID). Never retried.
 */
public class ValidationException extends RuntimeException {
  public ValidationException(String message) {
    super(message);
  }
}
