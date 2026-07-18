package com.kita.workflow.workflow;

import com.kita.workflow.authorization.BackOfficeAction;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.pending.PendingReview;
import com.kita.workflow.pending.PendingReviewStore;
import com.kita.workflow.ports.CrmPort;
import com.kita.workflow.ports.OperationsPort;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Drives the sales-order lifecycle DRAFT → PAYMENT_CONFIRMED → RELEASED → COMPLETED (FR-004, SC-001).
 * The operations order is the durable anchor; the review position (maker id + stage) is transient in
 * the {@link PendingReviewStore}. Payment confirmation and release are maker–checker gated: the
 * confirming employee must differ from the drafter (FR-021, SC-009). If a draft step fails after the
 * order is created, the operations order is cancelled (compensation, SC-005).
 */
@Component
public class SalesOrderWorkflow {

  static final String DRAFT = "DRAFT";
  static final String PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED";
  static final String RELEASED = "RELEASED";
  static final String COMPLETED = "COMPLETED";

  private final OperationsPort operations;
  private final CrmPort crm;
  private final PendingReviewStore pending;

  public SalesOrderWorkflow(OperationsPort operations, CrmPort crm, PendingReviewStore pending) {
    this.operations = operations;
    this.crm = crm;
    this.pending = pending;
  }

  public record DraftRequest(String customerId, List<OperationsPort.SalesLine> lines) {}

  public record DraftResult(String salesOrderId, String state, boolean reservedAll) {}

  /** Create a DRAFT order and reserve stock, attributed to {@code actorEmployeeId} (the maker). */
  public DraftResult draft(String actorEmployeeId, DraftRequest request) {
    if (!crm.customerActive(request.customerId())) {
      throw new ValidationException("unknown or inactive customer: " + request.customerId());
    }
    if (request.lines() == null || request.lines().isEmpty()) {
      throw new ValidationException("a sales order needs at least one line");
    }
    String salesOrderId = operations.createSalesOrder(request.customerId());
    try {
      for (OperationsPort.SalesLine line : request.lines()) {
        operations.addSalesOrderLine(salesOrderId, line);
      }
      operations.confirmSalesOrder(salesOrderId); // reserves; throws on oversell
    } catch (RuntimeException e) {
      operations.cancelSalesOrder(salesOrderId); // compensation — no dangling draft
      throw e;
    }
    pending.put(
        new PendingReview(
            salesOrderId,
            BackOfficeAction.CONFIRM_SALES_PAYMENT,
            actorEmployeeId,
            targetRef(salesOrderId),
            null,
            DRAFT,
            Instant.now()));
    return new DraftResult(salesOrderId, DRAFT, true);
  }

  /** Manager/cashier confirms payment; must differ from the drafter (maker≠checker). */
  public String confirmPayment(String actorEmployeeId, String salesOrderId) {
    PendingReview position = require(salesOrderId, DRAFT, "confirm payment");
    if (position.makerEmployeeId().equals(actorEmployeeId)) {
      throw new ValidationException("self-review not allowed: the drafter cannot confirm payment");
    }
    pending.put(position.withStage(PAYMENT_CONFIRMED));
    return PAYMENT_CONFIRMED;
  }

  /** Warehouse/manager releases after the packed check; commits reserved stock. */
  public String release(String actorEmployeeId, String salesOrderId) {
    PendingReview position = require(salesOrderId, PAYMENT_CONFIRMED, "release");
    operations.fulfillSalesOrder(salesOrderId);
    pending.put(position.withStage(RELEASED));
    return RELEASED;
  }

  /** Handed to the customer; clears the transient position. */
  public String complete(String actorEmployeeId, String salesOrderId) {
    require(salesOrderId, RELEASED, "complete");
    pending.remove(salesOrderId);
    return COMPLETED;
  }

  /** The drafter (maker) of an in-review sales order, for the checker's self-review guard. */
  public String makerOf(String salesOrderId) {
    return pending.get(salesOrderId).map(PendingReview::makerEmployeeId).orElse(null);
  }

  /** Abort/compensate: cancel the operations order and clear the position. */
  public void cancel(String actorEmployeeId, String salesOrderId) {
    operations.cancelSalesOrder(salesOrderId);
    pending.remove(salesOrderId);
  }

  private PendingReview require(String salesOrderId, String expectedStage, String action) {
    PendingReview position =
        pending
            .get(salesOrderId)
            .orElseThrow(
                () -> new ValidationException("no in-review sales order " + salesOrderId));
    if (!expectedStage.equals(position.stage())) {
      throw new ValidationException(
          "cannot " + action + ": order is " + position.stage() + ", expected " + expectedStage);
    }
    return position;
  }

  private static String targetRef(String salesOrderId) {
    return "sales-order:" + salesOrderId;
  }
}
