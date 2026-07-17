package com.kita.workflow.ports.fake;

import com.kita.workflow.common.DownstreamUnavailableException;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.ProcurementPort;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link ProcurementPort} for isolated build/test. Enforces the PO lifecycle order (send
 * requires an approved PO; receive requires a sent PO) and rejects over-receipt, so US3/US4 behaviour
 * is exercised for real. Seed suppliers with {@link #seedSupplier}.
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
    SENT,
    PARTIALLY_RECEIVED,
    FULLY_RECEIVED
  }

  private static final class Po {
    private Status status = Status.DRAFT;
    private final Map<String, BigDecimal> ordered = new HashMap<>();
    private final Map<String, BigDecimal> received = new HashMap<>();
  }

  private final java.util.Set<String> activeSuppliers = ConcurrentHashMap.newKeySet();
  private final ConcurrentMap<String, Po> orders = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, List<ProcurementPort.SuppliedItem>> suppliedItems =
      new ConcurrentHashMap<>();
  private volatile boolean failNextReceive;

  @Override
  public boolean supplierActive(String supplierId) {
    return activeSuppliers.contains(supplierId);
  }

  @Override
  public String createPurchaseOrder(String supplierId, List<PoLine> lines) {
    String id = UUID.randomUUID().toString();
    Po po = new Po();
    for (PoLine line : lines) {
      po.ordered.merge(line.itemId(), line.quantity(), BigDecimal::add);
    }
    orders.put(id, po);
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

  @Override
  public synchronized ReceiptResult receive(String purchaseOrderId, List<ReceiptLine> lines) {
    Po po = po(purchaseOrderId);
    if (po.status != Status.SENT && po.status != Status.PARTIALLY_RECEIVED) {
      throw new ValidationException("cannot receive against PO " + purchaseOrderId + ": not sent");
    }
    // Validate over-receipt across all lines BEFORE applying (all-or-nothing).
    for (ReceiptLine line : lines) {
      BigDecimal alreadyReceived = po.received.getOrDefault(line.itemId(), BigDecimal.ZERO);
      BigDecimal orderedQty = po.ordered.getOrDefault(line.itemId(), BigDecimal.ZERO);
      if (alreadyReceived.add(line.quantityReceived()).compareTo(orderedQty) > 0) {
        throw new ValidationException("over-receipt for item " + line.itemId());
      }
    }
    if (failNextReceive) {
      failNextReceive = false;
      // Simulate the inventory update being unavailable — nothing is applied (FR-016/US4 AC4).
      throw new DownstreamUnavailableException("inventory update unavailable");
    }
    for (ReceiptLine line : lines) {
      po.received.merge(line.itemId(), line.quantityReceived(), BigDecimal::add);
    }
    boolean full =
        po.ordered.entrySet().stream()
            .allMatch(
                e ->
                    po.received.getOrDefault(e.getKey(), BigDecimal.ZERO).compareTo(e.getValue())
                        >= 0);
    po.status = full ? Status.FULLY_RECEIVED : Status.PARTIALLY_RECEIVED;
    return new ReceiptResult(UUID.randomUUID().toString(), po.status.name());
  }

  @Override
  public String createSupplier(SupplierInput input) {
    String id = "sup-" + UUID.randomUUID();
    if (input.active()) {
      activeSuppliers.add(id);
    }
    return id;
  }

  @Override
  public void updateSupplier(String supplierId, SupplierInput input) {
    if (input.active()) {
      activeSuppliers.add(supplierId);
    } else {
      activeSuppliers.remove(supplierId);
    }
  }

  @Override
  public void setSuppliedItems(String supplierId, List<SuppliedItem> items) {
    suppliedItems.put(supplierId, List.copyOf(items));
  }

  // --- test seams -------------------------------------------------------------------------------

  public void seedSupplier(String supplierId) {
    activeSuppliers.add(supplierId);
  }

  public List<SuppliedItem> suppliedItemsOf(String supplierId) {
    return suppliedItems.getOrDefault(supplierId, List.of());
  }

  /** Drive create→approve→send and return the sent PO id, ready to receive against. */
  public String seedSentPurchaseOrder(String supplierId, Map<String, BigDecimal> orderedByItem) {
    Po po = new Po();
    po.ordered.putAll(orderedByItem);
    po.status = Status.SENT;
    String id = UUID.randomUUID().toString();
    orders.put(id, po);
    return id;
  }

  public String statusOf(String purchaseOrderId) {
    return po(purchaseOrderId).status.name();
  }

  public BigDecimal receivedQty(String purchaseOrderId, String itemId) {
    return po(purchaseOrderId).received.getOrDefault(itemId, BigDecimal.ZERO);
  }

  public void failNextReceive() {
    this.failNextReceive = true;
  }

  public void reset() {
    activeSuppliers.clear();
    orders.clear();
    suppliedItems.clear();
    failNextReceive = false;
  }

  private Po po(String purchaseOrderId) {
    Po po = orders.get(purchaseOrderId);
    if (po == null) {
      throw new ValidationException("unknown purchase order " + purchaseOrderId);
    }
    return po;
  }
}
