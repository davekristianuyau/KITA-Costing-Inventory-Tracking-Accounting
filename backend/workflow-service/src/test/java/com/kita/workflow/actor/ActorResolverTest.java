package com.kita.workflow.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.workflow.common.DownstreamUnavailableException;
import com.kita.workflow.common.ForbiddenException;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.common.security.Role;
import com.kita.workflow.ports.fake.InMemoryHrAdapter;
import org.junit.jupiter.api.Test;

/**
 * 017 US1 — the account is resolved to an employee, and every way that can fail is reported
 * <b>distinctly</b> (SC-004). The point of these tests is not that failures are refused, but that they
 * are refused for <em>visibly different reasons</em>: an empty Optional used to collapse "no employee",
 * "missing employee" and "inactive employee" into one indistinguishable value.
 */
class ActorResolverTest {

  // stub=false so only explicitly seeded accounts resolve (no all-roles stub actor).
  private final InMemoryHrAdapter hr = new InMemoryHrAdapter(false);
  private final ActorResolver resolver = new ActorResolver(hr);

  @Test
  void resolvesAnAccountToItsEmployeeWithTheRolesHrHolds() {
    hr.seed("alice", "CASHIER");

    ResolvedActor actor = resolver.resolve("alice");

    // The actor is the EMPLOYEE, not the login — that is the whole point of 017 (SC-003).
    assertThat(actor.employeeId()).isEqualTo("emp-alice");
    assertThat(actor.roles()).containsExactly(Role.CASHIER);
  }

  @Test
  void anAccountWithNoEmployeeLinkedIsRejectedWithThatReason() {
    hr.seedUnlinked("orphan");

    assertThatThrownBy(() -> resolver.resolve("orphan"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("no employee is linked");
  }

  @Test
  void anInactiveEmployeeIsRejectedWithAReasonNamingTheStatus() {
    hr.seedWithStatus("bob", "emp-bob", "SEPARATED", "SALES");

    assertThatThrownBy(() -> resolver.resolve("bob"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("SEPARATED");
  }

  @Test
  void aLinkPointingAtAMissingEmployeeIsItsOwnReason() {
    hr.seedMissingEmployee("ghost");

    assertThatThrownBy(() -> resolver.resolve("ghost"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("no longer exists");
  }

  @Test
  void anUnreachablePersonnelSystemFailsClosedAsUnavailableNotAsARefusal() {
    hr.seed("carol", "SALES");
    hr.setUnavailable(true);

    // 503, never 403/422: the user is told to retry, and access is NEVER granted on a failed lookup.
    assertThatThrownBy(() -> resolver.resolve("carol"))
        .isInstanceOf(DownstreamUnavailableException.class);
  }

  @Test
  void noResolutionFailureIsEverReportedAsNotPermitted() {
    hr.seedUnlinked("orphan");
    hr.seedWithStatus("bob", "emp-bob", "SEPARATED");
    hr.seedMissingEmployee("ghost");

    for (String account : new String[] {"orphan", "bob", "ghost", "never-seen"}) {
      assertThatThrownBy(() -> resolver.resolve(account))
          .as("resolution failure for '%s' must not masquerade as a permission refusal", account)
          .isNotInstanceOf(ForbiddenException.class);
    }
  }

  @Test
  void rejectsAMissingAccount() {
    assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> resolver.resolve("  ")).isInstanceOf(ValidationException.class);
  }

  @Test
  void ignoresUnknownRoleTokensFromHr() {
    hr.seed("mixed", "SALES", "NOT_A_REAL_ROLE");

    // An unrecognized token grants nothing and must not error (FR-004 scenario 3).
    assertThat(resolver.resolve("mixed").roles()).containsExactly(Role.SALES);
  }

  // --- 017 US3: leavers, and the freshness the whole design depends on --------------------------

  @Test
  void separationTakesEffectOnTheVeryNextActionWithoutReLogin() {
    hr.seed("dan", "SALES");
    assertThat(resolver.resolve("dan").roles()).containsExactly(Role.SALES);

    // The "session" is unchanged — only the personnel record moved. SC-002 requires that to be enough.
    hr.seedWithStatus("dan", "emp-dan", "SEPARATED", "SALES");

    assertThatThrownBy(() -> resolver.resolve("dan"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("SEPARATED");
  }

  @Test
  void aRoleChangeAppliesOnTheNextActionProvingNothingIsCached() {
    hr.seed("eve", "SALES");
    assertThat(resolver.resolve("eve").roles()).containsExactly(Role.SALES);

    hr.seed("eve", "CASHIER");

    // If any layer cached the resolution, this would still report SALES — and a revoked role would
    // keep working until the cache expired, which is exactly what SC-002/SC-006 forbid.
    assertThat(resolver.resolve("eve").roles()).containsExactly(Role.CASHIER);
  }

  @Test
  void revokingEveryRoleLeavesAResolvedActorWithNoGrants() {
    hr.seed("frank", "SALES");
    hr.seed("frank");

    // Still a valid employee — they simply may do nothing. That is a 403 later, not a 422 here.
    assertThat(resolver.resolve("frank").roles()).isEmpty();
  }
}
