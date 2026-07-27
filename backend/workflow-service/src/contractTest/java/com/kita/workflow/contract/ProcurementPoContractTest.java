package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.kita.workflow.common.RetryingCaller;
import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.ports.ProcurementPort.PoLine;
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
 * US2 consumer contract (FR-005): the create-PO body binds to procurement's real
 * {@link CreatePurchaseOrderRequest} — {@code supplierId} (UUID) + {@code lines[itemRef,qtyOrdered,
 * agreedPrice]}. Regressing to the old {@code {itemId,quantity,unitCost}} shape fails the build.
 */
class ProcurementPoContractTest {

  private MockWebServer server;
  private HttpProcurementAdapter adapter;
  private final UUID supplierId = UUID.randomUUID();

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
  void createPoBodyMatchesReceiverContract() throws Exception {
    server.enqueue(
        json(201, "{\"id\":\"" + UUID.randomUUID() + "\",\"poNo\":\"PO-1\",\"supplierId\":\"" + supplierId + "\",\"status\":\"DRAFT\"}"));

    String poId =
        adapter.createPurchaseOrder(
            supplierId.toString(),
            List.of(new PoLine("WIDGET", new BigDecimal("12"), new BigDecimal("4.50"))));

    RecordedRequest post = server.takeRequest();
    assertThat(post.getPath()).isEqualTo("/api/procurement/purchase-orders");
    CreatePurchaseOrderRequest bound =
        ContractSupport.bindJsonAndValidate(post.getBody().readUtf8(), CreatePurchaseOrderRequest.class);
    assertThat(bound.supplierId()).isEqualTo(supplierId);
    assertThat(bound.lines()).hasSize(1);
    assertThat(bound.lines().get(0).itemRef()).isEqualTo("WIDGET");
    assertThat(bound.lines().get(0).qtyOrdered()).isEqualByComparingTo("12");
    assertThat(bound.lines().get(0).agreedPrice()).isEqualByComparingTo("4.50");
    assertThat(poId).isNotBlank(); // read from response `id`
  }
}
