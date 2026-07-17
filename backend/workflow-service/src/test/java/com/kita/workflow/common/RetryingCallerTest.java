package com.kita.workflow.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Pure unit tests for bounded idempotent retry (FR-018, SC-010) — no Spring, no Docker. */
class RetryingCallerTest {

  private final RetryingCaller caller = new RetryingCaller(3, 0);

  @Test
  void retriesTransientThenSucceeds() {
    AtomicInteger attempts = new AtomicInteger();
    String result =
        caller.call(
            "key-1",
            () -> {
              if (attempts.incrementAndGet() < 3) {
                throw new TransientDownstreamException("timeout");
              }
              return "ok";
            });
    assertThat(result).isEqualTo("ok");
    assertThat(attempts).hasValue(3);
  }

  @Test
  void exhaustsThenReportsUnavailable() {
    AtomicInteger attempts = new AtomicInteger();
    assertThatThrownBy(
            () ->
                caller.call(
                    "key-2",
                    () -> {
                      attempts.incrementAndGet();
                      throw new TransientDownstreamException("still down");
                    }))
        .isInstanceOf(DownstreamUnavailableException.class);
    assertThat(attempts).hasValue(3); // bounded at max-attempts
  }

  @Test
  void doesNotRetryBusinessRejection() {
    AtomicInteger attempts = new AtomicInteger();
    assertThatThrownBy(
            () ->
                caller.call(
                    "key-3",
                    () -> {
                      attempts.incrementAndGet();
                      throw new ValidationException("over-receipt");
                    }))
        .isInstanceOf(ValidationException.class);
    assertThat(attempts).hasValue(1); // 4xx business "no" is not retried
  }

  @Test
  void appliesEffectExactlyOnceOnSuccess() {
    AtomicInteger effects = new AtomicInteger();
    caller.call(
        "key-4",
        () -> {
          effects.incrementAndGet();
          return "done";
        });
    assertThat(effects).hasValue(1);
  }
}
