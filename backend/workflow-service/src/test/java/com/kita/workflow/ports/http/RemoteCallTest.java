package com.kita.workflow.ports.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.workflow.common.DownstreamUnavailableException;
import com.kita.workflow.common.ForbiddenException;
import com.kita.workflow.common.RetryingCaller;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.common.security.CallerContext;
import java.math.BigDecimal;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * The three-way downstream error taxonomy (FR-003), exercised through an adapter + stub server (no
 * Docker): a business 4xx surfaces the receiver's own reason (422), a receiver 403 is a permission
 * refusal (distinct), and a 5xx is transient (retried → unavailable). No reason must ever collapse to a
 * bare status when the receiver explained itself.
 */
class RemoteCallTest {

  private MockWebServer server;
  private HttpProcurementAdapter adapter;

  private static final List<com.kita.workflow.ports.ProcurementPort.ReceiptLine> LINES =
      List.of(new com.kita.workflow.ports.ProcurementPort.ReceiptLine("item-a", new BigDecimal("1")));

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
    return new MockResponse()
        .setResponseCode(code)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }

  @Test
  void businessRejectionSurfacesReceiverReason() {
    server.enqueue(json(422, "{\"detail\":\"unknown supplier\"}"));

    assertThatThrownBy(() -> adapter.receive("po-1", LINES))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("unknown supplier"); // not a bare "422"
  }

  @Test
  void springStyleErrorBodyIsSurfaced() {
    server.enqueue(json(400, "{\"status\":400,\"error\":\"Bad Request\",\"message\":\"qtyReceived must be > 0\"}"));

    assertThatThrownBy(() -> adapter.receive("po-1", LINES))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("qtyReceived must be > 0");
  }

  @Test
  void receiver403IsPermissionRefusalNotBusinessInvalid() {
    server.enqueue(json(403, "{\"detail\":\"WAREHOUSE role required\"}"));

    assertThatThrownBy(() -> adapter.receive("po-1", LINES))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("WAREHOUSE role required");
  }

  @Test
  void emptyBodyFallsBackToStatus() {
    server.enqueue(json(400, ""));

    assertThatThrownBy(() -> adapter.receive("po-1", LINES))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("400");
  }

  @Test
  void fiveHundredIsTransientThenUnavailable() {
    server.enqueue(json(500, ""));
    server.enqueue(json(500, ""));
    server.enqueue(json(500, ""));

    assertThatThrownBy(() -> adapter.receive("po-1", LINES))
        .isInstanceOf(DownstreamUnavailableException.class);
    assertThat(server.getRequestCount()).isEqualTo(3);
  }
}
