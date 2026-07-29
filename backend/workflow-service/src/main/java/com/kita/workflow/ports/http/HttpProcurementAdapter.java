package com.kita.workflow.ports.http;

import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.ports.ProcurementPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real {@link ProcurementPort}: an HTTP client to procurement-service. Selected by
 * {@code workflow.procurement.adapter=http}. All calls go through {@link RemoteCall}. Corrected to the
 * receiver's real contract (018): PO/receipt lines use {@code itemRef/qtyOrdered/agreedPrice} and
 * {@code itemRef/qtyReceived}; a supplier's {@code supplierCode} is <em>derived</em> from its name;
 * supplied items are {@code POST}ed one per item (not a {@code PUT} list); responses are read from
 * {@code id} (not {@code purchaseOrderId}/{@code supplierId}/{@code receiptId}).
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
    List<Map<String, Object>> lineBodies = new ArrayList<>();
    for (PoLine line : lines) {
      Map<String, Object> l = new HashMap<>();
      l.put("itemRef", line.itemId());
      l.put("qtyOrdered", line.quantity());
      if (line.unitCost() != null) {
        l.put("agreedPrice", line.unitCost()); // optional — receiver falls back to catalog price
      }
      lineBodies.add(l);
    }
    // poNo omitted (receiver generates it); origin defaults to MANUAL.
    Map<?, ?> body =
        remote.write(
            client,
            HttpMethod.POST,
            "/api/procurement/purchase-orders",
            Map.of("supplierId", supplierId, "lines", lineBodies),
            response -> remote.applied(response, Map.class));
    return String.valueOf(body.get("id"));
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
    List<Map<String, Object>> lineBodies = new ArrayList<>();
    for (ReceiptLine line : lines) {
      lineBodies.add(Map.of("itemRef", line.itemId(), "qtyReceived", line.quantityReceived()));
    }
    Map<?, ?> body =
        remote.write(
            client,
            HttpMethod.POST,
            "/api/procurement/purchase-orders/" + purchaseOrderId + "/receipts",
            Map.of("lines", lineBodies),
            response -> remote.applied(response, Map.class));
    return new ReceiptResult(
        String.valueOf(body.get("id")), String.valueOf(body.get("orderStatus")));
  }

  @Override
  public String createSupplier(SupplierInput input) {
    Map<String, Object> body = new HashMap<>();
    body.put("supplierCode", DerivedValues.supplierCode(input.name())); // derived — no human supplies it
    body.put("name", input.name());
    Map<?, ?> resp =
        remote.write(
            client,
            HttpMethod.POST,
            "/api/procurement/suppliers",
            body,
            response -> remote.applied(response, Map.class));
    return String.valueOf(resp.get("id"));
  }

  @Override
  public void updateSupplier(String supplierId, SupplierInput input) {
    write(
        HttpMethod.PATCH,
        "/api/procurement/suppliers/" + supplierId,
        Map.of("name", input.name()));
  }

  @Override
  public void setSuppliedItems(String supplierId, List<SuppliedItem> items) {
    // Real API is POST one SupplierItemRequest per item (upsert), not a single PUT list.
    for (SuppliedItem item : items) {
      write(
          HttpMethod.POST,
          "/api/procurement/suppliers/" + supplierId + "/items",
          Map.of("itemRef", item.itemId(), "supplierPrice", item.unitCost()));
    }
  }

  private void write(HttpMethod method, String uri, Object body) {
    remote.write(client, method, uri, body, response -> remote.applied(response, Void.class));
  }
}
