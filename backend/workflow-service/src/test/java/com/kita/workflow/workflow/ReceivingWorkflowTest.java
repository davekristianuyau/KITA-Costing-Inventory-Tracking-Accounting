package com.kita.workflow.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.workflow.common.DownstreamUnavailableException;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.pending.InMemoryPendingReviewStore;
import com.kita.workflow.ports.ProcurementPort;
import com.kita.workflow.ports.fake.InMemoryProcurementAdapter;
import com.kita.workflow.workflow.ReceivingWorkflow.RecordRequest;
import com.kita.workflow.workflow.ReceivingWorkflow.RecordResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure unit tests for receiving under maker–checker (FR-010/011/016/021, SC-006/009) — no DB. */
class ReceivingWorkflowTest {

  private final InMemoryProcurementAdapter procurement = new InMemoryProcurementAdapter();
  private final InMemoryPendingReviewStore pending = new InMemoryPendingReviewStore();
  private final ReceivingWorkflow workflow = new ReceivingWorkflow(procurement, pending);

  private String poId;

  @BeforeEach
  void seed() {
    poId = procurement.seedSentPurchaseOrder("sup-1", Map.of("item-a", new BigDecimal("100")));
  }

  private static ProcurementPort.ReceiptLine line(String qty) {
    return new ProcurementPort.ReceiptLine("item-a", new BigDecimal(qty));
  }

  private RecordResult recordForty() {
    return workflow.record("emp-whse", new RecordRequest(poId, List.of(line("40"))));
  }

  @Test
  void recordIsTransientWithNoDownstreamWrite() {
    RecordResult r = recordForty();
    assertThat(r.state()).isEqualTo("PENDING_REVIEW");
    assertThat(procurement.statusOf(poId)).isEqualTo("SENT"); // nothing applied yet
    assertThat(procurement.receivedQty(poId, "item-a")).isEqualByComparingTo("0");
  }

  @Test
  void distinctCheckerConfirmsPartialThenFull() {
    RecordResult first = recordForty();
    ProcurementPort.ReceiptResult r1 = workflow.confirm("emp-whse-mgr", first.pendingReceiptId());
    assertThat(r1.poStatus()).isEqualTo("PARTIALLY_RECEIVED");
    assertThat(procurement.receivedQty(poId, "item-a")).isEqualByComparingTo("40");

    RecordResult second =
        workflow.record("emp-whse", new RecordRequest(poId, List.of(line("60"))));
    ProcurementPort.ReceiptResult r2 = workflow.confirm("emp-whse-mgr", second.pendingReceiptId());
    assertThat(r2.poStatus()).isEqualTo("FULLY_RECEIVED");
    assertThat(procurement.receivedQty(poId, "item-a")).isEqualByComparingTo("100");
  }

  @Test
  void selfReviewIsRefused() {
    RecordResult r = recordForty();
    assertThatThrownBy(() -> workflow.confirm("emp-whse", r.pendingReceiptId()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("self-review");
    assertThat(procurement.statusOf(poId)).isEqualTo("SENT"); // no effect
  }

  @Test
  void overReceiptIsRefusedWithNoChange() {
    RecordResult r = workflow.record("emp-whse", new RecordRequest(poId, List.of(line("140"))));
    assertThatThrownBy(() -> workflow.confirm("emp-whse-mgr", r.pendingReceiptId()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("over-receipt");
    assertThat(procurement.statusOf(poId)).isEqualTo("SENT");
    assertThat(procurement.receivedQty(poId, "item-a")).isEqualByComparingTo("0");
  }

  @Test
  void inventoryUpdateFailureRejectsWholeReceiptPoUnchanged() {
    RecordResult r = recordForty();
    procurement.failNextReceive();
    assertThatThrownBy(() -> workflow.confirm("emp-whse-mgr", r.pendingReceiptId()))
        .isInstanceOf(DownstreamUnavailableException.class);
    assertThat(procurement.statusOf(poId)).isEqualTo("SENT"); // no half-applied delivery (US4 AC4)
    assertThat(procurement.receivedQty(poId, "item-a")).isEqualByComparingTo("0");
  }

  @Test
  void confirmTimeRevalidationRejectsInvalidReferences() {
    // Record against a PO the procurement service does not know (became invalid since recording).
    RecordResult r =
        workflow.record("emp-whse", new RecordRequest("ghost-po", List.of(line("10"))));
    assertThatThrownBy(() -> workflow.confirm("emp-whse-mgr", r.pendingReceiptId()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void confirmingUnknownPendingReceiptIsRejected() {
    assertThatThrownBy(() -> workflow.confirm("emp-whse-mgr", "missing"))
        .isInstanceOf(ValidationException.class);
  }
}
