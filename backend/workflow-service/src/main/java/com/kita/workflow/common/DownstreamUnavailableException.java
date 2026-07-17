package com.kita.workflow.common;

/**
 * A downstream service was unreachable or returned 5xx even after the bounded retries (→ HTTP 503,
 * outcome FAILED_UNAVAILABLE). Distinct from a business "no": the action is retryable and nothing was
 * left half-applied (FR-018, SC-010).
 */
public class DownstreamUnavailableException extends RuntimeException {
  public DownstreamUnavailableException(String message) {
    super(message);
  }

  public DownstreamUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
