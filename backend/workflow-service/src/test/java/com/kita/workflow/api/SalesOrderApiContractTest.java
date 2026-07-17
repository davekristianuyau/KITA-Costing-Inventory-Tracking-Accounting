package com.kita.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.workflow.ports.fake.InMemoryCrmAdapter;
import com.kita.workflow.ports.fake.InMemoryOperationsAdapter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Contract test (CI only — needs Docker) for the sales-order lifecycle endpoints and status taxonomy
 * (contracts/workflow-api.md): 201 draft, 200 transitions, 422 self-review/unknown-customer, 403 wrong
 * role. Uses the default fake adapters, seeded here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SalesOrderApiContractTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired TestRestTemplate rest;
  @Autowired InMemoryCrmAdapter crm;
  @Autowired InMemoryOperationsAdapter operations;

  @BeforeEach
  void seed() {
    crm.seed("cust-1");
    operations.seedStock("item-a", new BigDecimal("100"));
  }

  private HttpEntity<Object> as(String employeeId, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Kita-User", employeeId);
    return new HttpEntity<>(body, headers);
  }

  private Map<String, Object> draftBody() {
    return Map.of(
        "customerId",
        "cust-1",
        "lines",
        List.of(Map.of("itemId", "item-a", "quantity", "10", "unitPrice", "125.00")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void fullLifecycleReturnsExpectedStates() {
    ResponseEntity<Map> draft =
        rest.postForEntity("/api/workflow/sales-orders", as("emp-sales", draftBody()), Map.class);
    assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = String.valueOf(draft.getBody().get("salesOrderId"));
    assertThat(draft.getBody()).containsEntry("state", "DRAFT");

    ResponseEntity<Map> confirm =
        rest.postForEntity(
            "/api/workflow/sales-orders/" + id + "/confirm-payment",
            as("emp-cashier", null),
            Map.class);
    assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(confirm.getBody()).containsEntry("state", "PAYMENT_CONFIRMED");

    ResponseEntity<Map> release =
        rest.postForEntity(
            "/api/workflow/sales-orders/" + id + "/release", as("emp-whse", null), Map.class);
    assertThat(release.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> complete =
        rest.postForEntity(
            "/api/workflow/sales-orders/" + id + "/complete", as("emp-sales", null), Map.class);
    assertThat(complete.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(complete.getBody()).containsEntry("state", "COMPLETED");
  }

  @Test
  @SuppressWarnings("unchecked")
  void selfReviewOfPaymentIs422() {
    ResponseEntity<Map> draft =
        rest.postForEntity("/api/workflow/sales-orders", as("emp-sales", draftBody()), Map.class);
    String id = String.valueOf(draft.getBody().get("salesOrderId"));
    ResponseEntity<Map> confirm =
        rest.postForEntity(
            "/api/workflow/sales-orders/" + id + "/confirm-payment",
            as("emp-sales", null),
            Map.class);
    assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(confirm.getBody()).containsEntry("outcome", "REJECTED_INVALID");
  }

  @Test
  void wrongRoleDraftIs403() {
    ResponseEntity<Map> draft =
        rest.postForEntity("/api/workflow/sales-orders", as("emp-cashier", draftBody()), Map.class);
    assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void unknownCustomerIs422() {
    Map<String, Object> body =
        Map.of(
            "customerId",
            "ghost",
            "lines",
            List.of(Map.of("itemId", "item-a", "quantity", "1", "unitPrice", "10.00")));
    ResponseEntity<Map> draft =
        rest.postForEntity("/api/workflow/sales-orders", as("emp-sales", body), Map.class);
    assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
