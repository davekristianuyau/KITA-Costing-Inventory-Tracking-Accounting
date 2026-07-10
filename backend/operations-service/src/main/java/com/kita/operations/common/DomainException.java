package com.kita.operations.common;

import org.springframework.http.HttpStatus;

/** Base for domain errors that map to an RFC-9457 problem response. */
public class DomainException extends RuntimeException {
  private final HttpStatus status;

  public DomainException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }

  /** 404 — a referenced entity does not exist. */
  public static class NotFound extends DomainException {
    public NotFound(String message) {
      super(HttpStatus.NOT_FOUND, message);
    }
  }

  /** 400 — invalid input or business-rule violation on the request. */
  public static class Validation extends DomainException {
    public Validation(String message) {
      super(HttpStatus.BAD_REQUEST, message);
    }
  }

  /** 409 — conflict such as insufficient stock or a concurrency/consistency issue. */
  public static class Conflict extends DomainException {
    public Conflict(String message) {
      super(HttpStatus.CONFLICT, message);
    }
  }
}
