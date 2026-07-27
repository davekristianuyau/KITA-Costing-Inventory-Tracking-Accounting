package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.operations.api.BuildDtos.BuildRequest;
import com.kita.workflow.common.RetryingCaller;
import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.ports.http.HttpOperationsAdapter;
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
 * US2 consumer contract (FR-005/002): the build body binds to operations' real {@link BuildRequest} —
 * {@code finishedItemId} (resolved from ref) and a {@code locationId} the caller <em>derives</em> (the
 * staff member supplies neither UUID). Regressing to the old {@code {itemId,quantity}} shape fails here.
 */
class OperationsBuildContractTest {

  private MockWebServer server;
  private HttpOperationsAdapter adapter;
  private final UUID chairId = UUID.randomUUID();
  private final UUID mainLocationId = UUID.randomUUID();

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
  void buildBodyMatchesReceiverContractWithDerivedLocation() throws Exception {
    server.enqueue(json(200, "[{\"id\":\"" + chairId + "\",\"sku\":\"CHAIR\",\"name\":\"Chair\"}]"));
    server.enqueue(json(200, "[{\"id\":\"" + mainLocationId + "\",\"code\":\"MAIN\",\"name\":\"Main\"}]"));
    server.enqueue(json(201, "{\"id\":\"" + UUID.randomUUID() + "\",\"finishedItemId\":\"" + chairId + "\",\"quantity\":5,\"status\":\"DONE\"}"));

    adapter.build("CHAIR", new BigDecimal("5"));

    server.takeRequest(); // GET /items
    server.takeRequest(); // GET /locations (derive default)
    RecordedRequest post = server.takeRequest();
    assertThat(post.getPath()).isEqualTo("/api/operations/builds");

    BuildRequest bound =
        ContractSupport.bindJsonAndValidate(post.getBody().readUtf8(), BuildRequest.class);
    assertThat(bound.finishedItemId()).isEqualTo(chairId);
    assertThat(bound.locationId()).isEqualTo(mainLocationId); // derived, not supplied
    assertThat(bound.quantity()).isEqualByComparingTo("5");
  }
}
