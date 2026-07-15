package com.kita.crm.common;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain and validation exceptions to consistent HTTP responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(NotFoundException ex) {
    return status(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorResponse> conflict(ConflictException ex) {
    return status(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> forbidden(ForbiddenException ex) {
    return status(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException ex) {
    return status(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .toList();
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("Validation Failed", 400, "Request validation failed", errors));
  }

  private ResponseEntity<ErrorResponse> status(HttpStatus s, String title, String detail) {
    return ResponseEntity.status(s).body(ErrorResponse.of(title, s.value(), detail));
  }
}
