package com.kita.workflow.common;

/**
 * A transient downstream failure (timeout, connect error, or HTTP 5xx) that {@link RetryingCaller}
 * may retry. Http adapters throw this; business rejections use {@link ValidationException} instead and
 * are never retried.
 */
public class TransientDownstreamException extends RuntimeException {
  public TransientDownstreamException(String message) {
    super(message);
  }

  public TransientDownstreamException(String message, Throwable cause) {
    super(message, cause);
  }
}
