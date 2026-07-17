package com.kita.workflow.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.workflow.common.ForbiddenException;
import com.kita.workflow.common.security.Role;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pure unit tests for role→action authorization incl. maker vs checker kind (FR-002, FR-021). */
class ActionAuthorizerTest {

  private final ActionAuthorizer authorizer =
      new ActionAuthorizer(
          List.of(
              new AuthorizationRule(
                  BackOfficeAction.TAKE_SALES_ORDER, Role.SALES, AuthorizationKind.MAKER),
              new AuthorizationRule(
                  BackOfficeAction.CONFIRM_SALES_PAYMENT,
                  Role.CASHIER,
                  AuthorizationKind.CHECKER)));

  @Test
  void permitsWhenRoleAndKindMatch() {
    assertThat(
            authorizer.permits(
                Set.of(Role.SALES), BackOfficeAction.TAKE_SALES_ORDER, AuthorizationKind.MAKER))
        .isTrue();
  }

  @Test
  void deniesWhenRoleMatchesButKindDiffers() {
    // SALES is a MAKER for TAKE_SALES_ORDER, not a CHECKER
    assertThat(
            authorizer.permits(
                Set.of(Role.SALES), BackOfficeAction.TAKE_SALES_ORDER, AuthorizationKind.CHECKER))
        .isFalse();
  }

  @Test
  void deniesWhenRoleAbsent() {
    assertThat(
            authorizer.permits(
                Set.of(Role.WAREHOUSE_STAFF),
                BackOfficeAction.CONFIRM_SALES_PAYMENT,
                AuthorizationKind.CHECKER))
        .isFalse();
  }

  @Test
  void authorizeThrowsForbiddenWhenNotPermitted() {
    assertThatThrownBy(
            () ->
                authorizer.authorize(
                    Set.of(Role.SALES),
                    BackOfficeAction.CONFIRM_SALES_PAYMENT,
                    AuthorizationKind.CHECKER))
        .isInstanceOf(ForbiddenException.class);
  }
}
