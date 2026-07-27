package com.kita.workflow.ports.http;

import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.ports.CrmPort;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link CrmPort}: an HTTP client to crm-service. Selected by {@code workflow.crm.adapter=http}.
 * All calls go through {@link RemoteCall} (retry, idempotency key, actor forwarding). Corrected to the
 * receiver's real contract (018): create requires a <em>derived</em> {@code customerCode} and a
 * {@code type}; update carries a {@code status} enum rather than an {@code active} flag; the response
 * id is read from {@code id} (not {@code customerId}).
 */
@Component
@ConditionalOnProperty(name = "workflow.crm.adapter", havingValue = "http")
public class HttpCrmAdapter implements CrmPort {

  /** Back-office customer maintenance creates person records unless CRM is used directly. */
  private static final String DEFAULT_CUSTOMER_TYPE = "INDIVIDUAL";

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
    Map<String, Object> body = new HashMap<>();
    body.put("customerCode", DerivedValues.customerCode(input.name())); // derived (FR-002)
    body.put("type", DEFAULT_CUSTOMER_TYPE); // derived record type — no staff member supplies it
    body.put("name", input.name());
    Map<?, ?> resp =
        remote.write(
            client,
            HttpMethod.POST,
            "/api/crm/customers",
            body,
            response -> remote.applied(response, Map.class));
    return String.valueOf(resp.get("id"));
  }

  @Override
  public void updateCustomer(String customerId, CustomerInput input) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", input.name());
    body.put("status", input.active() ? "ACTIVE" : "INACTIVE"); // crm uses a status enum
    remote.write(
        client,
        HttpMethod.PATCH,
        "/api/crm/customers/" + customerId,
        body,
        response -> remote.applied(response, Void.class));
  }
}
