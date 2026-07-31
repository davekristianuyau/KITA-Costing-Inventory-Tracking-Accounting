package com.kita.workflow.ports.fake;

import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.ports.HrPort;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link HrPort} — a <b>build/test seam only</b>, so {@code :workflow-service:test} can run
 * without the rest of the stack. It is no longer a deployed path: every stack sets
 * {@code HR_ADAPTER=http} (017 FR-012), and the sim's logins resolve through real account links rather
 * than through names that happen to match seeded employee ids.
 *
 * <p>Keyed by <b>account name</b>, matching the real adapter — the whole point of 017 is that the value
 * arriving in {@code X-Kita-User} is an account, not an employee id.
 */
@Component
@ConditionalOnProperty(name = "workflow.hr.adapter", havingValue = "fake", matchIfMissing = true)
public class InMemoryHrAdapter implements HrPort {

  /** What the fake knows about one account. A null {@code employeeId} models a dangling link. */
  private record Linked(String employeeId, String status, Set<String> roles) {}

  private final ConcurrentMap<String, Linked> byAccount = new ConcurrentHashMap<>();

  /** Accounts that exist but are deliberately linked to nothing (the FR-005 path). */
  private final Set<String> unlinked = ConcurrentHashMap.newKeySet();

  /** When true every resolve reports hr unreachable, to exercise the fail-closed path (FR-011). */
  private volatile boolean unavailable = false;

  public InMemoryHrAdapter(@Value("${workflow.security.stub:true}") boolean stub) {
    // Fixtures for isolated builds: one account per back-office role, so maker–checker paths can be
    // exercised without a database. Account name and employee id are deliberately identical here, so
    // these long-standing fixtures keep attributing exactly as before; tests that care about the
    // account≠employee distinction seed it explicitly with seedWithStatus.
    seedFixture("emp-sales", "SALES");
    seedFixture("emp-cashier", "CASHIER");
    seedFixture("emp-sales-mgr", "SALES_MANAGER");
    seedFixture("emp-whse", "WAREHOUSE_STAFF");
    seedFixture("emp-whse-mgr", "WAREHOUSE_MANAGER");
    seedFixture("emp-proc", "PROCUREMENT_STAFF");
    seedFixture("emp-approver", "PROCUREMENT_APPROVER");
    seedFixture("emp-prod", "PRODUCTION");
    seedFixture("emp-crm", "CRM_ADMIN");
    seedWithStatus("emp-separated", "emp-separated", "SEPARATED", "SALES");
    if (stub) {
      seedFixture(
          CallerContext.STUB_ACTOR,
          "SALES",
          "CASHIER",
          "SALES_MANAGER",
          "WAREHOUSE_STAFF",
          "WAREHOUSE_MANAGER",
          "PROCUREMENT_STAFF",
          "PROCUREMENT_APPROVER",
          "PRODUCTION",
          "CRM_ADMIN");
    }
  }

  /** A fixture whose employee id equals its account name (see the constructor's note). */
  private void seedFixture(String account, String... roles) {
    seedWithStatus(account, account, "ACTIVE", roles);
  }

  @Override
  public ResolutionOutcome resolve(String accountUsername) {
    if (unavailable) {
      return ResolutionOutcome.unavailable("hr-service unavailable (fake)");
    }
    if (accountUsername == null || accountUsername.isBlank()) {
      return ResolutionOutcome.noEmployeeLinked(String.valueOf(accountUsername));
    }
    if (unlinked.contains(accountUsername)) {
      return ResolutionOutcome.noEmployeeLinked(accountUsername);
    }
    Linked linked = byAccount.get(accountUsername);
    if (linked == null) {
      return ResolutionOutcome.noEmployeeLinked(accountUsername);
    }
    if (linked.employeeId() == null) {
      return ResolutionOutcome.missing(accountUsername);
    }
    if (!"ACTIVE".equalsIgnoreCase(linked.status())) {
      return ResolutionOutcome.notActive(linked.employeeId(), linked.status());
    }
    return ResolutionOutcome.resolved(linked.employeeId(), linked.roles());
  }

  // --- test seams --------------------------------------------------------------------------------

  /** Link an active employee to this account, holding the given roles. */
  public void seed(String account, String... roles) {
    seedWithStatus(account, "emp-" + account, "ACTIVE", roles);
  }

  /**
   * Link an employee with an explicit status (e.g. {@code SEPARATED}) to this account. Named distinctly
   * from {@link #seed} because a varargs overload would be ambiguous at every call site.
   */
  public void seedWithStatus(String account, String employeeId, String status, String... roles) {
    byAccount.put(
        account,
        new Linked(employeeId, status, Arrays.stream(roles).collect(Collectors.toUnmodifiableSet())));
    unlinked.remove(account);
  }

  /** An account that exists but has no employee linked (FR-005). */
  public void seedUnlinked(String account) {
    byAccount.remove(account);
    unlinked.add(account);
  }

  /** A link pointing at an employee record that no longer exists (FR-006). */
  public void seedMissingEmployee(String account) {
    byAccount.put(account, new Linked(null, "ACTIVE", Set.of()));
    unlinked.remove(account);
  }

  /** Make hr appear unreachable, to exercise the fail-closed path (FR-011). */
  public void setUnavailable(boolean value) {
    this.unavailable = value;
  }

  public Map<String, String> linkedAccounts() {
    return byAccount.entrySet().stream()
        .filter(e -> e.getValue().employeeId() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().employeeId()));
  }

  public void reset() {
    byAccount.clear();
    unlinked.clear();
    unavailable = false;
  }
}
