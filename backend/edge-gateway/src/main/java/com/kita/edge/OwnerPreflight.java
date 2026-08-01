package com.kita.edge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 017 FR-019 — warn loudly at startup if no account resolves to an employee holding {@code OWNER}.
 *
 * <p>With the permissive fallback retired, a deployment in that state is <b>unadministerable</b>: nobody
 * can link the first account or grant the first role, and every governed action is refused. The failure
 * looks like "the system is broken" rather than "nobody has been made an owner", which is exactly the
 * kind of thing worth saying out loud on boot rather than leaving someone to discover.
 *
 * <p>Warns rather than refuses to start: the edge must still serve {@code /auth/**} so an operator can
 * sign in and fix it, and a personnel service that is merely slow to come up should not stop the gateway
 * booting. Set {@code edge.preflight.owner-check=false} to silence it.
 */
@Configuration
@ConditionalOnProperty(name = "edge.preflight.owner-check", havingValue = "true", matchIfMissing = true)
public class OwnerPreflight {

  private static final Logger log = LoggerFactory.getLogger(OwnerPreflight.class);

  @Bean
  ApplicationRunner ownerPreflightRunner(
      RoleResolver roleResolver,
      @Value("${edge.preflight.owner-account:owner}") String ownerAccount) {
    return args -> {
      try {
        var roles = roleResolver.rolesFor(ownerAccount).block();
        if (roles != null && roles.contains("OWNER")) {
          log.info("preflight: account '{}' resolves to an OWNER — deployment is administerable", ownerAccount);
          return;
        }
        log.warn(
            "PREFLIGHT (017 FR-019): account '{}' does not resolve to an employee holding OWNER."
                + " With the permissive fallback retired, NOBODY can administer links or grant roles."
                + " Link an account to an employee holding OWNER before relying on this deployment.",
            ownerAccount);
      } catch (RuntimeException e) {
        log.warn(
            "PREFLIGHT (017 FR-019): could not check for an OWNER account ({}). If the personnel"
                + " service is simply still starting this is harmless; if it stays unreachable, every"
                + " governed action will fail closed.",
            e.toString());
      }
    };
  }
}
