package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.workflow.common.RetryingCaller;
import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.ports.OperationsPort.Availability;
import com.kita.workflow.ports.ProcurementPort.SupplierInput;
import com.kita.workflow.ports.http.HttpCrmAdapter;
import com.kita.workflow.ports.http.HttpOperationsAdapter;
import com.kita.workflow.ports.http.HttpProcurementAdapter;
import com.kita.workflow.ports.http.RemoteCall;
import java.math.BigDecimal;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * US2 consumer contract (FR-005) for the calls that carry no request body: the lifecycle transitions
 * and the existence/availability reads. There is no DTO to bind, so the contract here is the
 * <em>method + path</em> the receiver exposes (a renamed or moved endpoint must fail the build) plus
 * the response shape actually read.
 */
class LifecycleCallsContractTest {

  private MockWebServer server;
  private String base;
  private RemoteCall remote;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    base = server.url("/").toString();
    base = base.substring(0, base.length() - 1);
    remote = new RemoteCall(new RetryingCaller(3, 0), new CallerContext(true));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private HttpOperationsAdapter operations() {
    return new HttpOperationsAdapter(RestClient.builder(), base, remote);
  }

  private HttpProcurementAdapter procurement() {
    return new HttpProcurementAdapter(RestClient.builder(), base, remote);
  }

  private HttpCrmAdapter crm() {
    return new HttpCrmAdapter(RestClient.builder(), base, remote);
  }

  private void enqueue(int code, String body) {
    server.enqueue(
        new MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(body));
  }

  private static final String ORDER_JSON =
      "{\"id\":\"x\",\"customerRef\":\"c\",\"status\":\"CONFIRMED\",\"lines\":[]}";

  // --- operations sales lifecycle ----------------------------------------------------------------

  @Test
  void confirmSalesOrderHitsTheRealPath() throws Exception {
    enqueue(200, ORDER_JSON);
    String id = UUID.randomUUID().toString();
    operations().confirmSalesOrder(id);
    RecordedRequest r = server.takeRequest();
    assertThat(r.getMethod()).isEqualTo("POST");
    assertThat(r.getPath()).isEqualTo("/api/operations/sales-orders/" + id + "/confirm");
  }

  @Test
  void fulfillSalesOrderHitsTheRealPath() throws Exception {
    enqueue(200, ORDER_JSON);
    String id = UUID.randomUUID().toString();
    operations().fulfillSalesOrder(id);
    assertThat(server.takeRequest().getPath()).isEqualTo("/api/operations/sales-orders/" + id + "/fulfill");
  }

  @Test
  void cancelSalesOrderHitsTheRealPath() throws Exception {
    enqueue(200, ORDER_JSON);
    String id = UUID.randomUUID().toString();
    operations().cancelSalesOrder(id);
    assertThat(server.takeRequest().getPath()).isEqualTo("/api/operations/sales-orders/" + id + "/cancel");
  }

  @Test
  void availabilityReadsThePerLocationListOperationsReturns() throws Exception {
    UUID itemId = UUID.randomUUID();
    enqueue(200, "[{\"id\":\"" + itemId + "\",\"sku\":\"WIDGET\"}]");
    // operations returns AvailabilityResponse PER LOCATION — a list, not a single object.
    enqueue(
        200,
        "[{\"itemId\":\"" + itemId + "\",\"locationId\":\"" + UUID.randomUUID() + "\",\"onHand\":30,\"reserved\":5,\"available\":25},"
            + "{\"itemId\":\"" + itemId + "\",\"locationId\":\"" + UUID.randomUUID() + "\",\"onHand\":10,\"reserved\":0,\"available\":10}]");

    Availability availability = operations().availability("WIDGET");

    server.takeRequest(); // GET /items
    RecordedRequest r = server.takeRequest();
    assertThat(r.getPath()).isEqualTo("/api/operations/items/" + itemId + "/availability");
    assertThat(availability.onHand()).isEqualByComparingTo("40"); // aggregated across locations
    assertThat(availability.available()).isEqualByComparingTo("35");
  }

  // --- procurement lifecycle ---------------------------------------------------------------------

  @Test
  void approveAndSendHitTheRealPaths() throws Exception {
    String id = UUID.randomUUID().toString();
    enqueue(200, "{\"id\":\"" + id + "\",\"status\":\"APPROVED\"}");
    procurement().approve(id);
    assertThat(server.takeRequest().getPath()).isEqualTo("/api/procurement/purchase-orders/" + id + "/approve");

    enqueue(200, "{\"id\":\"" + id + "\",\"status\":\"SENT\"}");
    procurement().send(id);
    assertThat(server.takeRequest().getPath()).isEqualTo("/api/procurement/purchase-orders/" + id + "/send");
  }

  @Test
  void supplierActiveReadsTheRealLookupPath() throws Exception {
    String id = UUID.randomUUID().toString();
    enqueue(200, "{\"id\":\"" + id + "\",\"supplierCode\":\"ACME\",\"name\":\"Acme\",\"status\":\"ACTIVE\"}");
    assertThat(procurement().supplierActive(id)).isTrue();
    assertThat(server.takeRequest().getPath()).isEqualTo("/api/procurement/suppliers/" + id);
  }

  @Test
  void unknownSupplierIsNotActive() throws Exception {
    enqueue(404, "");
    assertThat(procurement().supplierActive(UUID.randomUUID().toString())).isFalse();
  }

  @Test
  void updateSupplierHitsThePatchPath() throws Exception {
    String id = UUID.randomUUID().toString();
    enqueue(200, "{\"id\":\"" + id + "\",\"name\":\"Acme\",\"status\":\"ACTIVE\"}");
    procurement().updateSupplier(id, new SupplierInput("Acme", true));
    RecordedRequest r = server.takeRequest();
    assertThat(r.getMethod()).isEqualTo("PATCH");
    assertThat(r.getPath()).isEqualTo("/api/procurement/suppliers/" + id);
  }

  // --- crm ---------------------------------------------------------------------------------------

  @Test
  void customerActiveReadsTheRealLookupPath() throws Exception {
    String id = UUID.randomUUID().toString();
    enqueue(200, "{\"id\":\"" + id + "\",\"name\":\"Acme\",\"status\":\"ACTIVE\"}");
    assertThat(crm().customerActive(id)).isTrue();
    assertThat(server.takeRequest().getPath()).isEqualTo("/api/crm/customers/" + id);
  }

  @Test
  void buildQuantityIsCarriedThroughToTheResult() throws Exception {
    UUID chair = UUID.randomUUID();
    enqueue(200, "[{\"id\":\"" + chair + "\",\"sku\":\"CHAIR\"}]");
    enqueue(200, "[{\"id\":\"" + UUID.randomUUID() + "\",\"code\":\"MAIN\",\"name\":\"Main\"}]");
    enqueue(201, "{\"id\":\"" + UUID.randomUUID() + "\",\"finishedItemId\":\"" + chair + "\",\"quantity\":7,\"status\":\"DONE\"}");

    assertThat(operations().build("CHAIR", new BigDecimal("7")).produced()).isEqualByComparingTo("7");
  }
}
