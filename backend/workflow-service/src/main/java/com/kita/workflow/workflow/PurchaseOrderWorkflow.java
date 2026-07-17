package com.kita.workflow.workflow;

import com.kita.workflow.common.Money;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.ProcurementPort;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Raises a purchase order and carries it through create → approve → send (FR-007..FR-009). The PO
 * total is computed here exactly with {@link Money} (half-up sum of line extensions, FR-020); the
 * approval threshold itself is enforced by procurement-service, and approval authority by the
 * authorizer (PROCUREMENT_APPROVER).
 */
@Component
public class PurchaseOrderWorkflow {

  static final String DRAFT = "DRAFT";
  static final String APPROVED = "APPROVED";
  static final String SENT = "SENT";

  private final ProcurementPort procurement;

  public PurchaseOrderWorkflow(ProcurementPort procurement) {
    this.procurement = procurement;
  }

  public record RaiseRequest(String supplierId, List<ProcurementPort.PoLine> lines) {}

  public record RaiseResult(String purchaseOrderId, String status, BigDecimal total) {}

  /** Create a DRAFT purchase order with computed totals, attributed to {@code actorEmployeeId}. */
  public RaiseResult raise(String actorEmployeeId, RaiseRequest request) {
    if (!procurement.supplierActive(request.supplierId())) {
      throw new ValidationException("unknown or inactive supplier: " + request.supplierId());
    }
    if (request.lines() == null || request.lines().isEmpty()) {
      throw new ValidationException("a purchase order needs at least one line");
    }
    BigDecimal total =
        Money.sum(
            request.lines().stream()
                .map(l -> Money.extend(l.quantity(), l.unitCost()))
                .toList());
    String poId = procurement.createPurchaseOrder(request.supplierId(), request.lines());
    return new RaiseResult(poId, DRAFT, total);
  }

  public String approve(String actorEmployeeId, String purchaseOrderId) {
    procurement.approve(purchaseOrderId);
    return APPROVED;
  }

  public String send(String actorEmployeeId, String purchaseOrderId) {
    procurement.send(purchaseOrderId);
    return SENT;
  }
}
