package com.kita.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.workflow.ports.fake.InMemoryProcurementAdapter;
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
 * Contract test (CI only — needs Docker) for the purchase-order endpoints: 201 raise with computed
 * total, 200 approve/send, 422 unknown supplier, 403 approve by a non-approver.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PurchaseOrderApiContractTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired TestRestTemplate rest;
  @Autowired InMemoryProcurementAdapter procurement;

  @BeforeEach
  void seed() {
    procurement.seedSupplier("sup-1");
  }

  private HttpEntity<Object> as(String employeeId, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Kita-User", employeeId);
    return new HttpEntity<>(body, headers);
  }

  private Map<String, Object> poBody() {
    return Map.of(
        "supplierId",
        "sup-1",
        "lines",
        List.of(Map.of("itemId", "item-a", "quantity", "100", "unitCost", "12.34")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void raiseApproveSend() {
    ResponseEntity<Map> raise =
        rest.postForEntity("/api/workflow/purchase-orders", as("emp-proc", poBody()), Map.class);
    assertThat(raise.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(raise.getBody()).containsEntry("status", "DRAFT");
    assertThat(String.valueOf(raise.getBody().get("total"))).isEqualTo("1234.00");
    String id = String.valueOf(raise.getBody().get("purchaseOrderId"));

    ResponseEntity<Map> approve =
        rest.postForEntity(
            "/api/workflow/purchase-orders/" + id + "/approve", as("emp-approver", null), Map.class);
    assertThat(approve.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(approve.getBody()).containsEntry("status", "APPROVED");

    ResponseEntity<Map> send =
        rest.postForEntity(
            "/api/workflow/purchase-orders/" + id + "/send", as("emp-proc", null), Map.class);
    assertThat(send.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(send.getBody()).containsEntry("status", "SENT");
  }

  @Test
  void approveByNonApproverIs403() {
    ResponseEntity<Map> raise =
        rest.postForEntity("/api/workflow/purchase-orders", as("emp-proc", poBody()), Map.class);
    String id = String.valueOf(raise.getBody().get("purchaseOrderId"));
    ResponseEntity<Map> approve =
        rest.postForEntity(
            "/api/workflow/purchase-orders/" + id + "/approve", as("emp-proc", null), Map.class);
    assertThat(approve.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void unknownSupplierIs422() {
    Map<String, Object> body =
        Map.of(
            "supplierId",
            "ghost",
            "lines",
            List.of(Map.of("itemId", "item-a", "quantity", "1", "unitCost", "1.00")));
    ResponseEntity<Map> raise =
        rest.postForEntity("/api/workflow/purchase-orders", as("emp-proc", body), Map.class);
    assertThat(raise.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
