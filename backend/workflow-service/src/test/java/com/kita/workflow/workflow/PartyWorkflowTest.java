package com.kita.workflow.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.workflow.ports.CrmPort;
import com.kita.workflow.ports.ProcurementPort;
import com.kita.workflow.ports.fake.InMemoryCrmAdapter;
import com.kita.workflow.ports.fake.InMemoryProcurementAdapter;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit tests for party maintenance (FR-014/015, SC-008) — fakes, no DB. */
class PartyWorkflowTest {

  private final InMemoryCrmAdapter crm = new InMemoryCrmAdapter();
  private final InMemoryProcurementAdapter procurement = new InMemoryProcurementAdapter();
  private final PartyWorkflow workflow = new PartyWorkflow(crm, procurement);

  @Test
  void createdCustomerIsImmediatelyUsable() {
    String id = workflow.createCustomer("emp-crm", new CrmPort.CustomerInput("Acme", true));
    assertThat(crm.customerActive(id)).isTrue(); // usable in an order the same session (SC-008)
  }

  @Test
  void updateCanDeactivateCustomer() {
    String id = workflow.createCustomer("emp-crm", new CrmPort.CustomerInput("Acme", true));
    workflow.updateCustomer("emp-crm", id, new CrmPort.CustomerInput("Acme", false));
    assertThat(crm.customerActive(id)).isFalse();
  }

  @Test
  void createdSupplierIsUsableAndSuppliedItemsAreSet() {
    String id = workflow.createSupplier("emp-proc", new ProcurementPort.SupplierInput("Supp", true));
    assertThat(procurement.supplierActive(id)).isTrue();

    workflow.setSuppliedItems(
        "emp-proc",
        id,
        List.of(new ProcurementPort.SuppliedItem("item-a", new BigDecimal("12.34"))));
    assertThat(procurement.suppliedItemsOf(id)).hasSize(1);
    assertThat(procurement.suppliedItemsOf(id).get(0).itemId()).isEqualTo("item-a");
  }
}
