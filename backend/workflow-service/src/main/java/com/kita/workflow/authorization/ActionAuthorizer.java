package com.kita.workflow.authorization;

import com.kita.workflow.common.ForbiddenException;
import com.kita.workflow.common.security.Role;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure authorization check (FR-002, FR-021, SC-004): does any of the caller's HR-resolved roles grant
 * the requested {@code (action, kind)}? Built from {@code authorization_mapping} rows, but holds no DB
 * dependency so it is unit-testable in isolation.
 */
public class ActionAuthorizer {

  private final Map<BackOfficeAction, Map<AuthorizationKind, Set<Role>>> grants =
      new EnumMap<>(BackOfficeAction.class);

  public ActionAuthorizer(Collection<AuthorizationRule> rules) {
    for (AuthorizationRule r : rules) {
      grants
          .computeIfAbsent(r.action(), a -> new EnumMap<>(AuthorizationKind.class))
          .computeIfAbsent(r.kind(), k -> EnumSet.noneOf(Role.class))
          .add(r.role());
    }
  }

  /** True if any held role grants the (action, kind). */
  public boolean permits(Set<Role> heldRoles, BackOfficeAction action, AuthorizationKind kind) {
    Set<Role> granted =
        grants.getOrDefault(action, Map.of()).getOrDefault(kind, Set.of());
    return heldRoles.stream().anyMatch(granted::contains);
  }

  /** Enforce the grant, else 403 with no side effect. */
  public void authorize(Set<Role> heldRoles, BackOfficeAction action, AuthorizationKind kind) {
    if (!permits(heldRoles, action, kind)) {
      throw new ForbiddenException(
          "role not permitted for " + action + " (" + kind + ")");
    }
  }
}
