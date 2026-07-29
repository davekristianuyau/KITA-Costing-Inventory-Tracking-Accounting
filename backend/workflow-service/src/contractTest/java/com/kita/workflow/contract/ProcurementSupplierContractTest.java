package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.procurement.supplier.CreateSupplierRequest;
import com.kita.procurement.supplier.SupplierItemRequest;
import com.kita.workflow.common.RetryingCaller;
import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.ports.ProcurementPort.SuppliedItem;
import com.kita.workflow.ports.ProcurementPort.SupplierInput;
import com.kita.workflow.ports.http.HttpProcurementAdapter;
import com.kita.workflow.ports.http.RemoteCall;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * US2 consumer contract (FR-005/002): supplier create binds to {@link CreateSupplierRequest} with a
 * <em>derived</em> {@code supplierCode}; supplied items are {@code POST}ed one per item and bind to
 * {@link SupplierItemRequest} ({@code itemRef,supplierPrice}).
 */
class ProcurementSupplierContractTest {

  private MockWebServer server;
  private HttpProcurementAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    String base = server.url("/").toString();
    base = base.substring(0, base.length() - 1);
    RemoteCall remote = new RemoteCall(new RetryingCaller(3, 0), new CallerContext(true));
    adapter = new HttpProcurementAdapter(RestClient.builder(), base, remote);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private static MockResponse json(int code, String body) {
    return new MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(body);
  }

  @Test
  void createSupplierDerivesCodeAndMatchesReceiverContract() throws Exception {
    server.enqueue(
        json(201, "{\"id\":\"" + UUID.randomUUID() + "\",\"supplierCode\":\"ACME-CORP\",\"name\":\"Acme Corp.\",\"status\":\"ACTIVE\"}"));

    adapter.createSupplier(new SupplierInput("Acme Corp.", true));

    RecordedRequest post = server.takeRequest();
    assertThat(post.getPath()).isEqualTo("/api/procurement/suppliers");
    CreateSupplierRequest bound =
        ContractSupport.bindJsonAndValidate(post.getBody().readUtf8(), CreateSupplierRequest.class);
    assertThat(bound.supplierCode()).isEqualTo("ACME-CORP"); // derived from the name (FR-002)
    assertThat(bound.name()).isEqualTo("Acme Corp.");
  }

  @Test
  void suppliedItemsArePostedPerItemAndMatchReceiverContract() throws Exception {
    server.enqueue(json(201, "{\"itemRef\":\"WIDGET\",\"supplierPrice\":4.50,\"preferred\":false}"));

    adapter.setSuppliedItems(
        UUID.randomUUID().toString(), List.of(new SuppliedItem("WIDGET", new BigDecimal("4.50"))));

    RecordedRequest post = server.takeRequest();
    assertThat(post.getMethod()).isEqualTo("POST"); // per-item POST, not PUT list
    assertThat(post.getPath()).endsWith("/items");
    SupplierItemRequest bound =
        ContractSupport.bindJsonAndValidate(post.getBody().readUtf8(), SupplierItemRequest.class);
    assertThat(bound.itemRef()).isEqualTo("WIDGET");
    assertThat(bound.supplierPrice()).isEqualByComparingTo("4.50");
  }
}
