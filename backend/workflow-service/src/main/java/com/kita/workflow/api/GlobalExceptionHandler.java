package com.kita.workflow.api;

import com.kita.workflow.common.DownstreamUnavailableException;
import com.kita.workflow.common.ErrorResponse;
import com.kita.workflow.common.ForbiddenException;
import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.common.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the back-office error taxonomy to HTTP + the {@link ErrorResponse} envelope (FR-018): 403
 * not-permitted, 422 invalid, 503 temporarily-unavailable (retryable).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> forbidden(ForbiddenException ex) {
    return body(HttpStatus.FORBIDDEN, "REJECTED_NOT_PERMITTED", ex.getMessage());
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ErrorResponse> invalid(ValidationException ex) {
    return body(HttpStatus.UNPROCESSABLE_ENTITY, "REJECTED_INVALID", ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> beanValidation(MethodArgumentNotValidException ex) {
    String reason =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .orElse("request validation failed");
    return body(HttpStatus.UNPROCESSABLE_ENTITY, "REJECTED_INVALID", reason);
  }

  @ExceptionHandler({DownstreamUnavailableException.class, TransientDownstreamException.class})
  public ResponseEntity<ErrorResponse> unavailable(RuntimeException ex) {
    return body(HttpStatus.SERVICE_UNAVAILABLE, "FAILED_UNAVAILABLE", ex.getMessage());
  }

  private ResponseEntity<ErrorResponse> body(HttpStatus status, String outcome, String reason) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(outcome, reason, status.name()));
  }
}
