package com.kita.workflow.ports.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.workflow.common.DownstreamUnavailableException;
import com.kita.workflow.common.RetryingCaller;
import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.ports.ProcurementPort;
import java.math.BigDecimal;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Adapter test against a stub server (in-process, no Docker): retry on 5xx then 503, idempotency key +
 * actor header propagation, and 409-treated-as-applied (FR-018, SC-010).
 */
class HttpProcurementAdapterTest {

  private MockWebServer server;
  private HttpProcurementAdapter adapter;

  private static final List<ProcurementPort.ReceiptLine> LINES =
      List.of(new ProcurementPort.ReceiptLine("item-a", new BigDecimal("40")));

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    String base = server.url("/").toString();
    base = base.substring(0, base.length() - 1); // drop trailing slash
    RemoteCall remote = new RemoteCall(new RetryingCaller(3, 0), new CallerContext(true));
    adapter = new HttpProcurementAdapter(RestClient.builder(), base, remote);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private static MockResponse json(int code, String body) {
    return new MockResponse()
        .setResponseCode(code)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }

  @Test
  void retriesTransientThenReportsUnavailable() {
    server.enqueue(json(503, ""));
    server.enqueue(json(503, ""));
    server.enqueue(json(503, ""));

    assertThatThrownBy(() -> adapter.receive("po-1", LINES))
        .isInstanceOf(DownstreamUnavailableException.class);
    assertThat(server.getRequestCount()).isEqualTo(3); // bounded retries all used
  }

  @Test
  void recoversAfterTransientAndForwardsHeaders() throws Exception {
    server.enqueue(json(503, ""));
    server.enqueue(json(201, "{\"receiptId\":\"r1\",\"poStatus\":\"PARTIALLY_RECEIVED\"}"));

    ProcurementPort.ReceiptResult result = adapter.receive("po-1", LINES);
    assertThat(result.poStatus()).isEqualTo("PARTIALLY_RECEIVED");
    assertThat(server.getRequestCount()).isEqualTo(2);

    server.takeRequest(); // first (503) attempt
    RecordedRequest retried = server.takeRequest();
    assertThat(retried.getHeader("X-Idempotency-Key")).isNotBlank();
    assertThat(retried.getHeader("X-Kita-User")).isEqualTo(CallerContext.STUB_ACTOR);
  }

  @Test
  void treats409AsAlreadyApplied() {
    server.enqueue(json(409, "{\"receiptId\":\"r1\",\"poStatus\":\"FULLY_RECEIVED\"}"));

    ProcurementPort.ReceiptResult result = adapter.receive("po-1", LINES);
    assertThat(result.poStatus()).isEqualTo("FULLY_RECEIVED"); // replay, not an error
  }

  @Test
  void keepsSameIdempotencyKeyAcrossRetries() throws Exception {
    server.enqueue(json(503, ""));
    server.enqueue(json(201, "{\"receiptId\":\"r1\",\"poStatus\":\"PARTIALLY_RECEIVED\"}"));

    adapter.receive("po-1", LINES);
    String firstKey = server.takeRequest().getHeader("X-Idempotency-Key");
    String secondKey = server.takeRequest().getHeader("X-Idempotency-Key");
    assertThat(secondKey).isEqualTo(firstKey); // same key ⇒ downstream de-dupes the replay
  }
}
