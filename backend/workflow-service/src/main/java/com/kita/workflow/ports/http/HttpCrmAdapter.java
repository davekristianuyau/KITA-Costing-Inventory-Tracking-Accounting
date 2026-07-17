package com.kita.workflow.ports.http;

import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.ports.CrmPort;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link CrmPort}: an HTTP client to crm-service. Selected by {@code workflow.crm.adapter=http}.
 * All calls go through {@link RemoteCall} (retry, idempotency key, actor forwarding).
 */
@Component
@ConditionalOnProperty(name = "workflow.crm.adapter", havingValue = "http")
public class HttpCrmAdapter implements CrmPort {

  private final RestClient client;
  private final RemoteCall remote;

  public HttpCrmAdapter(
      RestClient.Builder builder,
      @Value("${workflow.crm.base-url:http://crm-service:8086}") String baseUrl,
      RemoteCall remote) {
    this.client = builder.baseUrl(baseUrl).build();
    this.remote = remote;
  }

  @Override
  public boolean customerActive(String customerId) {
    return remote.get(
        client,
        "/api/crm/customers/{id}",
        response -> {
          if (response.getStatusCode().is5xxServerError()) {
            throw new TransientDownstreamException("crm-service " + response.getStatusCode().value());
          }
          return response.getStatusCode().is2xxSuccessful();
        },
        customerId);
  }

  @Override
  public String createCustomer(CustomerInput input) {
    Map<?, ?> body =
        remote.write(
            client,
            HttpMethod.POST,
            "/api/crm/customers",
            input,
            response -> remote.applied(response, Map.class));
    return String.valueOf(body.get("customerId"));
  }

  @Override
  public void updateCustomer(String customerId, CustomerInput input) {
    remote.write(
        client,
        HttpMethod.PATCH,
        "/api/crm/customers/" + customerId,
        input,
        response -> remote.applied(response, Void.class));
  }
}
