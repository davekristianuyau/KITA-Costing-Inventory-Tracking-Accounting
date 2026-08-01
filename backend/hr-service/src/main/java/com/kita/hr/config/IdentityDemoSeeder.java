package com.kita.hr.config;

import com.kita.hr.employee.CreateEmployeeRequest;
import com.kita.hr.employee.Employee;
import com.kita.hr.employee.EmployeeRepository;
import com.kita.hr.employee.EmployeeService;
import com.kita.hr.employee.EmploymentType;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds demo employees, their account links and their roles (017 FR-012), so the simulation resolves
 * through <b>real links</b> rather than through login names that happen to match seeded employee ids.
 *
 * <p>The {@code owner} link matters most: once the permissive fallback is off, a deployment where no
 * account resolves to an {@code OWNER} cannot be administered at all — nobody could grant the first
 * role or link the first account (FR-019). Seeding it is what keeps a fresh stack usable.
 *
 * <p>Goes through {@link EmployeeService} rather than writing rows directly, so the seeded data is
 * created by the same code path (and leaves the same audit trail) as a real administrator's actions.
 *
 * <p>Idempotent, and skipped entirely once any employee exists, so it never touches a real deployment's
 * data. Disable with {@code hr.seed.enabled=false}.
 */
@Configuration
@ConditionalOnProperty(name = "hr.seed.enabled", havingValue = "true", matchIfMissing = true)
public class IdentityDemoSeeder {

  private static final Logger log = LoggerFactory.getLogger(IdentityDemoSeeder.class);

  /** account → the roles that account's employee holds. Ordered so `owner` is created first. */
  private static final Map<String, List<String>> DEMO_STAFF = new LinkedHashMap<>();

  static {
    DEMO_STAFF.put("owner", List.of("OWNER"));
    DEMO_STAFF.put("emp-sales", List.of("SALES"));
    DEMO_STAFF.put("emp-cashier", List.of("CASHIER"));
    DEMO_STAFF.put("emp-sales-mgr", List.of("SALES_MANAGER"));
    DEMO_STAFF.put("emp-whse", List.of("WAREHOUSE_STAFF"));
    DEMO_STAFF.put("emp-whse-mgr", List.of("WAREHOUSE_MANAGER"));
    DEMO_STAFF.put("emp-proc", List.of("PROCUREMENT_STAFF", "PROCUREMENT_ADMIN"));
    DEMO_STAFF.put("emp-approver", List.of("PROCUREMENT_APPROVER", "APPROVER"));
    DEMO_STAFF.put("emp-prod", List.of("PRODUCTION"));
    DEMO_STAFF.put("emp-crm", List.of("CRM_ADMIN"));
  }

  @Bean
  ApplicationRunner seedIdentityDemoData(EmployeeService service, EmployeeRepository employees) {
    return args -> {
      if (employees.count() > 0) {
        return; // never touch an existing dataset
      }
      int seeded = 0;
      for (Map.Entry<String, List<String>> entry : DEMO_STAFF.entrySet()) {
        String account = entry.getKey();
        Employee employee =
            service.create(
                new CreateEmployeeRequest(
                    "EMP-" + account.toUpperCase().replace("-", ""),
                    "Demo",
                    account,
                    null,
                    null,
                    null,
                    EmploymentType.REGULAR,
                    account,
                    LocalDate.of(2025, 1, 1),
                    null,
                    null,
                    null,
                    null),
                "seed");
        service.linkAccount(employee.getId(), account, "seed");
        service.replaceRoles(employee.getId(), entry.getValue(), "seed");
        seeded++;
      }
      log.info(
          "seeded {} demo employees with real account links and roles (incl. an OWNER, FR-019)",
          seeded);
    };
  }
}
