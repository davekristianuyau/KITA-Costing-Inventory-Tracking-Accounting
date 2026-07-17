package com.kita.workflow.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.workflow.common.ValidationException;
import com.kita.workflow.pending.InMemoryPendingReviewStore;
import com.kita.workflow.ports.OperationsPort;
import com.kita.workflow.ports.fake.InMemoryCrmAdapter;
import com.kita.workflow.ports.fake.InMemoryOperationsAdapter;
import com.kita.workflow.workflow.SalesOrderWorkflow.DraftRequest;
import com.kita.workflow.workflow.SalesOrderWorkflow.DraftResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the sales-order lifecycle (FR-004, SC-001/005/006/009) — fakes, no DB. */
class SalesOrderWorkflowTest {

  private final InMemoryOperationsAdapter ops = new InMemoryOperationsAdapter();
  private final InMemoryCrmAdapter crm = new InMemoryCrmAdapter();
  private final InMemoryPendingReviewStore pending = new InMemoryPendingReviewStore();
  private final SalesOrderWorkflow workflow = new SalesOrderWorkflow(ops, crm, pending);

  @BeforeEach
  void seed() {
    crm.seed("cust-1");
    ops.seedStock("item-a", new BigDecimal("100"));
  }

  private static OperationsPort.SalesLine line(String qty, String price) {
    return new OperationsPort.SalesLine("item-a", new BigDecimal(qty), new BigDecimal(price));
  }

  private DraftResult draftTen() {
    return workflow.draft("emp-sales", new DraftRequest("cust-1", List.of(line("10", "125.00"))));
  }

  @Test
  void fullLifecycleEachTransitionAdvances() {
    DraftResult d = draftTen();
    assertThat(d.state()).isEqualTo("DRAFT");
    assertThat(d.reservedAll()).isTrue();
    assertThat(ops.availableQty("item-a")).isEqualByComparingTo("90"); // 10 reserved

    assertThat(workflow.confirmPayment("emp-cashier", d.salesOrderId())).isEqualTo("PAYMENT_CONFIRMED");
    assertThat(workflow.release("emp-whse", d.salesOrderId())).isEqualTo("RELEASED");
    assertThat(workflow.complete("emp-sales", d.salesOrderId())).isEqualTo("COMPLETED");
    assertThat(pending.get(d.salesOrderId())).isEmpty(); // transient position cleared
  }

  @Test
  void selfReviewOfPaymentIsRefused() {
    DraftResult d = draftTen();
    assertThatThrownBy(() -> workflow.confirmPayment("emp-sales", d.salesOrderId()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("self-review");
  }

  @Test
  void oversellIsRejectedAndCompensatedWithNoReservation() {
    ops.seedStock("item-a", new BigDecimal("5"));
    assertThatThrownBy(
            () ->
                workflow.draft(
                    "emp-sales", new DraftRequest("cust-1", List.of(line("10", "125.00")))))
        .isInstanceOf(ValidationException.class);
    assertThat(ops.availableQty("item-a")).isEqualByComparingTo("5"); // nothing reserved (SC-006)
  }

  @Test
  void unknownCustomerIsRejected() {
    assertThatThrownBy(
            () ->
                workflow.draft("emp-sales", new DraftRequest("ghost", List.of(line("1", "10.00")))))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("customer");
  }

  @Test
  void cannotReleaseBeforePaymentConfirmed() {
    DraftResult d = draftTen();
    assertThatThrownBy(() -> workflow.release("emp-whse", d.salesOrderId()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("PAYMENT_CONFIRMED");
  }

  @Test
  void confirmingAnUnknownOrderIsRejected() {
    assertThatThrownBy(() -> workflow.confirmPayment("emp-cashier", "missing"))
        .isInstanceOf(ValidationException.class);
  }
}
