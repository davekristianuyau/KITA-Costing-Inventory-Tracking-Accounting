package com.kita.workflow.workflow;

import com.kita.workflow.authorization.BackOfficeAction;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.pending.PendingReview;
import com.kita.workflow.pending.PendingReviewStore;
import com.kita.workflow.ports.ProcurementPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Goods receiving under maker–checker review (US4, FR-010/011, FR-021). The maker records a receipt as
 * transient pending state with <b>no</b> downstream write; a distinct checker confirms, and only then
 * is the single atomic {@link ProcurementPort#receive} call made (advances the PO + posts the goods
 * receipt to operations). Self-review is refused; over-receipt and an unavailable inventory update
 * reject the whole receipt with the PO unchanged, and the pending item is left for correction/retry.
 */
@Component
public class ReceivingWorkflow {

  static final String PENDING_REVIEW = "PENDING_REVIEW";

  private final ProcurementPort procurement;
  private final PendingReviewStore pending;

  public ReceivingWorkflow(ProcurementPort procurement, PendingReviewStore pending) {
    this.procurement = procurement;
    this.pending = pending;
  }

  public record RecordRequest(String purchaseOrderId, List<ProcurementPort.ReceiptLine> lines) {}

  public record RecordResult(String pendingReceiptId, String state) {}

  /** Record a receipt pending review — transient, no downstream write yet. */
  public RecordResult record(String actorEmployeeId, RecordRequest request) {
    if (request.lines() == null || request.lines().isEmpty()) {
      throw new ValidationException("a receipt needs at least one line");
    }
    String pendingReceiptId = UUID.randomUUID().toString();
    pending.put(
        new PendingReview(
            pendingReceiptId,
            BackOfficeAction.CONFIRM_DELIVERY_RECEIPT,
            actorEmployeeId,
            "po:" + request.purchaseOrderId(),
            request,
            PENDING_REVIEW,
            Instant.now()));
    return new RecordResult(pendingReceiptId, PENDING_REVIEW);
  }

  /** Confirm by a distinct checker; commits the receipt atomically via procurement. */
  public ProcurementPort.ReceiptResult confirm(String actorEmployeeId, String pendingReceiptId) {
    PendingReview review =
        pending
            .get(pendingReceiptId)
            .orElseThrow(
                () -> new ValidationException("no pending receipt " + pendingReceiptId));
    if (review.makerEmployeeId().equals(actorEmployeeId)) {
      throw new ValidationException("self-review not allowed: the recorder cannot confirm");
    }
    RecordRequest request = (RecordRequest) review.payload();
    // Re-validates references downstream (unknown/over-receipt/unavailable throw); nothing applied on
    // failure, so the pending item stays for correction/retry.
    ProcurementPort.ReceiptResult result =
        procurement.receive(request.purchaseOrderId(), request.lines());
    pending.remove(pendingReceiptId);
    return result;
  }

  /** The maker of a pending receipt (for attribution of the checker's confirmation). */
  public String makerOf(String pendingReceiptId) {
    return pending.get(pendingReceiptId).map(PendingReview::makerEmployeeId).orElse(null);
  }
}
