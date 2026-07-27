package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.crm.customer.CreateCustomerRequest;
import com.kita.crm.customer.CustomerStatus;
import com.kita.crm.customer.UpdateCustomerRequest;
import com.kita.workflow.common.RetryingCaller;
import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.ports.CrmPort.CustomerInput;
import com.kita.workflow.ports.http.HttpCrmAdapter;
import com.kita.workflow.ports.http.RemoteCall;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * US2 consumer contract (FR-005/002): the customer bodies bind to crm's real
 * {@link CreateCustomerRequest} / {@link UpdateCustomerRequest} — including the <em>derived</em>
 * {@code customerCode} and {@code type} that no staff member supplies, and the {@code status} enum the
 * update takes in place of the caller's {@code active} flag.
 */
class CrmCustomerContractTest {

  private MockWebServer server;
  private HttpCrmAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    String base = server.url("/").toString();
    base = base.substring(0, base.length() - 1);
    RemoteCall remote = new RemoteCall(new RetryingCaller(3, 0), new CallerContext(true));
    adapter = new HttpCrmAdapter(RestClient.builder(), base, remote);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private static MockResponse json(int code, String body) {
    return new MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(body);
  }

  @Test
  void createCustomerDerivesCodeAndTypeAndMatchesReceiverContract() throws Exception {
    UUID id = UUID.randomUUID();
    server.enqueue(
        json(201, "{\"id\":\"" + id + "\",\"customerCode\":\"JUAN-DELA-CRUZ\",\"type\":\"INDIVIDUAL\",\"name\":\"Juan Dela Cruz\",\"status\":\"ACTIVE\"}"));

    String created = adapter.createCustomer(new CustomerInput("Juan Dela Cruz", true));

    RecordedRequest post = server.takeRequest();
    assertThat(post.getPath()).isEqualTo("/api/crm/customers");
    CreateCustomerRequest bound =
        ContractSupport.bindJsonAndValidate(post.getBody().readUtf8(), CreateCustomerRequest.class);
    assertThat(bound.customerCode()).isEqualTo("JUAN-DELA-CRUZ"); // derived (FR-002)
    assertThat(bound.type()).isNotNull(); // required by the receiver, derived by the caller
    assertThat(bound.name()).isEqualTo("Juan Dela Cruz");
    assertThat(created).isEqualTo(id.toString()); // read from response `id`, not `customerId`
  }

  @Test
  void updateCustomerSendsStatusEnumNotActiveFlag() throws Exception {
    server.enqueue(json(200, "{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"Acme\",\"status\":\"INACTIVE\"}"));

    adapter.updateCustomer(UUID.randomUUID().toString(), new CustomerInput("Acme", false));

    RecordedRequest patch = server.takeRequest();
    assertThat(patch.getMethod()).isEqualTo("PATCH");
    UpdateCustomerRequest bound =
        ContractSupport.bindJsonAndValidate(patch.getBody().readUtf8(), UpdateCustomerRequest.class);
    assertThat(bound.name()).isEqualTo("Acme");
    assertThat(bound.status()).isEqualTo(CustomerStatus.INACTIVE);
  }
}
