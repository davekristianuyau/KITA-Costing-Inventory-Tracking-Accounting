package com.kita.workflow.actor;

import com.kita.workflow.common.DownstreamUnavailableException;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.common.security.Role;
import com.kita.workflow.ports.HrPort;
import com.kita.workflow.ports.HrPort.ResolutionOutcome;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Resolves the signed-in <b>account</b> to the employee it belongs to, and their roles, from the
 * personnel record (017 FR-001/FR-004). Roles never come from a header or the session token.
 *
 * <p>Each failure is reported <b>distinctly</b> (SC-004) — "no employee linked", "employee not active"
 * and "employee missing" are business rejections (422) carrying their own reason, while an unreachable
 * hr-service is an operational failure (503). None of them may be reported as "not permitted" (403),
 * which means something quite different: resolved fine, but the roles don't grant this action.
 *
 * <p>Deliberately <b>uncached</b>: SC-002/SC-006 require a status or role change to take effect on the
 * very next action, and a cache would reintroduce exactly the delay this design removes.
 */
@Component
public class ActorResolver {

  private final HrPort hrPort;

  public ActorResolver(HrPort hrPort) {
    this.hrPort = hrPort;
  }

  /**
   * @param accountUsername the signed-in account ({@code X-Kita-User}) — an account name, not an
   *     employee id; resolving one to the other is the point of this feature
   * @return the acting employee and the roles the personnel record holds for them
   */
  public ResolvedActor resolve(String accountUsername) {
    if (accountUsername == null || accountUsername.isBlank()) {
      throw new ValidationException("no acting account (missing X-Kita-User)");
    }
    ResolutionOutcome outcome = hrPort.resolve(accountUsername);
    return switch (outcome.status()) {
      case RESOLVED -> new ResolvedActor(outcome.employeeId(), mapRoles(outcome.roles()));
      // Fail closed: never grant access because the personnel system could not be asked (FR-011).
      case UNAVAILABLE -> throw new DownstreamUnavailableException(
          "cannot check the personnel record right now: " + outcome.detail());
      // The three business rejections. Each keeps its own reason so the user is told what to fix.
      case NO_EMPLOYEE_LINKED, EMPLOYEE_NOT_ACTIVE, EMPLOYEE_MISSING -> throw new ValidationException(
          outcome.detail());
    };
  }

  /** HR role tokens are opaque; one this service does not recognize grants nothing and never errors. */
  private Set<Role> mapRoles(Set<String> tokens) {
    Set<Role> roles = EnumSet.noneOf(Role.class);
    if (tokens != null) {
      for (String token : tokens) {
        try {
          roles.add(Role.valueOf(token.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
          // unknown role token from HR: ignore, it grants nothing here (FR-004)
        }
      }
    }
    return roles;
  }
}
