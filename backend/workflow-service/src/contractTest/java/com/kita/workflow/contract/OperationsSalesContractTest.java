package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.operations.api.SalesDtos.SalesOrderCreateRequest;
import com.kita.workflow.common.RetryingCaller;
import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.ports.OperationsPort.SalesLine;
import com.kita.workflow.ports.http.HttpOperationsAdapter;
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
 * US2 consumer contract (FR-005): the sales-order body the adapter puts on the wire binds to
 * operations' <em>real</em> {@link SalesOrderCreateRequest} — {@code customerRef} + atomic {@code lines}
 * with UUID {@code itemId}. If operations renames a field, or the adapter regresses to the old
 * {@code customerId}/{@code /items} shape, this fails the build (SC-003).
 */
class OperationsSalesContractTest {

  private MockWebServer server;
  private HttpOperationsAdapter adapter;
  private final UUID widgetId = UUID.randomUUID();

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    String base = server.url("/").toString();
    base = base.substring(0, base.length() - 1);
    RemoteCall remote = new RemoteCall(new RetryingCaller(3, 0), new CallerContext(true));
    adapter = new HttpOperationsAdapter(RestClient.builder(), base, remote);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private static MockResponse json(int code, String body) {
    return new MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(body);
  }

  @Test
  void createSalesOrderBodyMatchesReceiverContract() throws Exception {
    server.enqueue(json(200, "[{\"id\":\"" + widgetId + "\",\"sku\":\"WIDGET\",\"name\":\"Widget\"}]"));
    server.enqueue(json(201, "{\"id\":\"" + UUID.randomUUID() + "\",\"customerRef\":\"cust-1\",\"status\":\"DRAFT\",\"lines\":[]}"));

    adapter.createSalesOrder(
        "cust-1", List.of(new SalesLine("WIDGET", new BigDecimal("3"), new BigDecimal("125.00"))));

    server.takeRequest(); // GET /items (ref→UUID resolution)
    RecordedRequest post = server.takeRequest();
    assertThat(post.getPath()).isEqualTo("/api/operations/sales-orders"); // NOT /{id}/items
    assertThat(post.getMethod()).isEqualTo("POST");

    SalesOrderCreateRequest bound =
        ContractSupport.bindJsonAndValidate(post.getBody().readUtf8(), SalesOrderCreateRequest.class);
    assertThat(bound.customerRef()).isEqualTo("cust-1");
    assertThat(bound.lines()).hasSize(1);
    assertThat(bound.lines().get(0).itemId()).isEqualTo(widgetId); // resolved to UUID
    assertThat(bound.lines().get(0).quantity()).isEqualByComparingTo("3");
    assertThat(bound.lines().get(0).unitPrice()).isEqualByComparingTo("125.00");
  }
}
