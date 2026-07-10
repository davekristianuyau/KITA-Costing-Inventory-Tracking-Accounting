package com.kita.operations.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain and validation errors to RFC-9457 ProblemDetail responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(DomainException.class)
  public ProblemDetail handleDomain(DomainException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    pd.setTitle(ex.getStatus().getReasonPhrase());
    return pd;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setTitle("Bad Request");
    return pd;
  }
}
