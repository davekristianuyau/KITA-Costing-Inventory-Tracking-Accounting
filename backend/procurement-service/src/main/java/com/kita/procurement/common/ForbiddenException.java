package com.kita.procurement.common;

/** Thrown when the caller lacks the required role/scope (→ HTTP 403). */
public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) {
    super(message);
  }
}
