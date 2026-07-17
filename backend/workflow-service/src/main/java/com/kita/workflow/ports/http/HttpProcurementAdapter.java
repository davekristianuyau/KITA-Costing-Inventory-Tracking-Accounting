package com.kita.workflow.ports.http;

import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.ports.ProcurementPort;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link ProcurementPort}: an HTTP client to procurement-service. Selected by
 * {@code workflow.procurement.adapter=http}. All calls go through {@link RemoteCall}; the idempotent
 * {@code receive} relies on RemoteCall treating a 409 as already-applied (retry-safe, SC-010).
 */
@Component
@ConditionalOnProperty(name = "workflow.procurement.adapter", havingValue = "http")
public class HttpProcurementAdapter implements ProcurementPort {

  private final RestClient client;
  private final RemoteCall remote;

  public HttpProcurementAdapter(
      RestClient.Builder builder,
      @Value("${workflow.procurement.base-url:http://procurement-service:8087}") String baseUrl,
      RemoteCall remote) {
    this.client = builder.baseUrl(baseUrl).build();
    this.remote = remote;
  }

  @Override
  public boolean supplierActive(String supplierId) {
    return remote.get(
        client,
        "/api/procurement/suppliers/{id}",
        response -> {
          if (response.getStatusCode().is5xxServerError()) {
            throw new TransientDownstreamException(
                "procurement-service " + response.getStatusCode().value());
          }
          return response.getStatusCode().is2xxSuccessful();
        },
        supplierId);
  }

  @Override
  public String createPurchaseOrder(String supplierId, List<PoLine> lines) {
    Map<?, ?> body =
        remote.write(
            client,
            HttpMethod.POST,
            "/api/procurement/purchase-orders",
            Map.of("supplierId", supplierId, "lines", lines),
            response -> remote.applied(response, Map.class));
    return String.valueOf(body.get("purchaseOrderId"));
  }

  @Override
  public void approve(String purchaseOrderId) {
    write(HttpMethod.POST, "/api/procurement/purchase-orders/" + purchaseOrderId + "/approve", null);
  }

  @Override
  public void send(String purchaseOrderId) {
    write(HttpMethod.POST, "/api/procurement/purchase-orders/" + purchaseOrderId + "/send", null);
  }

  @Override
  public ReceiptResult receive(String purchaseOrderId, List<ReceiptLine> lines) {
    Map<?, ?> body =
        remote.write(
            client,
            HttpMethod.POST,
            "/api/procurement/purchase-orders/" + purchaseOrderId + "/receipts",
            Map.of("lines", lines),
            response -> remote.applied(response, Map.class));
    return new ReceiptResult(
        String.valueOf(body.get("receiptId")), String.valueOf(body.get("poStatus")));
  }

  @Override
  public String createSupplier(SupplierInput input) {
    Map<?, ?> body =
        remote.write(
            client,
            HttpMethod.POST,
            "/api/procurement/suppliers",
            input,
            response -> remote.applied(response, Map.class));
    return String.valueOf(body.get("supplierId"));
  }

  @Override
  public void updateSupplier(String supplierId, SupplierInput input) {
    write(HttpMethod.PATCH, "/api/procurement/suppliers/" + supplierId, input);
  }

  @Override
  public void setSuppliedItems(String supplierId, List<SuppliedItem> items) {
    write(HttpMethod.PUT, "/api/procurement/suppliers/" + supplierId + "/items", Map.of("items", items));
  }

  private void write(HttpMethod method, String uri, Object body) {
    remote.write(client, method, uri, body, response -> remote.applied(response, Void.class));
  }
}
