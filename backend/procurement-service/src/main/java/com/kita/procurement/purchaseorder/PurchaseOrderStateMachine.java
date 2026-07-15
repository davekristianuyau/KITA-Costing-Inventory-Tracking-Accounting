package com.kita.procurement.purchaseorder;

import com.kita.procurement.common.ConflictException;

/**
 * Guards the purchase-order lifecycle. Pure (no Spring/DB) so every legal and illegal transition is
 * exhaustively unit-testable.
 *
 * <pre>
 * DRAFT ──approve──▶ APPROVED ──send──▶ SENT ──receive──▶ PARTIALLY_RECEIVED ──▶ FULLY_RECEIVED ──▶ CLOSED
 * DRAFT | APPROVED | SENT(pre-receipt) ──cancel──▶ CANCELLED
 * </pre>
 *
 * <p>There is no transition out of CLOSED or CANCELLED.
 */
public final class PurchaseOrderStateMachine {

  private PurchaseOrderStateMachine() {}

  public static void assertCanApprove(PurchaseOrderStatus status) {
    if (status != PurchaseOrderStatus.DRAFT) {
      throw new ConflictException("only a DRAFT order can be approved (was " + status + ")");
    }
  }

  public static void assertCanSend(PurchaseOrderStatus status) {
    if (status != PurchaseOrderStatus.APPROVED) {
      throw new ConflictException("only an APPROVED order can be sent (was " + status + ")");
    }
  }

  public static void assertCanReceive(PurchaseOrderStatus status) {
    if (status != PurchaseOrderStatus.SENT && status != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
      throw new ConflictException(
          "only a SENT or PARTIALLY_RECEIVED order can be received against (was " + status + ")");
    }
  }

  /** Cancel is allowed only before any stock has arrived — a received order must be closed, not erased. */
  public static void assertCanCancel(PurchaseOrderStatus status) {
    switch (status) {
      case DRAFT, APPROVED, SENT -> {
        // fine: nothing has been received yet
      }
      case PARTIALLY_RECEIVED, FULLY_RECEIVED ->
          throw new ConflictException("an order with receipts cannot be cancelled (was " + status + ")");
      default -> throw new ConflictException("a " + status + " order cannot be cancelled");
    }
  }

  /** Lines are immutable once the order has gone to the supplier (FR-007). */
  public static void assertLinesEditable(PurchaseOrderStatus status) {
    if (status != PurchaseOrderStatus.DRAFT) {
      throw new ConflictException("lines can only be edited while the order is DRAFT (was " + status + ")");
    }
  }

  /** The status a receipt leaves the order in, given whether every line is now fully received. */
  public static PurchaseOrderStatus afterReceipt(boolean allLinesFullyReceived) {
    return allLinesFullyReceived
        ? PurchaseOrderStatus.FULLY_RECEIVED
        : PurchaseOrderStatus.PARTIALLY_RECEIVED;
  }
}
