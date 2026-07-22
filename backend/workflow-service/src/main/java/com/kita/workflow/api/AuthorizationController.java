package com.kita.workflow.api;

import com.kita.workflow.authorization.AuthorizationKind;
import com.kita.workflow.authorization.AuthorizationMappingRepository;
import com.kita.workflow.authorization.AuthorizationRule;
import com.kita.workflow.authorization.BackOfficeAction;
import com.kita.workflow.common.security.Role;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of the seeded role→action grants (FR-004). A projection of
 * {@code authorization_mapping} — it grants nothing, changes nothing, and records no activity
 * (FR-012). Mappings are viewed here, never edited.
 */
@RestController
@RequestMapping("/api/workflow/authorization")
public class AuthorizationController {

  private static final Comparator<AuthorizationRule> STABLE =
      Comparator.comparing(AuthorizationRule::action)
          .thenComparing(AuthorizationRule::kind)
          .thenComparing(AuthorizationRule::role);

  private final AuthorizationMappingRepository repository;

  public AuthorizationController(AuthorizationMappingRepository repository) {
    this.repository = repository;
  }

  public record AuthorizationRuleView(
      BackOfficeAction action, Role role, AuthorizationKind kind) {}

  @GetMapping
  public List<AuthorizationRuleView> list() {
    return repository.findAll().stream()
        .map(m -> m.toRule())
        .sorted(STABLE)
        .map(r -> new AuthorizationRuleView(r.action(), r.role(), r.kind()))
        .toList();
  }
}
