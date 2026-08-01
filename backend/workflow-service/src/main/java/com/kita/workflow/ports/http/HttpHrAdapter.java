package com.kita.workflow.ports.http;

import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.ports.HrPort;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link HrPort}: an HTTP client to hr-service. Selected by {@code workflow.hr.adapter=http}.
 *
 * <p><b>017.</b> Resolves by <em>account username</em> ({@code GET /api/hr/employees/by-account/{u}}),
 * which is what {@code X-Kita-User} carries — the previous lookup used an HR UUID that no caller ever
 * had. One call returns status <b>and</b> roles, so authorization needs a single round trip.
 *
 * <p>Status mapping is deliberately explicit, because SC-004 forbids collapsing these into each other:
 * 404 ⇒ no employee linked; 200 with a non-ACTIVE status ⇒ inactive (reason names it); 5xx/unreachable
 * ⇒ {@code UNAVAILABLE}, which the caller turns into a retryable 503 rather than granting anything
 * (fail closed, FR-011).
 *
 * <p>Deliberately <b>uncached</b>: caching would delay revocation, and SC-002/SC-006 require a status
 * or role change to bite on the very next action.
 */
@Component
@ConditionalOnProperty(name = "workflow.hr.adapter", havingValue = "http")
public class HttpHrAdapter implements HrPort {

  private final RestClient client;
  private final RemoteCall remote;

  public HttpHrAdapter(
      RestClient.Builder builder,
      @Value("${workflow.hr.base-url:http://hr-service:8085}") String baseUrl,
      RemoteCall remote) {
    this.client = builder.baseUrl(baseUrl).build();
    this.remote = remote;
  }

  /** The subset of hr's real {@code EmployeeResponse} this caller reads (bound by name). */
  record HrEmployeeResponse(String id, String status, List<String> roles) {}

  @Override
  public ResolutionOutcome resolve(String accountUsername) {
    try {
      return remote.get(
          client,
          "/api/hr/employees/by-account/{username}",
          response -> {
            var code = response.getStatusCode();
            if (code.value() == 404) {
              return ResolutionOutcome.noEmployeeLinked(accountUsername);
            }
            if (code.is5xxServerError()) {
              throw new TransientDownstreamException("hr-service " + code.value());
            }
            if (!code.is2xxSuccessful()) {
              // Any other non-2xx is an unusable answer — fail closed rather than guess.
              return ResolutionOutcome.unavailable("hr-service returned " + code.value());
            }
            HrEmployeeResponse body = response.bodyTo(HrEmployeeResponse.class);
            if (body == null || body.id() == null) {
              return ResolutionOutcome.missing(accountUsername);
            }
            if (!"ACTIVE".equalsIgnoreCase(body.status())) {
              return ResolutionOutcome.notActive(body.id(), body.status());
            }
            return ResolutionOutcome.resolved(
                body.id(), body.roles() == null ? Set.of() : Set.copyOf(body.roles()));
          },
          accountUsername);
    } catch (RuntimeException e) {
      // Retries are exhausted or the host is unreachable: never grant on a failed lookup (FR-011).
      return ResolutionOutcome.unavailable("hr-service unavailable: " + e.getMessage());
    }
  }
}
