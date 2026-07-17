package com.kita.workflow.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the acting employee's identity from the gateway-set {@code X-Kita-User} header. Unlike the
 * other services, roles are NOT read here — they are resolved from the HR record (FR-002). In stub
 * mode a caller with no header resolves to {@link #STUB_ACTOR}, which the fake HR adapter knows as an
 * all-roles employee so the service is usable before the gateway is wired up.
 */
@Component
public class CallerContext {

  /** The employee id assumed for header-less callers in stub mode. */
  public static final String STUB_ACTOR = "stub-admin";

  private final boolean stub;

  public CallerContext(@Value("${workflow.security.stub:true}") boolean stub) {
    this.stub = stub;
  }

  /** The acting employee id (gateway {@code X-Kita-User}); {@link #STUB_ACTOR} in stub mode. */
  public String actor() {
    String header = header("X-Kita-User");
    if (header == null || header.isBlank()) {
      return stub ? STUB_ACTOR : null;
    }
    return header.trim();
  }

  private String header(String name) {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes sra) {
      return sra.getRequest().getHeader(name);
    }
    return null;
  }
}
