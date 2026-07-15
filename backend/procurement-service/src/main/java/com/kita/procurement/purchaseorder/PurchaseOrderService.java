package com.kita.procurement.purchaseorder;

import com.kita.procurement.common.AuditWriter;
import com.kita.procurement.common.ConflictException;
import com.kita.procurement.common.ForbiddenException;
import com.kita.procurement.common.Money;
import com.kita.procurement.common.NotFoundException;
import com.kita.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.kita.procurement.supplier.Supplier;
import com.kita.procurement.supplier.SupplierItemRepository;
import com.kita.procurement.supplier.SupplierRepository;
import com.kita.procurement.supplier.SupplierStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purchase-order lifecycle: create → approve → send (+ cancel). Every transition takes a row lock
 * and re-checks the guard inside the transaction, so two concurrent callers serialise and exactly
 * one wins (no double-approve).
 */
@Service
public class PurchaseOrderService {

  private final PurchaseOrderRepository orders;
  private final PurchaseOrderLineRepository lines;
  private final SupplierRepository suppliers;
  private final SupplierItemRepository supplierItems;
  private final AuditWriter audit;
  private final BigDecimal approvalThreshold;

  public PurchaseOrderService(
      PurchaseOrderRepository orders,
      PurchaseOrderLineRepository lines,
      SupplierRepository suppliers,
      SupplierItemRepository supplierItems,
      AuditWriter audit,
      @Value("${procurement.approval.threshold:50000.00}") BigDecimal approvalThreshold) {
    this.orders = orders;
    this.lines = lines;
    this.suppliers = suppliers;
    this.supplierItems = supplierItems;
    this.audit = audit;
    this.approvalThreshold = approvalThreshold;
  }

  @Transactional
  public PurchaseOrder create(CreatePurchaseOrderRequest req, String actor) {
    Supplier supplier =
        suppliers
            .findById(req.supplierId())
            .orElseThrow(() -> new NotFoundException("supplier not found: " + req.supplierId()));
    // An inactive supplier keeps its open POs but takes no new ones (spec edge case).
    if (supplier.getStatus() != SupplierStatus.ACTIVE) {
      throw new ConflictException("supplier is " + supplier.getStatus() + " and cannot take new orders");
    }

    String poNo = req.poNo() == null || req.poNo().isBlank() ? generatePoNo() : req.poNo();
    if (orders.existsByPoNo(poNo)) {
      throw new ConflictException("po_no already exists: " + poNo);
    }
    PurchaseOrder po =
        orders.save(
            new PurchaseOrder(
                poNo,
                supplier.getId(),
                req.origin() == null ? PurchaseOrderOrigin.MANUAL : req.origin(),
                actor));

    List<BigDecimal> lineTotals = new ArrayList<>();
    for (CreatePurchaseOrderRequest.LineRequest l : req.lines()) {
      BigDecimal price = l.agreedPrice() != null ? l.agreedPrice() : catalogPrice(supplier.getId(), l.itemRef());
      PurchaseOrderLine line =
          lines.save(new PurchaseOrderLine(po.getId(), l.itemRef(), l.qtyOrdered(), price));
      lineTotals.add(line.getLineTotal());
    }
    po.setOrderTotal(Money.sum(lineTotals));
    PurchaseOrder saved = orders.save(po);
    audit.record(actor, "PO_CREATED", saved.getId().toString(), "po_no=" + poNo + " total=" + saved.getOrderTotal());
    return saved;
  }

  /**
   * Approve. Orders above the configured threshold need an authorized approver (FR-006); the caller
   * tells us whether the requester holds that role.
   */
  @Transactional
  public PurchaseOrder approve(UUID id, boolean callerIsAuthorizedApprover, String actor) {
    PurchaseOrder po = lockOrThrow(id);
    PurchaseOrderStateMachine.assertCanApprove(po.getStatus());
    if (po.getOrderTotal().compareTo(approvalThreshold) > 0 && !callerIsAuthorizedApprover) {
      throw new ForbiddenException(
          "order total " + po.getOrderTotal() + " exceeds the approval threshold "
              + approvalThreshold + " and requires an authorized approver");
    }
    po.markApproved(actor);
    PurchaseOrder saved = orders.save(po);
    audit.record(actor, "PO_APPROVED", id.toString(), "total=" + po.getOrderTotal());
    return saved;
  }

  /** Send to the supplier. Lines lock here: nothing about them may change afterwards (FR-007). */
  @Transactional
  public PurchaseOrder send(UUID id, String actor) {
    PurchaseOrder po = lockOrThrow(id);
    PurchaseOrderStateMachine.assertCanSend(po.getStatus());
    po.markSent();
    PurchaseOrder saved = orders.save(po);
    audit.record(actor, "PO_SENT", id.toString(), null);
    return saved;
  }

  /** Cancel before any receipt. Has no inventory effect — nothing was ever posted (FR-008). */
  @Transactional
  public PurchaseOrder cancel(UUID id, String actor) {
    PurchaseOrder po = lockOrThrow(id);
    PurchaseOrderStateMachine.assertCanCancel(po.getStatus());
    po.setStatus(PurchaseOrderStatus.CANCELLED);
    PurchaseOrder saved = orders.save(po);
    audit.record(actor, "PO_CANCELLED", id.toString(), null);
    return saved;
  }

  @Transactional(readOnly = true)
  public PurchaseOrder get(UUID id) {
    return orders.findById(id).orElseThrow(() -> new NotFoundException("purchase order not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<PurchaseOrder> list() {
    return orders.findAll();
  }

  @Transactional(readOnly = true)
  public List<PurchaseOrderLine> lines(UUID orderId) {
    return lines.findByPurchaseOrderId(orderId);
  }

  private PurchaseOrder lockOrThrow(UUID id) {
    return orders
        .findByIdForUpdate(id)
        .orElseThrow(() -> new NotFoundException("purchase order not found: " + id));
  }

  private BigDecimal catalogPrice(UUID supplierId, String itemRef) {
    return supplierItems
        .findBySupplierIdAndItemRef(supplierId, itemRef)
        .orElseThrow(
            () ->
                new ConflictException(
                    "supplier does not supply " + itemRef + "; give an agreedPrice or add the item"))
        .getSupplierPrice();
  }

  private String generatePoNo() {
    return "PO-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
  }
}
