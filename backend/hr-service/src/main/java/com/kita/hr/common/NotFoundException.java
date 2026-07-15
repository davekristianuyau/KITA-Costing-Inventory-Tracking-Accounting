package com.kita.hr.common;

/** Thrown when a requested resource does not exist (→ HTTP 404). */
public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }
}
