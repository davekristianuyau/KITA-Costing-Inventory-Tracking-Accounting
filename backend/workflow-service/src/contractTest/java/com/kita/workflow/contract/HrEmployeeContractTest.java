package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.hr.employee.EmployeeResponse;
import com.kita.hr.employee.EmployeeStatus;
import com.kita.hr.employee.EmploymentType;
import com.kita.workflow.common.RetryingCaller;
import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.ports.HrPort.ResolutionOutcome;
import com.kita.workflow.ports.HrPort.Status;
import com.kita.workflow.ports.http.HttpHrAdapter;
import com.kita.workflow.ports.http.RemoteCall;
import java.time.LocalDate;
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
 * US2 consumer contract (018 FR-005), response side: the adapter is driven against JSON serialised from
 * hr-service's <em>real</em> {@link EmployeeResponse}.
 *
 * <p>017 rewrote both sides of this call. hr now carries the account link and the role tokens, and the
 * caller resolves by <b>account name</b> rather than by an HR UUID it never had. This test is what
 * caught the DTO change the moment it landed — it stopped compiling — which is the guard working.
 */
class HrEmployeeContractTest {

  private MockWebServer server;
  private final UUID employeeId = UUID.randomUUID();

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private HttpHrAdapter adapter() {
    String base = server.url("/").toString();
    base = base.substring(0, base.length() - 1);
    RemoteCall remote = new RemoteCall(new RetryingCaller(3, 0), new CallerContext(true));
    return new HttpHrAdapter(RestClient.builder(), base, remote);
  }

  /** A real hr-service response body — built from HR's own record, not a hand-written stub. */
  private String realEmployeeJson(EmployeeStatus status, String account, List<String> roles) {
    EmployeeResponse response =
        new EmployeeResponse(
            employeeId,
            "EMP-001",
            "Juan",
            "Dela Cruz",
            LocalDate.of(1990, 1, 1),
            "juan@example.com",
            "0917",
            EmploymentType.REGULAR,
            "Sales Clerk",
            LocalDate.of(2020, 1, 1),
            null,
            status,
            null,
            null,
            null,
            null,
            account,
            roles);
    return ContractSupport.toJson(response);
  }

  private void enqueue(int code, String body) {
    server.enqueue(
        new MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(body));
  }

  @Test
  void resolvesByAccountAndReadsStatusAndRolesFromTheRealRecord() throws Exception {
    enqueue(200, realEmployeeJson(EmployeeStatus.ACTIVE, "alice", List.of("SALES", "CASHIER")));

    ResolutionOutcome outcome = adapter().resolve("alice");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/api/hr/employees/by-account/alice");
    assertThat(outcome.status()).isEqualTo(Status.RESOLVED);
    assertThat(outcome.employeeId()).isEqualTo(employeeId.toString());
    assertThat(outcome.roles()).containsExactlyInAnyOrder("SALES", "CASHIER");
  }

  @Test
  void a404MeansNoEmployeeLinkedNotAnError() {
    enqueue(404, "");

    ResolutionOutcome outcome = adapter().resolve("orphan");

    assertThat(outcome.status()).isEqualTo(Status.NO_EMPLOYEE_LINKED);
    assertThat(outcome.detail()).contains("orphan");
  }

  @Test
  void aNonActiveStatusResolvesButIsNotActiveAndNamesTheStatus() {
    enqueue(200, realEmployeeJson(EmployeeStatus.SEPARATED, "bob", List.of("SALES")));

    ResolutionOutcome outcome = adapter().resolve("bob");

    // 200 + non-ACTIVE must stay distinguishable from a 404 "no employee" (SC-004).
    assertThat(outcome.status()).isEqualTo(Status.EMPLOYEE_NOT_ACTIVE);
    assertThat(outcome.detail()).contains("SEPARATED");
  }

  @Test
  void anEmployeeWithNoRolesResolvesWithAnEmptyRoleSet() {
    enqueue(200, realEmployeeJson(EmployeeStatus.ACTIVE, "carol", List.of()));

    ResolutionOutcome outcome = adapter().resolve("carol");

    assertThat(outcome.status()).isEqualTo(Status.RESOLVED);
    assertThat(outcome.roles()).isEmpty();
  }

  @Test
  void anUnreachableHrFailsClosedAsUnavailable() {
    enqueue(500, "");
    enqueue(500, "");
    enqueue(500, "");

    ResolutionOutcome outcome = adapter().resolve("alice");

    // Never RESOLVED, never a business rejection — the caller must report "retry", not "denied".
    assertThat(outcome.status()).isEqualTo(Status.UNAVAILABLE);
  }
}
