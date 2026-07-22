package com.kita.workflow.pending;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.workflow.api.PendingReviewController;
import com.kita.workflow.authorization.BackOfficeAction;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Read-only view of the transient maker-checker queue (FR-005, feature 016). The store keeps a captured
 * request payload for replay on confirm; it MUST never reach the browser.
 */
class PendingReviewQueryTest {

  private final PendingReviewStore store = new InMemoryPendingReviewStore();
  private final PendingReviewController controller = new PendingReviewController(store);

  private PendingReview receipt(String id, String maker, Instant at) {
    return new PendingReview(
        id,
        BackOfficeAction.RECORD_DELIVERY_RECEIPT,
        maker,
        "po:po-1",
        List.of("the captured request payload"),
        null,
        at);
  }

  @Test
  void listsAnItemAwaitingItsChecker() {
    store.put(receipt("pr-1", "emp-whse", Instant.parse("2026-07-22T02:00:00Z")));

    List<PendingReviewController.PendingReviewView> rows = controller.list(null);

    assertThat(rows).singleElement().satisfies(
        r -> {
          assertThat(r.pendingId()).isEqualTo("pr-1");
          assertThat(r.makerEmployeeId()).isEqualTo("emp-whse");
          assertThat(r.targetRef()).isEqualTo("po:po-1");
          assertThat(r.action()).isEqualTo(BackOfficeAction.RECORD_DELIVERY_RECEIPT);
        });
  }

  @Test
  void confirmingRemovesItFromTheQueue() {
    store.put(receipt("pr-1", "emp-whse", Instant.parse("2026-07-22T02:00:00Z")));
    store.remove("pr-1");

    assertThat(controller.list(null)).isEmpty();
  }

  @Test
  void neverSerializesTheCapturedPayload() throws Exception {
    store.put(receipt("pr-1", "emp-whse", Instant.parse("2026-07-22T02:00:00Z")));

    // findAndRegisterModules mirrors the JSR-310 handling Spring Boot's configured mapper has
    String json =
        new ObjectMapper().findAndRegisterModules().writeValueAsString(controller.list(null));

    assertThat(json).doesNotContain("payload").doesNotContain("captured request payload");
  }

  @Test
  void narrowsByAction() {
    store.put(receipt("pr-1", "emp-whse", Instant.parse("2026-07-22T02:00:00Z")));
    store.put(
        new PendingReview(
            "so-1",
            BackOfficeAction.TAKE_SALES_ORDER,
            "emp-sales",
            "sales-order:so-1",
            null,
            "PAYMENT_CONFIRMED",
            Instant.parse("2026-07-22T02:05:00Z")));

    assertThat(controller.list(BackOfficeAction.RECORD_DELIVERY_RECEIPT))
        .extracting(PendingReviewController.PendingReviewView::pendingId)
        .containsExactly("pr-1");
    assertThat(controller.list(null)).hasSize(2);
  }

  @Test
  void ordersOldestFirstAndCarriesTheSalesStage() {
    store.put(
        new PendingReview(
            "so-1",
            BackOfficeAction.TAKE_SALES_ORDER,
            "emp-sales",
            "sales-order:so-1",
            null,
            "PAYMENT_CONFIRMED",
            Instant.parse("2026-07-22T02:05:00Z")));
    store.put(receipt("pr-1", "emp-whse", Instant.parse("2026-07-22T02:00:00Z")));

    List<PendingReviewController.PendingReviewView> rows = controller.list(null);

    assertThat(rows)
        .extracting(PendingReviewController.PendingReviewView::pendingId)
        .containsExactly("pr-1", "so-1");
    assertThat(rows.get(1).stage()).isEqualTo("PAYMENT_CONFIRMED");
  }

  @Test
  void anEmptyQueueIsAnEmptyList() {
    assertThat(controller.list(null)).isEmpty();
  }
}
