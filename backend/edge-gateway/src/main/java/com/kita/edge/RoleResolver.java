package com.kita.edge;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Resolves the signed-in account to the roles the <b>personnel record</b> holds — once per request
 * (017 FR-018).
 *
 * <p>Deliberately <b>not</b> read from the session token. Roles in a token mean the token itself
 * carries authority: a stolen or stale one keeps its privileges, and a revoked role survives until
 * expiry. Resolving beside the session instead keeps roles server-side and fresh, so a revocation bites
 * on the next request (SC-002/SC-006).
 *
 * <p>Equally deliberately <b>uncached</b> — a cache would reintroduce exactly that delay.
 *
 * <p>Failure handling is asymmetric on purpose:
 *
 * <ul>
 *   <li>hr unreachable / 5xx ⇒ {@link Unavailable}, and the caller refuses the request. Never grant on
 *       a failed lookup (FR-011).
 *   <li>404 (no employee linked) or a non-ACTIVE employee ⇒ <b>no roles</b>. The request proceeds and
 *       is refused by the receiving service's own authorization, which keeps "you may not do this"
 *       distinguishable from "we could not check".
 * </ul>
 */
@Component
public class RoleResolver {

  private static final Logger log = LoggerFactory.getLogger(RoleResolver.class);

  /** Thrown into the reactive chain when the personnel record cannot be consulted at all. */
  public static class Unavailable extends RuntimeException {
    public Unavailable(String message) {
      super(message);
    }
  }

  /** The subset of hr's employee response the edge needs. */
  record HrEmployee(String id, String status, List<String> roles) {}

  private final WebClient hr;
  private final Duration timeout;

  public RoleResolver(
      WebClient.Builder builder,
      @Value("${edge.hr.base-url:http://hr-service:8085}") String baseUrl,
      @Value("${edge.hr.timeout-ms:2000}") long timeoutMillis) {
    this.hr = builder.baseUrl(baseUrl).build();
    this.timeout = Duration.ofMillis(timeoutMillis);
  }

  /** The roles this account may act with right now; empty when it resolves to nobody actable. */
  public Mono<List<String>> rolesFor(String accountUsername) {
    if (accountUsername == null || accountUsername.isBlank()) {
      return Mono.just(List.of());
    }
    return hr.get()
        .uri("/api/hr/employees/by-account/{username}", accountUsername)
        .retrieve()
        .bodyToMono(HrEmployee.class)
        .timeout(timeout)
        .map(
            employee -> {
              if (!"ACTIVE".equalsIgnoreCase(employee.status())) {
                // Linked but not actable: no roles, so the receiving service refuses on its own terms.
                return List.<String>of();
              }
              return employee.roles() == null ? List.<String>of() : employee.roles();
            })
        .onErrorResume(
            WebClientResponseException.NotFound.class,
            notFound -> Mono.just(List.of())) // no employee linked to this account
        .onErrorMap(
            e -> !(e instanceof Unavailable),
            e -> {
              log.warn("cannot resolve roles for account {}: {}", accountUsername, e.toString());
              return new Unavailable("personnel record unavailable");
            });
  }
}
