package com.kita.hr.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.hr.common.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 017 FR-017: {@code OWNER} is the highest-position administrator and implies every role this service
 * knows. This is the feature's largest privilege-escalation surface, so it is asserted directly — both
 * that OWNER grants everything, and that it is the ONLY token which does.
 */
class OwnerRoleTest {

  private void callerWithRoles(String rolesHeader) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (rolesHeader != null) {
      request.addHeader("X-Kita-Roles", rolesHeader);
    }
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @AfterEach
  void clear() {
    RequestContextHolder.resetRequestAttributes();
  }

  /** stub=false so the permissive fallback cannot mask what is being asserted. */
  private CallerContext caller() {
    return new CallerContext(false);
  }

  @Test
  void ownerImpliesEveryRoleThisServiceKnows() {
    callerWithRoles("OWNER");
    assertThat(caller().roles()).containsAll(java.util.EnumSet.allOf(Role.class));
  }

  @Test
  void ownerSatisfiesEveryRequirement() {
    callerWithRoles("OWNER");
    assertThatCode(() -> caller().require(Role.HR_ADMIN)).doesNotThrowAnyException();
    assertThatCode(() -> caller().require(Role.PAYROLL_OFFICER)).doesNotThrowAnyException();
  }

  @Test
  void anOrdinaryRoleStillGrantsOnlyItself() {
    callerWithRoles("PAYROLL_OFFICER");
    assertThat(caller().roles()).containsExactly(Role.PAYROLL_OFFICER);
    assertThatThrownBy(() -> caller().require(Role.HR_ADMIN)).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void anUnrecognizedTokenGrantsNothingAndDoesNotError() {
    callerWithRoles("SOME_FUTURE_ROLE");
    assertThat(caller().roles()).isEmpty();
  }
}
