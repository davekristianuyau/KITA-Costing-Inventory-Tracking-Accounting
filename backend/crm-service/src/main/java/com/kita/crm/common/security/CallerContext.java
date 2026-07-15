package com.kita.crm.common.security;

import com.kita.crm.common.ForbiddenException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the caller's roles from the gateway-set {@code X-Kita-Roles} header. In stub mode
 * (dev/test) a caller with no role header is treated as CRM_ADMIN so the service is usable before the
 * gateway is wired up. The gateway is the real authenticator; this only interprets what it forwards.
 */
@Component
public class CallerContext {

  private final boolean stub;

  public CallerContext(@Value("${crm.security.stub:true}") boolean stub) {
    this.stub = stub;
  }

  public Set<Role> roles() {
    String header = header("X-Kita-Roles");
    if (header == null || header.isBlank()) {
      return stub ? EnumSet.allOf(Role.class) : EnumSet.noneOf(Role.class);
    }
    EnumSet<Role> roles = EnumSet.noneOf(Role.class);
    for (String r : header.split(",")) {
      try {
        roles.add(Role.valueOf(r.trim().toUpperCase()));
      } catch (IllegalArgumentException ignored) {
        // unknown role token: ignore
      }
    }
    return roles;
  }

  public boolean hasAnyRole(Role... allowed) {
    Set<Role> mine = roles();
    return Arrays.stream(allowed).anyMatch(mine::contains);
  }

  /** Require at least one of the given roles, else 403. */
  public void require(Role... allowed) {
    if (!hasAnyRole(allowed)) {
      throw new ForbiddenException("requires one of: " + Arrays.toString(allowed));
    }
  }

  public String actor() {
    String header = header("X-Kita-User");
    return header == null || header.isBlank() ? "system" : header.trim();
  }

  private String header(String name) {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes sra) {
      return sra.getRequest().getHeader(name);
    }
    return null;
  }
}
