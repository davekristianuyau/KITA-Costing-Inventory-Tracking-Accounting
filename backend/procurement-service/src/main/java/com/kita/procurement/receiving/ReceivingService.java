package com.kita.procurement.receiving;

import com.kita.procurement.common.AuditWriter;
import com.kita.procurement.common.ConflictException;
import com.kita.procurement.common.NotFoundException;
import com.kita.procurement.operations.OperationsPort;
import com.kita.procurement.purchaseorder.PurchaseOrder;
import com.kita.procurement.purchaseorder.PurchaseOrderLine;
import com.kita.procurement.purchaseorder.PurchaseOrderLineRepository;
import com.kita.procurement.purchaseorder.PurchaseOrderRepository;
import com.kita.procurement.purchaseorder.PurchaseOrderStateMachine;
import com.kita.procurement.purchaseorder.PurchaseOrderStatus;
import com.kita.procurement.receiving.dto.RecordReceiptRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records deliveries against a purchase order, reconciles what is still outstanding, advances the
 * order's state, and posts the receipt to operations-service exactly once.
 *
 * <p>This service never mutates inventory itself — operations-service owns on-hand and average cost
 * and is told via {@link OperationsPort}.
 */
@Service
public class ReceivingService {

  private final GoodsReceiptRepository receipts;
  private final GoodsReceiptLineRepository receiptLines;
  private final PurchaseOrderRepository orders;
  private final PurchaseOrderLineRepository orderLines;
  private final OperationsPort operations;
  private final AuditWriter audit;

  public ReceivingService(
      GoodsReceiptRepository receipts,
      GoodsReceiptLineRepository receiptLines,
      PurchaseOrderRepository orders,
      PurchaseOrderLineRepository orderLines,
      OperationsPort operations,
      AuditWriter audit) {
    this.receipts = receipts;
    this.receiptLines = receiptLines;
    this.orders = orders;
    this.orderLines = orderLines;
    this.operations = operations;
    this.audit = audit;
  }

  /**
   * Record a delivery. Takes a row lock on the order so two concurrent receipts serialise rather
   * than both reading the same outstanding quantities and both being allowed through.
   */
  @Transactional
  public GoodsReceipt record(UUID orderId, RecordReceiptRequest req, String actor) {
    PurchaseOrder po =
        orders
            .findByIdForUpdate(orderId)
            .orElseThrow(() -> new NotFoundException("purchase order not found: " + orderId));
    PurchaseOrderStateMachine.assertCanReceive(po.getStatus());

    Map<String, PurchaseOrderLine> byItem = new HashMap<>();
    for (PurchaseOrderLine l : orderLines.findByPurchaseOrderId(orderId)) {
      byItem.put(l.getItemRef(), l);
    }

    // Validate the whole delivery before applying any of it, so a bad line cannot half-apply.
    for (RecordReceiptRequest.ReceiptLineRequest l : req.lines()) {
      PurchaseOrderLine poLine = byItem.get(l.itemRef());
      if (poLine == null) {
        throw new ConflictException("item not on this order: " + l.itemRef());
      }
      BigDecimal wouldBe = poLine.getQtyReceived().add(l.qtyReceived());
      if (wouldBe.compareTo(poLine.getQtyOrdered()) > 0) {
        // No silent over-receipt (FR-010).
        throw new ConflictException(
            "over-receipt for "
                + l.itemRef()
                + ": ordered "
                + poLine.getQtyOrdered()
                + ", already received "
                + poLine.getQtyReceived()
                + ", now receiving "
                + l.qtyReceived());
      }
    }

    GoodsReceipt receipt = receipts.save(new GoodsReceipt(orderId, actor, UUID.randomUUID().toString()));

    List<OperationsPort.GoodsReceiptLine> postLines = new ArrayList<>();
    for (RecordReceiptRequest.ReceiptLineRequest l : req.lines()) {
      PurchaseOrderLine poLine = byItem.get(l.itemRef());
      poLine.receive(l.qtyReceived());
      orderLines.save(poLine);
      receiptLines.save(
          new GoodsReceiptLine(
              receipt.getId(), poLine.getId(), l.itemRef(), l.qtyReceived(), poLine.getAgreedPrice()));
      // Stock is valued at the price agreed on the PO, not today's catalog price.
      postLines.add(
          new OperationsPort.GoodsReceiptLine(l.itemRef(), l.qtyReceived(), poLine.getAgreedPrice()));
    }

    boolean allReceived =
        orderLines.findByPurchaseOrderId(orderId).stream().allMatch(PurchaseOrderLine::isFullyReceived);
    po.setStatus(PurchaseOrderStateMachine.afterReceipt(allReceived));
    orders.save(po);

    postToOperations(receipt, postLines);

    audit.record(
        actor,
        "GOODS_RECEIVED",
        receipt.getId().toString(),
        "po=" + orderId + " status=" + po.getStatus());
    return receipt;
  }

  /**
   * Post once and record that it landed. The port is idempotent on the key as a second line of
   * defence, so a retry after a timeout cannot double-count stock (FR-011, SC-004).
   */
  private void postToOperations(GoodsReceipt receipt, List<OperationsPort.GoodsReceiptLine> lines) {
    if (receipt.isPostedToOperations()) {
      return;
    }
    operations.postGoodsReceipt(
        new OperationsPort.GoodsReceiptPost(
            receipt.getId(), receipt.getPurchaseOrderId(), receipt.getPostIdempotencyKey(), lines));
    receipt.markPosted();
    receipts.save(receipt);
  }

  /** A fully received order is closed out; there is nothing further to expect from the supplier. */
  @Transactional
  public PurchaseOrder close(UUID orderId, String actor) {
    PurchaseOrder po =
        orders
            .findByIdForUpdate(orderId)
            .orElseThrow(() -> new NotFoundException("purchase order not found: " + orderId));
    if (po.getStatus() != PurchaseOrderStatus.FULLY_RECEIVED) {
      throw new ConflictException("only a FULLY_RECEIVED order can be closed (was " + po.getStatus() + ")");
    }
    po.setStatus(PurchaseOrderStatus.CLOSED);
    PurchaseOrder saved = orders.save(po);
    audit.record(actor, "PO_CLOSED", orderId.toString(), null);
    return saved;
  }

  @Transactional(readOnly = true)
  public List<GoodsReceipt> forOrder(UUID orderId) {
    return receipts.findByPurchaseOrderId(orderId);
  }

  @Transactional(readOnly = true)
  public List<GoodsReceiptLine> linesOf(UUID receiptId) {
    return receiptLines.findByGoodsReceiptId(receiptId);
  }
}
