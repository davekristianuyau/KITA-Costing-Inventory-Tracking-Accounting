package com.kita.workflow.ports.fake;

import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.ProcurementPort;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link ProcurementPort} for isolated build/test. Enforces the PO lifecycle order (send
 * requires an approved PO) so state transitions are exercised for real. Seed suppliers with
 * {@link #seedSupplier}.
 */
@Component
@ConditionalOnProperty(
    name = "workflow.procurement.adapter",
    havingValue = "fake",
    matchIfMissing = true)
public class InMemoryProcurementAdapter implements ProcurementPort {

  private enum Status {
    DRAFT,
    APPROVED,
    SENT
  }

  private static final class Po {
    private Status status = Status.DRAFT;
  }

  private final java.util.Set<String> activeSuppliers = ConcurrentHashMap.newKeySet();
  private final ConcurrentMap<String, Po> orders = new ConcurrentHashMap<>();

  @Override
  public boolean supplierActive(String supplierId) {
    return activeSuppliers.contains(supplierId);
  }

  @Override
  public String createPurchaseOrder(String supplierId, List<PoLine> lines) {
    String id = UUID.randomUUID().toString();
    orders.put(id, new Po());
    return id;
  }

  @Override
  public void approve(String purchaseOrderId) {
    po(purchaseOrderId).status = Status.APPROVED;
  }

  @Override
  public void send(String purchaseOrderId) {
    Po po = po(purchaseOrderId);
    if (po.status != Status.APPROVED) {
      throw new ValidationException("cannot send PO " + purchaseOrderId + ": not approved");
    }
    po.status = Status.SENT;
  }

  // --- test seams -------------------------------------------------------------------------------

  public void seedSupplier(String supplierId) {
    activeSuppliers.add(supplierId);
  }

  public String statusOf(String purchaseOrderId) {
    return po(purchaseOrderId).status.name();
  }

  public void reset() {
    activeSuppliers.clear();
    orders.clear();
  }

  private Po po(String purchaseOrderId) {
    Po po = orders.get(purchaseOrderId);
    if (po == null) {
      throw new ValidationException("unknown purchase order " + purchaseOrderId);
    }
    return po;
  }
}
