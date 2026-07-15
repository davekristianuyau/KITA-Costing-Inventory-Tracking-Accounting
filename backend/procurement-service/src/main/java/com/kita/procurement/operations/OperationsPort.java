package com.kita.procurement.operations;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The boundary to operations-service. procurement-service never touches inventory directly: it reads
 * reorder signals and posts goods receipts through this port, and operations-service owns stock
 * balances and costing (AVCO/FIFO).
 *
 * <p>Mirrors the feature-003 Party port so this service can be built and tested in isolation against
 * a fake; the real adapter is an HTTP client.
 */
public interface OperationsPort {

  /** An item at or below its reorder point — the input to restock sizing. */
  record ReorderSignal(String itemRef, BigDecimal onHand, BigDecimal reorderPoint, BigDecimal targetLevel) {}

  /** One line of stock arriving, at the price actually agreed on the PO. */
  record GoodsReceiptLine(String itemRef, BigDecimal quantity, BigDecimal unitCost) {}

  /**
   * A receipt to post. {@code idempotencyKey} is derived from the receipt id, so a retry after a
   * timeout must not double-post (FR-011, SC-004).
   */
  record GoodsReceiptPost(
      UUID receiptId, UUID purchaseOrderId, String idempotencyKey, List<GoodsReceiptLine> lines) {}

  /** Items at/below reorder point, with current stock and target level. */
  List<ReorderSignal> getReorderSignals();

  /**
   * Post a receipt so operations-service updates on-hand and average cost. Implementations MUST be
   * idempotent on {@code idempotencyKey}.
   *
   * @return true if this call posted the receipt, false if it had already been posted
   */
  boolean postGoodsReceipt(GoodsReceiptPost receipt);
}
