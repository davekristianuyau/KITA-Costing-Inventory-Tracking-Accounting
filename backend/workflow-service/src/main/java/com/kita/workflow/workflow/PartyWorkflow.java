package com.kita.workflow.workflow;

import com.kita.workflow.ports.CrmPort;
import com.kita.workflow.ports.ProcurementPort;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Customer and supplier maintenance (US6, FR-014/015). Thin delegation to the owning services — the
 * created/updated party is immediately usable by the same employee (nothing is cached here, SC-008).
 */
@Component
public class PartyWorkflow {

  private final CrmPort crm;
  private final ProcurementPort procurement;

  public PartyWorkflow(CrmPort crm, ProcurementPort procurement) {
    this.crm = crm;
    this.procurement = procurement;
  }

  public String createCustomer(String actorEmployeeId, CrmPort.CustomerInput input) {
    return crm.createCustomer(input);
  }

  public void updateCustomer(String actorEmployeeId, String customerId, CrmPort.CustomerInput input) {
    crm.updateCustomer(customerId, input);
  }

  public String createSupplier(String actorEmployeeId, ProcurementPort.SupplierInput input) {
    return procurement.createSupplier(input);
  }

  public void updateSupplier(
      String actorEmployeeId, String supplierId, ProcurementPort.SupplierInput input) {
    procurement.updateSupplier(supplierId, input);
  }

  public void setSuppliedItems(
      String actorEmployeeId, String supplierId, List<ProcurementPort.SuppliedItem> items) {
    procurement.setSuppliedItems(supplierId, items);
  }
}
