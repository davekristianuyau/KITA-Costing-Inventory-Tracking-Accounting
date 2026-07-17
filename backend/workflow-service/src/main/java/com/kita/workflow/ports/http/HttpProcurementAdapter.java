package com.kita.workflow.ports.http;

import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.ProcurementPort;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link ProcurementPort}: an HTTP client to procurement-service. Selected by
 * {@code workflow.procurement.adapter=http}. 5xx is transient (retryable); other 4xx (e.g. an
 * over-threshold approval or unknown supplier) become {@link ValidationException} (422).
 */
@Component
@ConditionalOnProperty(name = "workflow.procurement.adapter", havingValue = "http")
public class HttpProcurementAdapter implements ProcurementPort {

  private final RestClient client;

  public HttpProcurementAdapter(
      RestClient.Builder builder,
      @Value("${workflow.procurement.base-url:http://procurement-service:8087}") String baseUrl) {
    this.client = builder.baseUrl(baseUrl).build();
  }

  @Override
  public boolean supplierActive(String supplierId) {
    return client
        .get()
        .uri("/api/procurement/suppliers/{id}", supplierId)
        .exchange(
            (request, response) -> {
              HttpStatusCode status = response.getStatusCode();
              if (status.is5xxServerError()) {
                throw new TransientDownstreamException("procurement-service " + status.value());
              }
              return status.is2xxSuccessful();
            });
  }

  @Override
  public String createPurchaseOrder(String supplierId, List<PoLine> lines) {
    Map<?, ?> body =
        post(
            "/api/procurement/purchase-orders",
            Map.of("supplierId", supplierId, "lines", lines),
            Map.class);
    return String.valueOf(body.get("purchaseOrderId"));
  }

  @Override
  public void approve(String purchaseOrderId) {
    post("/api/procurement/purchase-orders/" + purchaseOrderId + "/approve", null, Void.class);
  }

  @Override
  public void send(String purchaseOrderId) {
    post("/api/procurement/purchase-orders/" + purchaseOrderId + "/send", null, Void.class);
  }

  @Override
  public ReceiptResult receive(String purchaseOrderId, List<ReceiptLine> lines) {
    Map<?, ?> body =
        post(
            "/api/procurement/purchase-orders/" + purchaseOrderId + "/receipts",
            Map.of("lines", lines),
            Map.class);
    return new ReceiptResult(
        String.valueOf(body.get("receiptId")), String.valueOf(body.get("poStatus")));
  }

  @Override
  public String createSupplier(SupplierInput input) {
    Map<?, ?> body = post("/api/procurement/suppliers", input, Map.class);
    return String.valueOf(body.get("supplierId"));
  }

  @Override
  public void updateSupplier(String supplierId, SupplierInput input) {
    client
        .patch()
        .uri("/api/procurement/suppliers/{id}", supplierId)
        .body(input)
        .exchange(
            (request, response) -> {
              throwIfNotOk(response.getStatusCode(), "update supplier");
              return null;
            });
  }

  @Override
  public void setSuppliedItems(String supplierId, List<SuppliedItem> items) {
    client
        .put()
        .uri("/api/procurement/suppliers/{id}/items", supplierId)
        .body(Map.of("items", items))
        .exchange(
            (request, response) -> {
              throwIfNotOk(response.getStatusCode(), "set supplied items");
              return null;
            });
  }

  private void throwIfNotOk(HttpStatusCode status, String what) {
    if (status.is5xxServerError()) {
      throw new TransientDownstreamException("procurement-service " + status.value() + " on " + what);
    }
    if (!status.is2xxSuccessful()) {
      throw new ValidationException("procurement-service rejected " + what + ": " + status.value());
    }
  }

  private <T> T post(String uri, Object body, Class<T> type) {
    var spec = client.post().uri(uri);
    if (body != null) {
      spec.body(body);
    }
    return spec.exchange(
        (request, response) -> {
          HttpStatusCode status = response.getStatusCode();
          if (status.is5xxServerError()) {
            throw new TransientDownstreamException("procurement-service " + status.value() + " on " + uri);
          }
          if (!status.is2xxSuccessful()) {
            throw new ValidationException("procurement-service rejected " + uri + ": " + status.value());
          }
          return type == Void.class ? null : response.bodyTo(type);
        });
  }
}
