package com.kita.workflow.ports.fake;

import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.ports.HrPort;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link HrPort} for isolated build/test and dev. Seeds one employee per role plus the
 * all-roles stub actor so the quickstart runs self-contained. Unknown ids resolve to empty, so
 * "unknown / separated employee is rejected" holds.
 *
 * <p><b>Also a stopgap in the local simulation</b> (see docker-compose: {@code HR_ADAPTER=fake} while the
 * other adapters run {@code http}). Nothing yet maps a login account to an employee record: the edge sets
 * {@code X-Kita-User} to the login name, while {@link com.kita.workflow.ports.http.HttpHrAdapter} looks an
 * employee up by its own id. The sim works around it by naming demo logins after these seeds. Closing that
 * gap properly is spec {@code 017-account-employee-identity}; when it lands, the sim moves to the http
 * adapter and this class goes back to being test-only.
 */
@Component
@ConditionalOnProperty(name = "workflow.hr.adapter", havingValue = "fake", matchIfMissing = true)
public class InMemoryHrAdapter implements HrPort {

  private final ConcurrentMap<String, EmployeeView> employees = new ConcurrentHashMap<>();

  public InMemoryHrAdapter(@Value("${workflow.security.stub:true}") boolean stub) {
    seed("emp-sales", "SALES");
    seed("emp-cashier", "CASHIER");
    seed("emp-sales-mgr", "SALES_MANAGER");
    seed("emp-whse", "WAREHOUSE_STAFF");
    seed("emp-whse-mgr", "WAREHOUSE_MANAGER");
    seed("emp-proc", "PROCUREMENT_STAFF");
    seed("emp-approver", "PROCUREMENT_APPROVER");
    seed("emp-prod", "PRODUCTION");
    seed("emp-crm", "CRM_ADMIN");
    seed("emp-separated", false, "SALES");
    if (stub) {
      employees.put(
          CallerContext.STUB_ACTOR,
          new EmployeeView(
              CallerContext.STUB_ACTOR,
              true,
              Set.of(
                  "SALES",
                  "CASHIER",
                  "SALES_MANAGER",
                  "WAREHOUSE_STAFF",
                  "WAREHOUSE_MANAGER",
                  "PROCUREMENT_STAFF",
                  "PROCUREMENT_APPROVER",
                  "PRODUCTION",
                  "CRM_ADMIN")));
    }
  }

  @Override
  public Optional<EmployeeView> getEmployee(String id) {
    return Optional.ofNullable(employees.get(id));
  }

  /** Seed/replace an employee for tests. */
  public void seed(String id, boolean active, String... roles) {
    employees.put(
        id, new EmployeeView(id, active, Arrays.stream(roles).collect(Collectors.toSet())));
  }

  public void seed(String id, String... roles) {
    seed(id, true, roles);
  }
}
