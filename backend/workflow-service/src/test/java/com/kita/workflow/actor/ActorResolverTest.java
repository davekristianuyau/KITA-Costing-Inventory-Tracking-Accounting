package com.kita.workflow.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.workflow.common.ValidationException;
import com.kita.workflow.common.security.Role;
import com.kita.workflow.ports.fake.InMemoryHrAdapter;
import org.junit.jupiter.api.Test;

/** Pure unit tests for actor validation + HR role resolution (FR-001, FR-002). */
class ActorResolverTest {

  // stub=false so only explicitly seeded employees resolve (no all-roles stub-admin).
  private final InMemoryHrAdapter hr = new InMemoryHrAdapter(false);
  private final ActorResolver resolver = new ActorResolver(hr);

  @Test
  void resolvesActiveEmployeeWithHrRoles() {
    ResolvedActor actor = resolver.resolve("emp-cashier");
    assertThat(actor.employeeId()).isEqualTo("emp-cashier");
    assertThat(actor.roles()).containsExactly(Role.CASHIER);
  }

  @Test
  void rejectsSeparatedEmployee() {
    assertThatThrownBy(() -> resolver.resolve("emp-separated"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("not active");
  }

  @Test
  void rejectsUnknownEmployee() {
    assertThatThrownBy(() -> resolver.resolve("nobody")).isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsMissingActor() {
    assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(ValidationException.class);
  }

  @Test
  void ignoresUnknownRoleTokensFromHr() {
    hr.seed("emp-mixed", "SALES", "NOT_A_REAL_ROLE");
    assertThat(resolver.resolve("emp-mixed").roles()).containsExactly(Role.SALES);
  }
}
