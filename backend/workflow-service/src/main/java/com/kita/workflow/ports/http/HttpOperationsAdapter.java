package com.kita.workflow.ports.http;

import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.OperationsPort;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link OperationsPort}: an HTTP client to operations-service. Selected by
 * {@code workflow.operations.adapter=http}. 5xx is transient (retryable); 4xx business rejections
 * become {@link ValidationException} (422).
 */
@Component
@ConditionalOnProperty(name = "workflow.operations.adapter", havingValue = "http")
public class HttpOperationsAdapter implements OperationsPort {

  private final RestClient client;

  public HttpOperationsAdapter(
      RestClient.Builder builder,
      @Value("${workflow.operations.base-url:http://operations-service:8083}") String baseUrl) {
    this.client = builder.baseUrl(baseUrl).build();
  }

  @Override
  public String createSalesOrder(String customerId) {
    Map<?, ?> body =
        post("/api/operations/sales-orders", Map.of("customerId", customerId), Map.class);
    return String.valueOf(body.get("salesOrderId"));
  }

  @Override
  public void addSalesOrderLine(String salesOrderId, SalesLine line) {
    post("/api/operations/sales-orders/" + salesOrderId + "/items", line, Void.class);
  }

  @Override
  public void confirmSalesOrder(String salesOrderId) {
    post("/api/operations/sales-orders/" + salesOrderId + "/confirm", null, Void.class);
  }

  @Override
  public void fulfillSalesOrder(String salesOrderId) {
    post("/api/operations/sales-orders/" + salesOrderId + "/fulfill", null, Void.class);
  }

  @Override
  public void cancelSalesOrder(String salesOrderId) {
    post("/api/operations/sales-orders/" + salesOrderId + "/cancel", null, Void.class);
  }

  @Override
  public Availability availability(String itemId) {
    return client
        .get()
        .uri("/api/operations/items/{id}/availability", itemId)
        .exchange(
            (request, response) -> {
              throwIfNotOk(response.getStatusCode(), "availability");
              return response.bodyTo(Availability.class);
            });
  }

  @Override
  public BuildResult build(String itemId, java.math.BigDecimal quantity) {
    return post(
        "/api/operations/builds",
        Map.of("itemId", itemId, "quantity", quantity),
        BuildResult.class);
  }

  private <T> T post(String uri, Object body, Class<T> type) {
    var spec = client.post().uri(uri);
    if (body != null) {
      spec.body(body);
    }
    return spec.exchange(
        (request, response) -> {
          throwIfNotOk(response.getStatusCode(), uri);
          return type == Void.class ? null : response.bodyTo(type);
        });
  }

  private void throwIfNotOk(HttpStatusCode status, String what) {
    if (status.is5xxServerError()) {
      throw new TransientDownstreamException("operations-service " + status.value() + " on " + what);
    }
    if (!status.is2xxSuccessful()) {
      throw new ValidationException("operations-service rejected " + what + ": " + status.value());
    }
  }
}
