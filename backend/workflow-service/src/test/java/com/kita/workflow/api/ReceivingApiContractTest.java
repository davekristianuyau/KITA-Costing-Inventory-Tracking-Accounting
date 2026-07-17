package com.kita.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.workflow.ports.fake.InMemoryProcurementAdapter;
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
 * Contract test (CI only — needs Docker) for receiving: 201 record (pending), 201 confirm by a
 * distinct checker, 422 self-review, 422 over-receipt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReceivingApiContractTest {

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

  private String poId;

  @BeforeEach
  void seed() {
    poId = procurement.seedSentPurchaseOrder("sup-1", Map.of("item-a", new BigDecimal("100")));
  }

  private HttpEntity<Object> as(String employeeId, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Kita-User", employeeId);
    return new HttpEntity<>(body, headers);
  }

  private Map<String, Object> receiptBody(String qty) {
    return Map.of("lines", List.of(Map.of("itemId", "item-a", "quantityReceived", qty)));
  }

  private String recordAs(String employee, String qty) {
    ResponseEntity<Map> rec =
        rest.postForEntity(
            "/api/workflow/purchase-orders/" + poId + "/receipts",
            as(employee, receiptBody(qty)),
            Map.class);
    assertThat(rec.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return String.valueOf(rec.getBody().get("pendingReceiptId"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void recordThenConfirmByDistinctChecker() {
    String pendingId = recordAs("emp-whse", "40");
    assertThat(procurement.statusOf(poId)).isEqualTo("SENT"); // still transient before confirm

    ResponseEntity<Map> confirm =
        rest.postForEntity(
            "/api/workflow/receipts/" + pendingId + "/confirm", as("emp-whse-mgr", null), Map.class);
    assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(confirm.getBody()).containsEntry("poStatus", "PARTIALLY_RECEIVED");
  }

  @Test
  void selfReviewIs422() {
    String pendingId = recordAs("emp-whse", "40");
    ResponseEntity<Map> confirm =
        rest.postForEntity(
            "/api/workflow/receipts/" + pendingId + "/confirm", as("emp-whse", null), Map.class);
    assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void overReceiptIs422() {
    String pendingId = recordAs("emp-whse", "140");
    ResponseEntity<Map> confirm =
        rest.postForEntity(
            "/api/workflow/receipts/" + pendingId + "/confirm", as("emp-whse-mgr", null), Map.class);
    assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(procurement.statusOf(poId)).isEqualTo("SENT");
  }
}
