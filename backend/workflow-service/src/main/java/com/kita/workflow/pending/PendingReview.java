package com.kita.workflow.pending;

import com.kita.workflow.authorization.BackOfficeAction;
import java.time.Instant;

/**
 * Transient in-flight state for a review-gated action awaiting its checker, or the review position of
 * a sales order (Clarify Q5). Held only in the {@link PendingReviewStore}; discarded on confirmation.
 * Losing it means the maker re-records — no domain effect has occurred.
 *
 * @param pendingId handle returned to the maker (sales orders reuse the operations order id)
 * @param action the review-gated action
 * @param makerEmployeeId who created it — enforces maker ≠ checker
 * @param targetRef the affected domain record, e.g. {@code sales-order:<id>} or {@code po:<id>}
 * @param payload the captured request to replay downstream on confirm (may be null for a position)
 * @param stage optional lifecycle stage (e.g. sales-order position); null for a simple pending item
 * @param createdAt for optional expiry
 */
public record PendingReview(
    String pendingId,
    BackOfficeAction action,
    String makerEmployeeId,
    String targetRef,
    Object payload,
    String stage,
    Instant createdAt) {

  public PendingReview withStage(String newStage) {
    return new PendingReview(pendingId, action, makerEmployeeId, targetRef, payload, newStage, createdAt);
  }
}
