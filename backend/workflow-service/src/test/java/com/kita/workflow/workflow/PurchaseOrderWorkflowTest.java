package com.kita.workflow.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.ProcurementPort;
import com.kita.workflow.ports.fake.InMemoryProcurementAdapter;
import com.kita.workflow.workflow.PurchaseOrderWorkflow.RaiseRequest;
import com.kita.workflow.workflow.PurchaseOrderWorkflow.RaiseResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the purchase-order lifecycle + exact total (FR-007..009, FR-020) — no DB. */
class PurchaseOrderWorkflowTest {

  private final InMemoryProcurementAdapter procurement = new InMemoryProcurementAdapter();
  private final PurchaseOrderWorkflow workflow = new PurchaseOrderWorkflow(procurement);

  @BeforeEach
  void seed() {
    procurement.seedSupplier("sup-1");
  }

  private static ProcurementPort.PoLine line(String item, String qty, String cost) {
    return new ProcurementPort.PoLine(item, new BigDecimal(qty), new BigDecimal(cost));
  }

  @Test
  void raiseComputesExactHalfUpTotal() {
    // 100 × 12.34 = 1234.00 ; 3 × 12.345 = 37.035 → 37.04 (rounding edge) ; total 1271.04
    RaiseResult r =
        workflow.raise(
            "emp-proc",
            new RaiseRequest("sup-1", List.of(line("item-a", "100", "12.34"), line("item-b", "3", "12.345"))));
    assertThat(r.status()).isEqualTo("DRAFT");
    assertThat(r.total()).isEqualByComparingTo("1271.04");
    assertThat(r.purchaseOrderId()).isNotBlank();
  }

  @Test
  void unknownSupplierIsRejected() {
    assertThatThrownBy(
            () ->
                workflow.raise("emp-proc", new RaiseRequest("ghost", List.of(line("item-a", "1", "1.00")))))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("supplier");
  }

  @Test
  void emptyLinesRejected() {
    assertThatThrownBy(() -> workflow.raise("emp-proc", new RaiseRequest("sup-1", List.of())))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void approveThenSendAdvancesStatus() {
    RaiseResult r =
        workflow.raise("emp-proc", new RaiseRequest("sup-1", List.of(line("item-a", "1", "10.00"))));
    assertThat(workflow.approve("emp-approver", r.purchaseOrderId())).isEqualTo("APPROVED");
    assertThat(workflow.send("emp-proc", r.purchaseOrderId())).isEqualTo("SENT");
  }

  @Test
  void cannotSendBeforeApprove() {
    RaiseResult r =
        workflow.raise("emp-proc", new RaiseRequest("sup-1", List.of(line("item-a", "1", "10.00"))));
    assertThatThrownBy(() -> workflow.send("emp-proc", r.purchaseOrderId()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("not approved");
  }
}
