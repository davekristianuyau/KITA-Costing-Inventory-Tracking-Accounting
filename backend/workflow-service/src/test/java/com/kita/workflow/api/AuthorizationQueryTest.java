package com.kita.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kita.workflow.authorization.AuthorizationKind;
import com.kita.workflow.authorization.AuthorizationMapping;
import com.kita.workflow.authorization.AuthorizationMappingRepository;
import com.kita.workflow.authorization.AuthorizationRule;
import com.kita.workflow.authorization.BackOfficeAction;
import com.kita.workflow.common.security.Role;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Read-only view of the seeded role→action grants (FR-004, feature 016). Projection only: it adds no
 * behaviour and records nothing (FR-012).
 */
class AuthorizationQueryTest {

  private final AuthorizationMappingRepository repository =
      mock(AuthorizationMappingRepository.class);
  private final AuthorizationController controller = new AuthorizationController(repository);

  private static AuthorizationMapping mapping(
      BackOfficeAction action, Role role, AuthorizationKind kind) {
    AuthorizationMapping m = mock(AuthorizationMapping.class);
    when(m.toRule()).thenReturn(new AuthorizationRule(action, role, kind));
    return m;
  }

  @Test
  void returnsOneRowPerSeededMappingWithItsKind() {
    // build the stubbed mappings first — stubbing inside an open when(...) is an unfinished stub
    List<AuthorizationMapping> seeded =
        List.of(
            mapping(BackOfficeAction.TAKE_SALES_ORDER, Role.SALES, AuthorizationKind.MAKER),
            mapping(
                BackOfficeAction.CONFIRM_SALES_PAYMENT, Role.CASHIER, AuthorizationKind.CHECKER));
    when(repository.findAll()).thenReturn(seeded);

    List<AuthorizationController.AuthorizationRuleView> rows = controller.list();

    assertThat(rows).hasSize(2);
    assertThat(rows)
        .extracting(AuthorizationController.AuthorizationRuleView::kind)
        .containsExactlyInAnyOrder(AuthorizationKind.MAKER, AuthorizationKind.CHECKER);
  }

  @Test
  void ordersDeterministicallyByActionThenKindThenRole() {
    List<AuthorizationMapping> seeded =
        List.of(
            mapping(
                BackOfficeAction.CONFIRM_SALES_PAYMENT,
                Role.SALES_MANAGER,
                AuthorizationKind.CHECKER),
            mapping(BackOfficeAction.TAKE_SALES_ORDER, Role.SALES, AuthorizationKind.MAKER),
            mapping(
                BackOfficeAction.CONFIRM_SALES_PAYMENT, Role.CASHIER, AuthorizationKind.CHECKER));
    when(repository.findAll()).thenReturn(seeded);

    List<AuthorizationController.AuthorizationRuleView> rows = controller.list();

    assertThat(rows)
        .extracting(r -> r.action() + "/" + r.kind() + "/" + r.role())
        .containsExactly(
            "TAKE_SALES_ORDER/MAKER/SALES",
            "CONFIRM_SALES_PAYMENT/CHECKER/CASHIER",
            "CONFIRM_SALES_PAYMENT/CHECKER/SALES_MANAGER");
  }

  @Test
  void readingRecordsNoActivity() {
    when(repository.findAll()).thenReturn(List.of());

    assertThat(controller.list()).isEmpty();

    verify(repository, never()).save(any());
    verify(repository, never()).saveAll(any());
  }
}
