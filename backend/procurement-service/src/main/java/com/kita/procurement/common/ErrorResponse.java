package com.kita.procurement.common;

import java.util.List;

/** RFC-7807-style error body returned at the API boundary. */
public record ErrorResponse(String title, int status, String detail, List<String> errors) {

  public static ErrorResponse of(String title, int status, String detail) {
    return new ErrorResponse(title, status, detail, List.of());
  }
}
