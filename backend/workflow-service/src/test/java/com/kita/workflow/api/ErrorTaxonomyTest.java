package com.kita.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.workflow.common.DownstreamUnavailableException;
import com.kita.workflow.common.ErrorResponse;
import com.kita.workflow.common.ForbiddenException;
import com.kita.workflow.common.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Verifies the FR-018 error taxonomy: 403/422/503 with the right outcome envelope (contract). */
class ErrorTaxonomyTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void forbiddenMapsTo403NotPermitted() {
    ResponseEntity<ErrorResponse> r = handler.forbidden(new ForbiddenException("nope"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(r.getBody().outcome()).isEqualTo("REJECTED_NOT_PERMITTED");
  }

  @Test
  void validationMapsTo422Invalid() {
    ResponseEntity<ErrorResponse> r = handler.invalid(new ValidationException("bad"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(r.getBody().outcome()).isEqualTo("REJECTED_INVALID");
  }

  @Test
  void downstreamUnavailableMapsTo503Unavailable() {
    ResponseEntity<ErrorResponse> r =
        handler.unavailable(new DownstreamUnavailableException("down"));
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(r.getBody().outcome()).isEqualTo("FAILED_UNAVAILABLE");
  }
}
