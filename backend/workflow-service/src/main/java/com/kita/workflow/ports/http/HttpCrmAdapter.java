package com.kita.workflow.ports.http;

import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.CrmPort;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link CrmPort}: an HTTP client to crm-service ({@code GET /api/crm/customers/{id}}). Selected
 * by {@code workflow.crm.adapter=http}.
 */
@Component
@ConditionalOnProperty(name = "workflow.crm.adapter", havingValue = "http")
public class HttpCrmAdapter implements CrmPort {

  private final RestClient client;

  public HttpCrmAdapter(
      RestClient.Builder builder,
      @Value("${workflow.crm.base-url:http://crm-service:8086}") String baseUrl) {
    this.client = builder.baseUrl(baseUrl).build();
  }

  @Override
  public boolean customerActive(String customerId) {
    return client
        .get()
        .uri("/api/crm/customers/{id}", customerId)
        .exchange(
            (request, response) -> {
              HttpStatusCode status = response.getStatusCode();
              if (status.is5xxServerError()) {
                throw new TransientDownstreamException("crm-service " + status.value());
              }
              return status.is2xxSuccessful();
            });
  }

  @Override
  public String createCustomer(CustomerInput input) {
    Map<?, ?> body =
        client
            .post()
            .uri("/api/crm/customers")
            .body(input)
            .exchange(
                (request, response) -> {
                  throwIfNotOk(response.getStatusCode(), "create customer");
                  return response.bodyTo(Map.class);
                });
    return String.valueOf(body.get("customerId"));
  }

  @Override
  public void updateCustomer(String customerId, CustomerInput input) {
    client
        .patch()
        .uri("/api/crm/customers/{id}", customerId)
        .body(input)
        .exchange(
            (request, response) -> {
              throwIfNotOk(response.getStatusCode(), "update customer");
              return null;
            });
  }

  private void throwIfNotOk(HttpStatusCode status, String what) {
    if (status.is5xxServerError()) {
      throw new TransientDownstreamException("crm-service " + status.value() + " on " + what);
    }
    if (!status.is2xxSuccessful()) {
      throw new ValidationException("crm-service rejected " + what + ": " + status.value());
    }
  }
}
