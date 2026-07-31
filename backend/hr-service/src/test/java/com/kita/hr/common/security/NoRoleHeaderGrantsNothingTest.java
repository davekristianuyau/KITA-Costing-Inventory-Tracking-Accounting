package com.kita.hr.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.hr.common.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 017 FR-018 / SC-008 — with the permissive development fallback retired, a caller presenting no roles
 * gets <b>nothing</b>.
 *
 * <p>Before 017 an absent {@code X-Kita-Roles} header meant "grant every role", which is a reasonable
 * convenience while there is no login and a serious hole once there is one. The fallback survives only
 * as an explicitly-enabled seam for isolated {@code :service:test} runs, never in a stack that
 * authorizes.
 */
class NoRoleHeaderGrantsNothingTest {

  @AfterEach
  void clear() {
    RequestContextHolder.resetRequestAttributes();
  }

  private void callerWithNoRoleHeader() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
  }

  @Test
  void withTheFallbackOffAnAbsentRoleHeaderGrantsNothing() {
    callerWithNoRoleHeader();
    CallerContext caller = new CallerContext(false);

    assertThat(caller.roles()).isEmpty();
    assertThatThrownBy(() -> caller.require(Role.HR_ADMIN)).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void theFallbackStillWorksWhenExplicitlyEnabledForIsolatedTests() {
    callerWithNoRoleHeader();

    // Kept deliberately: :service:test must be runnable without a gateway in front of it.
    assertThat(new CallerContext(true).roles()).isNotEmpty();
  }
}
