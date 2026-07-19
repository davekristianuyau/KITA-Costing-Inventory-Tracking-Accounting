package com.kita.session;

/** Raised when a session token cannot be decrypted, its signature fails to verify, or it has expired. */
public class InvalidSessionException extends RuntimeException {
  public InvalidSessionException(String message) {
    super(message);
  }
}
