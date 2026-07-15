package com.kita.hr.common;

/** Thrown on a state/uniqueness conflict, e.g. duplicate key or illegal transition (→ HTTP 409). */
public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
