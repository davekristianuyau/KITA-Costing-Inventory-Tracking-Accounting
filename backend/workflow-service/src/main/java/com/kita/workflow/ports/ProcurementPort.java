package com.kita.workflow.ports;

import java.math.BigDecimal;
import java.util.List;

/**
 * Boundary to procurement-service (FR-007..FR-011, FR-015). US3 covers the purchase-order lifecycle;
 * receiving (US4) and supplier maintenance (US6) add methods later. procurement-service owns the PO
 * master and its approval threshold — this port only drives it.
 */
public interface ProcurementPort {

  /** True if the supplier exists and is active. */
  boolean supplierActive(String supplierId);

  /** Create a purchase order for the supplier with the given lines; returns the PO id. */
  String createPurchaseOrder(String supplierId, List<PoLine> lines);

  /** Approve the PO (threshold enforced by procurement-service). */
  void approve(String purchaseOrderId);

  /** Send the approved PO to the supplier. */
  void send(String purchaseOrderId);

  /**
   * Receive a delivery against the PO: atomically advances the PO (partially/fully received) and posts
   * the goods receipt to operations (idempotent in procurement-service). Over-receipt → 422 with no
   * change. Called on the checker's confirmation (US4).
   */
  ReceiptResult receive(String purchaseOrderId, List<ReceiptLine> lines);

  record PoLine(String itemId, BigDecimal quantity, BigDecimal unitCost) {}

  record ReceiptLine(String itemId, BigDecimal quantityReceived) {}

  /** {@code poStatus} is PARTIALLY_RECEIVED | FULLY_RECEIVED. */
  record ReceiptResult(String receiptId, String poStatus) {}
}
