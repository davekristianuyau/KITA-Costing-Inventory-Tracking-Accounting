package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.hr.employee.EmployeeResponse;
import com.kita.hr.employee.EmployeeStatus;
import com.kita.hr.employee.EmploymentType;
import com.kita.workflow.common.RetryingCaller;
import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.ports.HrPort.EmployeeView;
import com.kita.workflow.ports.http.HrPositionRoles;
import com.kita.workflow.ports.http.HttpHrAdapter;
import com.kita.workflow.ports.http.RemoteCall;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * US2 consumer contract (FR-005), response side: the adapter is driven against the JSON hr-service's
 * <em>real</em> {@link EmployeeResponse} produces. This is the drift that hurt most — the old mapping
 * read {@code active} and {@code roles}, neither of which HR has, so every actor came back inactive
 * with no roles and every governed action failed before reaching an owning service.
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

  private HttpHrAdapter adapterWith(HrPositionRoles roles) {
    String base = server.url("/").toString();
    base = base.substring(0, base.length() - 1);
    RemoteCall remote = new RemoteCall(new RetryingCaller(3, 0), new CallerContext(true));
    return new HttpHrAdapter(RestClient.builder(), base, remote, roles);
  }

  /** A real hr-service response body — built from HR's own record, not a hand-written stub. */
  private String realEmployeeJson(EmployeeStatus status, String position) {
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
            position,
            LocalDate.of(2020, 1, 1),
            null,
            status,
            null,
            null,
            null,
            null);
    return ContractSupport.toJson(response);
  }

  private void enqueue(String body) {
    server.enqueue(
        new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").setBody(body));
  }

  @Test
  void activeIsDerivedFromTheRealStatusField() {
    enqueue(realEmployeeJson(EmployeeStatus.ACTIVE, "SALES CLERK"));
    HrPositionRoles roles = new HrPositionRoles();
    roles.getPositionRoles().put("SALES CLERK", "SALES");

    Optional<EmployeeView> view = adapterWith(roles).getEmployee(employeeId.toString());

    assertThat(view).isPresent();
    assertThat(view.get().active()).isTrue(); // from status=ACTIVE, not a non-existent `active` field
    assertThat(view.get().roles()).containsExactly("SALES");
  }

  @Test
  void separatedEmployeeIsNotActive() {
    enqueue(realEmployeeJson(EmployeeStatus.SEPARATED, "SALES CLERK"));

    Optional<EmployeeView> view = adapterWith(new HrPositionRoles()).getEmployee(employeeId.toString());

    assertThat(view).isPresent();
    assertThat(view.get().active()).isFalse();
  }

  @Test
  void unmappedPositionGrantsNoRolesFailClosed() {
    enqueue(realEmployeeJson(EmployeeStatus.ACTIVE, "MYSTERY ROLE"));

    Optional<EmployeeView> view = adapterWith(new HrPositionRoles()).getEmployee(employeeId.toString());

    // HR holds no back-office roles; an unmapped position must grant nothing rather than everything.
    assertThat(view).isPresent();
    assertThat(view.get().roles()).isEmpty();
  }
}
