package com.kita.workflow.common;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bounded, idempotent auto-retry for downstream calls (FR-018, SC-010).
 *
 * <p>On a {@link TransientDownstreamException} (timeout / connect error / HTTP 5xx) the call is
 * retried up to {@code workflow.retry.max-attempts} with a short backoff. Because the adapter carries
 * a stable {@code X-Idempotency-Key}, a replay is de-duplicated downstream (a repeated key / 409 is
 * treated as already-applied), so retries never cause a duplicate side effect. When attempts are
 * exhausted a {@link DownstreamUnavailableException} is thrown. Business rejections
 * ({@link ValidationException}, {@link ForbiddenException}) are not retried — they propagate at once.
 */
@Component
public class RetryingCaller {

  private static final Logger log = LoggerFactory.getLogger(RetryingCaller.class);

  private final int maxAttempts;
  private final long backoffMillis;

  public RetryingCaller(
      @Value("${workflow.retry.max-attempts:3}") int maxAttempts,
      @Value("${workflow.retry.backoff-millis:100}") long backoffMillis) {
    this.maxAttempts = Math.max(1, maxAttempts);
    this.backoffMillis = Math.max(0, backoffMillis);
  }

  /**
   * Run {@code downstreamCall}, retrying transient failures. The caller closes over its stable
   * idempotency key so every attempt uses the same key.
   *
   * @return how many attempts were made is not returned; retries are transparent to the caller
   */
  public <T> T call(String idempotencyKey, Supplier<T> downstreamCall) {
    TransientDownstreamException last = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return downstreamCall.get();
      } catch (TransientDownstreamException e) {
        last = e;
        log.warn(
            "transient downstream failure (attempt {}/{}, key={}): {}",
            attempt,
            maxAttempts,
            idempotencyKey,
            e.getMessage());
        if (attempt < maxAttempts) {
          sleep(attempt);
        }
      }
    }
    throw new DownstreamUnavailableException(
        "downstream unavailable after " + maxAttempts + " attempts", last);
  }

  private void sleep(int attempt) {
    if (backoffMillis == 0) {
      return;
    }
    try {
      Thread.sleep(backoffMillis * attempt);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new DownstreamUnavailableException("retry interrupted", ie);
    }
  }
}
