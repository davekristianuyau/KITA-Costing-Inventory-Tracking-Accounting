package com.kita.identity.config;

import com.kita.identity.domain.AppUser;
import com.kita.identity.domain.AppUserRepository;
import com.kita.identity.domain.Client;
import com.kita.identity.domain.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Seeds two demo clients and their users if the store is empty (idempotent). client-a prefers AWS (used by
 * the US4 LocalStack imitation); client-b prefers GCP. Demo password comes from config (never committed).
 *
 * <p>Each client also gets one login per back-office employee. The acting employee for a governed action is
 * the signed-in user (the edge sets {@code X-Kita-User} from the session and strips anything the browser
 * sends), so acting as a different role means signing in as that employee — which is also how the
 * maker-checker split is exercised. These names match the employees workflow-service resolves roles for.
 * Feature 017 replaces this name-matching convention with a real account→employee link.
 */
@Configuration
@ConditionalOnProperty(name = "identity.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoSeeder {

  private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

  /** One login per seeded back-office employee — the names workflow-service knows and holds roles for. */
  private static final String[] BACK_OFFICE_EMPLOYEES = {
    "emp-sales",
    "emp-cashier",
    "emp-sales-mgr",
    "emp-whse",
    "emp-whse-mgr",
    "emp-proc",
    "emp-approver",
    "emp-prod",
    "emp-crm"
  };

  @Bean
  ApplicationRunner seedDemoData(
      ClientRepository clients,
      AppUserRepository users,
      PasswordEncoder encoder,
      @Value("${identity.seed.demo-password:demo-pass}") String demoPassword) {
    return args -> {
      if (clients.existsByCompanyId("client-a")) {
        return;
      }
      Client a =
          clients.save(
              new Client("client-a", "Client A", "AWS", "kita-client-a-gateway-1:8081"));
      Client b =
          clients.save(new Client("client-b", "Client B", "GCP", "kita-client-b-gateway-1:8081"));
      String hash = encoder.encode(demoPassword);
      users.save(new AppUser(a, "alice", hash));
      users.save(new AppUser(b, "bob", hash));
      for (Client client : new Client[] {a, b}) {
        for (String employee : BACK_OFFICE_EMPLOYEES) {
          users.save(new AppUser(client, employee, hash));
        }
      }
      log.info(
          "seeded demo clients client-a/client-b with users alice/bob + {} back-office employee logins each",
          BACK_OFFICE_EMPLOYEES.length);
    };
  }
}
