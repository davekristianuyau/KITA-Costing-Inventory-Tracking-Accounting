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

  // --- 017 FR-017: OWNER is the highest-position administrator -------------------------------------
  // NOTE: workflow does NOT decide roles in CallerContext (it does not read roles at all) — the
  // decision is here, against authorization_mapping rows, where OWNER never appears. Putting the
  // OWNER branch anywhere else would leave an owner refused EVERY governed action.

  @Test
  void ownerPermitsEveryActionAndKindEvenThoughItIsInNoMappingRow() {
    ActionAuthorizer authorizer =
        new ActionAuthorizer(
            java.util.List.of(
                new AuthorizationRule(
                    BackOfficeAction.TAKE_SALES_ORDER, Role.SALES, AuthorizationKind.MAKER)));

    for (BackOfficeAction action : BackOfficeAction.values()) {
      for (AuthorizationKind kind : AuthorizationKind.values()) {
        assertThat(authorizer.permits(java.util.Set.of(Role.OWNER), action, kind))
            .as("OWNER must permit %s (%s)", action, kind)
            .isTrue();
      }
    }
  }

  @Test
  void ownerAlongsideOtherRolesStillPermitsEverything() {
    ActionAuthorizer authorizer = new ActionAuthorizer(java.util.List.of());
    assertThat(
            authorizer.permits(
                java.util.Set.of(Role.SALES, Role.OWNER),
                BackOfficeAction.APPROVE_PURCHASE_ORDER,
                AuthorizationKind.CHECKER))
        .isTrue();
  }

  @Test
  void withoutOwnerAnUnmappedActionIsStillRefused() {
    ActionAuthorizer authorizer = new ActionAuthorizer(java.util.List.of());
    assertThat(
            authorizer.permits(
                java.util.Set.of(Role.SALES),
                BackOfficeAction.APPROVE_PURCHASE_ORDER,
                AuthorizationKind.CHECKER))
        .as("OWNER must be the only blanket grant")
        .isFalse();
  }

  // --- 017 FR-013 guard: this feature changes WHOSE roles are checked, never WHAT the rules are ---

  @Test
  void ownerIsTheOnlyBlanketGrantEverIntroduced() {
    ActionAuthorizer authorizer =
        new ActionAuthorizer(
            java.util.List.of(
                new AuthorizationRule(
                    BackOfficeAction.TAKE_SALES_ORDER, Role.SALES, AuthorizationKind.MAKER)));

    // Every non-OWNER role must grant exactly what the mapping says and nothing more. If a future
    // change adds a second implicit grant, this fails — which is the point: FR-013 says the rules
    // themselves do not move, and FR-020's OWNER exemption is the single deliberate exception.
    for (Role role : Role.values()) {
      if (role == Role.OWNER) {
        continue;
      }
      boolean mapped = role == Role.SALES;
      assertThat(
              authorizer.permits(
                  java.util.Set.of(role),
                  BackOfficeAction.TAKE_SALES_ORDER,
                  AuthorizationKind.MAKER))
          .as("%s should%s grant TAKE_SALES_ORDER (MAKER)", role, mapped ? "" : " NOT")
          .isEqualTo(mapped);

      assertThat(
              authorizer.permits(
                  java.util.Set.of(role),
                  BackOfficeAction.TAKE_SALES_ORDER,
                  AuthorizationKind.CHECKER))
          .as("%s must not gain an unmapped (action, kind)", role)
          .isFalse();
    }
  }

  @Test
  void anEmptyRoleSetGrantsNothing() {
    ActionAuthorizer authorizer =
        new ActionAuthorizer(
            java.util.List.of(
                new AuthorizationRule(
                    BackOfficeAction.TAKE_SALES_ORDER, Role.SALES, AuthorizationKind.MAKER)));

    assertThat(
            authorizer.permits(
                java.util.Set.of(), BackOfficeAction.TAKE_SALES_ORDER, AuthorizationKind.MAKER))
        .isFalse();
  }
}
