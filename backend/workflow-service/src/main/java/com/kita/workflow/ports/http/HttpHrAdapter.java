package com.kita.workflow.ports.http;

import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.ports.HrPort;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link HrPort}: an HTTP client to hr-service ({@code GET /api/hr/employees/{id}}). Selected by
 * {@code workflow.hr.adapter=http}. A 404 means unknown ⇒ empty; 5xx is transient (retried).
 *
 * <p><b>018 correction.</b> The previous mapping deserialised the response straight into
 * {@code EmployeeView(id, active, roles)}, but hr-service's real {@code EmployeeResponse} carries
 * neither field: it has a {@code status} enum (ACTIVE / ON_LEAVE / SUSPENDED / SEPARATED) and no
 * back-office role data at all. Against real HR that silently produced {@code active=false} +
 * {@code roles=null} — every actor rejected before any owning service was even called. Now:
 *
 * <ul>
 *   <li><b>active</b> is derived from the real {@code status} ({@code ACTIVE} ⇒ active).
 *   <li><b>roles</b> are mapped from the employee's {@code position} via the explicit, configurable
 *       {@code workflow.hr.position-roles.<POSITION>} map, because HR holds no back-office roles of
 *       its own. An unmapped position resolves to <em>no</em> roles — fail-closed: the action is
 *       refused (403) rather than silently granted. Populating this map (or sourcing identity from
 *       spec 017) is a deployment prerequisite for governed actions against real HR.
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "workflow.hr.adapter", havingValue = "http")
public class HttpHrAdapter implements HrPort {

  private static final Logger log = LoggerFactory.getLogger(HttpHrAdapter.class);

  private final RestClient client;
  private final RemoteCall remote;
  private final Map<String, String> positionRoles;

  public HttpHrAdapter(
      RestClient.Builder builder,
      @Value("${workflow.hr.base-url:http://hr-service:8085}") String baseUrl,
      RemoteCall remote,
      HrPositionRoles positionRoles) {
    this.client = builder.baseUrl(baseUrl).build();
    this.remote = remote;
    this.positionRoles = positionRoles.map();
  }

  /**
   * The subset of hr-service's real {@code EmployeeResponse} this caller reads. Bound by name, so a
   * rename on the HR side fails the consumer-contract test instead of silently nulling a field.
   */
  public record HrEmployeeResponse(String id, String status, String position) {}

  @Override
  public Optional<EmployeeView> getEmployee(String id) {
    Optional<HrEmployeeResponse> employee =
        remote.get(
            client,
            "/api/hr/employees/{id}",
            response -> {
              var status = response.getStatusCode();
              if (status.value() == 404) {
                return Optional.<HrEmployeeResponse>empty();
              }
              if (status.is5xxServerError()) {
                throw new TransientDownstreamException("hr-service " + status.value());
              }
              if (!status.is2xxSuccessful()) {
                return Optional.<HrEmployeeResponse>empty();
              }
              return Optional.ofNullable(response.bodyTo(HrEmployeeResponse.class));
            },
            id);
    return employee.map(e -> new EmployeeView(e.id(), isActive(e.status()), rolesFor(e.position())));
  }

  /** hr-service statuses: ACTIVE / ON_LEAVE / SUSPENDED / SEPARATED — only ACTIVE may act. */
  private static boolean isActive(String status) {
    return "ACTIVE".equalsIgnoreCase(status);
  }

  /** Fail-closed: an unmapped position grants nothing (the action is refused, never auto-granted). */
  private Set<String> rolesFor(String position) {
    if (position == null || position.isBlank()) {
      return Set.of();
    }
    // Normalized on both sides so "Sales Clerk" matches a `sales-clerk` config key — and because a
    // map key containing a space does not survive Spring's relaxed binding at all.
    String configured = positionRoles.get(HrPositionRoles.normalize(position));
    if (configured == null || configured.isBlank()) {
      log.warn(
          "no workflow.hr.position-roles mapping for position '{}': the actor gets no back-office"
              + " roles and governed actions will be refused (hr-service holds no roles of its own)",
          position);
      return Set.of();
    }
    return new HashSet<>(Arrays.asList(configured.split("\\s*,\\s*")));
  }
}
