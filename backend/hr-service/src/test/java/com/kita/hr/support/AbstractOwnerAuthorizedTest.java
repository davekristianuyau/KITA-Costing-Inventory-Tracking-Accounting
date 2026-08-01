package com.kita.hr.support;

import org.springframework.http.HttpHeaders;

/**
 * Base for tests of the 017 administration endpoints, which are {@code OWNER}-gated (FR-010/FR-017).
 *
 * <p>Both helpers send an explicit {@code X-Kita-Roles} header. That matters: hr's {@code CallerContext}
 * falls back to "all roles" only when the header is <em>absent</em> (the dev stub), so a test that sent
 * nothing would pass regardless of the gate and prove nothing. {@link #nonOwner()} is deliberately a
 * privileged-but-not-OWNER caller, so the assertion is "OWNER is required", not merely "some role is".
 */
public abstract class AbstractOwnerAuthorizedTest extends AbstractHrIT {

  protected HttpHeaders owner() {
    return rolesHeader("OWNER", "owner-admin");
  }

  /** HR_ADMIN: privileged for ordinary hr work, but must NOT be able to administer identity. */
  protected HttpHeaders nonOwner() {
    return rolesHeader("HR_ADMIN", "hr-admin");
  }

  private HttpHeaders rolesHeader(String roles, String user) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Kita-Roles", roles);
    headers.set("X-Kita-User", user);
    return headers;
  }
}
