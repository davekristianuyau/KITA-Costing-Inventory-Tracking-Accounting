package com.kita.workflow.ports.http;

import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.ports.HrPort;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link HrPort}: an HTTP client to hr-service ({@code GET /api/hr/employees/{id}}). Selected by
 * {@code workflow.hr.adapter=http}. A 404 means unknown ⇒ empty; 5xx is transient (retryable).
 */
@Component
@ConditionalOnProperty(name = "workflow.hr.adapter", havingValue = "http")
public class HttpHrAdapter implements HrPort {

  private final RestClient client;

  public HttpHrAdapter(
      RestClient.Builder builder,
      @Value("${workflow.hr.base-url:http://hr-service:8085}") String baseUrl) {
    this.client = builder.baseUrl(baseUrl).build();
  }

  @Override
  public Optional<EmployeeView> getEmployee(String id) {
    return client
        .get()
        .uri("/api/hr/employees/{id}", id)
        .exchange(
            (request, response) -> {
              HttpStatusCode status = response.getStatusCode();
              if (status.value() == 404) {
                return Optional.empty();
              }
              if (status.is5xxServerError()) {
                throw new TransientDownstreamException("hr-service " + status.value());
              }
              if (!status.is2xxSuccessful()) {
                return Optional.<EmployeeView>empty();
              }
              return Optional.ofNullable(response.bodyTo(EmployeeView.class));
            });
  }
}
