package com.kita.workflow.ports.http;

import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.OperationsPort;
import com.kita.workflow.ports.http.DerivedValues.ItemOption;
import com.kita.workflow.ports.http.DerivedValues.LocationOption;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link OperationsPort}: an HTTP client to operations-service. Selected by
 * {@code workflow.operations.adapter=http}. Calls go through {@link RemoteCall}. Corrected to the
 * receiver's real contract (018): a sales order is created atomically with its lines (no add-line
 * endpoint), items are resolved from ref (sku) to UUID, a build derives its finished-item UUID and a
 * default location, and responses are read from {@code id} (not {@code salesOrderId}).
 */
@Component
@ConditionalOnProperty(name = "workflow.operations.adapter", havingValue = "http")
public class HttpOperationsAdapter implements OperationsPort {

  private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
      new ParameterizedTypeReference<>() {};

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
  public String createSalesOrder(String customerRef, List<SalesLine> lines) {
    Map<String, UUID> skuToId = resolveSkus(lines.stream().map(SalesLine::itemId).toList());
    List<Map<String, Object>> lineBodies = new ArrayList<>();
    for (SalesLine line : lines) {
      lineBodies.add(
          Map.of(
              "itemId", skuToId.get(line.itemId().toLowerCase()),
              "quantity", line.quantity(),
              "unitPrice", line.unitPrice()));
    }
    Map<?, ?> body =
        remote.write(
            client,
            HttpMethod.POST,
            "/api/operations/sales-orders",
            Map.of("customerRef", customerRef, "lines", lineBodies),
            response -> remote.applied(response, Map.class));
    return String.valueOf(body.get("id"));
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
    UUID id = resolveSkus(List.of(itemId)).get(itemId.toLowerCase());
    // operations returns availability PER LOCATION (a list); aggregate to a single view.
    List<Map<String, Object>> levels =
        remote.get(
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
              return response.bodyTo(LIST_OF_MAPS);
            },
            id);
    BigDecimal onHand = sum(levels, "onHand");
    BigDecimal available = sum(levels, "available");
    return new Availability(itemId, onHand, available);
  }

  @Override
  public BuildResult build(String itemId, BigDecimal quantity) {
    UUID finishedItemId = resolveSkus(List.of(itemId)).get(itemId.toLowerCase());
    UUID locationId = defaultLocationId();
    Map<?, ?> body =
        remote.write(
            client,
            HttpMethod.POST,
            "/api/operations/builds",
            Map.of("finishedItemId", finishedItemId, "locationId", locationId, "quantity", quantity),
            response -> remote.applied(response, Map.class));
    return new BuildResult(String.valueOf(body.get("id")), quantity);
  }

  /** One GET /items, mapped to lowercase-sku → UUID for the requested refs (unknown ref → 422). */
  private Map<String, UUID> resolveSkus(List<String> skus) {
    List<Map<String, Object>> items =
        remote.get(
            client,
            "/api/operations/items",
            response -> {
              if (response.getStatusCode().is5xxServerError()) {
                throw new TransientDownstreamException(
                    "operations-service " + response.getStatusCode().value());
              }
              return response.bodyTo(LIST_OF_MAPS);
            });
    List<ItemOption> catalog =
        items.stream()
            .map(m -> new ItemOption(String.valueOf(m.get("sku")), UUID.fromString(String.valueOf(m.get("id")))))
            .toList();
    Map<String, UUID> resolved = new java.util.HashMap<>();
    for (String sku : skus) {
      resolved.put(sku.toLowerCase(), DerivedValues.resolveSku(sku, catalog));
    }
    return resolved;
  }

  private UUID defaultLocationId() {
    List<Map<String, Object>> locations =
        remote.get(
            client,
            "/api/operations/locations",
            response -> {
              if (response.getStatusCode().is5xxServerError()) {
                throw new TransientDownstreamException(
                    "operations-service " + response.getStatusCode().value());
              }
              return response.bodyTo(LIST_OF_MAPS);
            });
    List<LocationOption> options =
        locations.stream()
            .map(m -> new LocationOption(String.valueOf(m.get("code")), UUID.fromString(String.valueOf(m.get("id")))))
            .toList();
    return DerivedValues.defaultLocation(options);
  }

  private static BigDecimal sum(List<Map<String, Object>> levels, String field) {
    BigDecimal total = BigDecimal.ZERO;
    for (Map<String, Object> level : levels) {
      Object v = level.get(field);
      if (v != null) {
        total = total.add(new BigDecimal(String.valueOf(v)));
      }
    }
    return total;
  }

  private void post(String uri) {
    remote.write(client, HttpMethod.POST, uri, null, response -> remote.applied(response, Void.class));
  }
}
