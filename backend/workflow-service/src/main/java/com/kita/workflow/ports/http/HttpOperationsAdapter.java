package com.kita.workflow.ports.http;

import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.OperationsPort;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link OperationsPort}: an HTTP client to operations-service. Selected by
 * {@code workflow.operations.adapter=http}. All calls go through {@link RemoteCall}.
 */
@Component
@ConditionalOnProperty(name = "workflow.operations.adapter", havingValue = "http")
public class HttpOperationsAdapter implements OperationsPort {

  private final RestClient client;
  private final RemoteCall remote;

  public HttpOperationsAdapter(
      RestClient.Builder builder,
      @Value("${workflow.operations.base-url:http://operations-service:8083}") String baseUrl,
      RemoteCall remote) {
    this.client = builder.baseUrl(baseUrl).build();
    this.remote = remote;
  }

  @Override
  public String createSalesOrder(String customerId) {
    Map<?, ?> body =
        remote.write(
            client,
            HttpMethod.POST,
            "/api/operations/sales-orders",
            Map.of("customerId", customerId),
            response -> remote.applied(response, Map.class));
    return String.valueOf(body.get("salesOrderId"));
  }

  @Override
  public void addSalesOrderLine(String salesOrderId, SalesLine line) {
    remote.write(
        client,
        HttpMethod.POST,
        "/api/operations/sales-orders/" + salesOrderId + "/items",
        line,
        response -> remote.applied(response, Void.class));
  }

  @Override
  public void confirmSalesOrder(String salesOrderId) {
    post("/api/operations/sales-orders/" + salesOrderId + "/confirm");
  }

  @Override
  public void fulfillSalesOrder(String salesOrderId) {
    post("/api/operations/sales-orders/" + salesOrderId + "/fulfill");
  }

  @Override
  public void cancelSalesOrder(String salesOrderId) {
    post("/api/operations/sales-orders/" + salesOrderId + "/cancel");
  }

  @Override
  public Availability availability(String itemId) {
    return remote.get(
        client,
        "/api/operations/items/{id}/availability",
        response -> {
          var status = response.getStatusCode();
          if (status.is5xxServerError()) {
            throw new TransientDownstreamException("operations-service " + status.value());
          }
          if (!status.is2xxSuccessful()) {
            throw new ValidationException("operations-service availability: " + status.value());
          }
          return response.bodyTo(Availability.class);
        },
        itemId);
  }

  @Override
  public BuildResult build(String itemId, BigDecimal quantity) {
    return remote.write(
        client,
        HttpMethod.POST,
        "/api/operations/builds",
        Map.of("itemId", itemId, "quantity", quantity),
        response -> remote.applied(response, BuildResult.class));
  }

  private void post(String uri) {
    remote.write(client, HttpMethod.POST, uri, null, response -> remote.applied(response, Void.class));
  }
}
