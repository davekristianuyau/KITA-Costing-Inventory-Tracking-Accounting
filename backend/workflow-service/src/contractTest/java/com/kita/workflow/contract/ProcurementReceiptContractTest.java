package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.procurement.receiving.dto.RecordReceiptRequest;
import com.kita.workflow.common.RetryingCaller;
import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.ports.ProcurementPort.ReceiptLine;
import com.kita.workflow.ports.ProcurementPort.ReceiptResult;
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
 * US2 consumer contract (FR-005): the receipt body binds to procurement's real
 * {@link RecordReceiptRequest} — {@code lines[itemRef,qtyReceived]} — and the response is read from the
 * real {@code id}/{@code orderStatus} keys (not {@code receiptId}/{@code poStatus}).
 */
class ProcurementReceiptContractTest {

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
  void receiptBodyMatchesReceiverContractAndReadsRealResponseKeys() throws Exception {
    server.enqueue(
        json(201, "{\"id\":\"" + UUID.randomUUID() + "\",\"purchaseOrderId\":\"" + UUID.randomUUID()
            + "\",\"orderStatus\":\"PARTIALLY_RECEIVED\",\"postedToOperations\":true,\"lines\":[]}"));

    ReceiptResult result =
        adapter.receive(
            UUID.randomUUID().toString(),
            List.of(new ReceiptLine("WIDGET", new BigDecimal("5"))));

    RecordedRequest post = server.takeRequest();
    RecordReceiptRequest bound =
        ContractSupport.bindJsonAndValidate(post.getBody().readUtf8(), RecordReceiptRequest.class);
    assertThat(bound.lines()).hasSize(1);
    assertThat(bound.lines().get(0).itemRef()).isEqualTo("WIDGET");
    assertThat(bound.lines().get(0).qtyReceived()).isEqualByComparingTo("5");
    assertThat(result.poStatus()).isEqualTo("PARTIALLY_RECEIVED"); // from response `orderStatus`
  }
}
