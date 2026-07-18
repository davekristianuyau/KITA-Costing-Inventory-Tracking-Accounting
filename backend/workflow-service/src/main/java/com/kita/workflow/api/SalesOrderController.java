package com.kita.workflow.api;

import com.kita.workflow.actor.BackOfficePipeline;
import com.kita.workflow.api.dto.SalesOrderDtos.DraftRequest;
import com.kita.workflow.api.dto.SalesOrderDtos.DraftResponse;
import com.kita.workflow.api.dto.SalesOrderDtos.StateResponse;
import com.kita.workflow.authorization.AuthorizationKind;
import com.kita.workflow.authorization.BackOfficeAction;
import com.kita.workflow.workflow.SalesOrderWorkflow;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sales-order lifecycle endpoints (US2). Every call runs through {@link BackOfficePipeline} so the
 * acting employee is validated, authorized against their HR roles, and the outcome recorded.
 */
@RestController
@RequestMapping("/api/workflow/sales-orders")
public class SalesOrderController {

  private final BackOfficePipeline pipeline;
  private final SalesOrderWorkflow workflow;

  public SalesOrderController(BackOfficePipeline pipeline, SalesOrderWorkflow workflow) {
    this.pipeline = pipeline;
    this.workflow = workflow;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DraftResponse draft(@Valid @RequestBody DraftRequest body) {
    return pipeline.execute(
        BackOfficeAction.TAKE_SALES_ORDER,
        AuthorizationKind.MAKER,
        null,
        null,
        actor ->
            toResponse(
                workflow.draft(
                    actor.employeeId(),
                    new SalesOrderWorkflow.DraftRequest(body.customerId(), body.toSalesLines()))),
        r -> "sales-order:" + r.salesOrderId());
  }

  @PostMapping("/{id}/confirm-payment")
  public StateResponse confirmPayment(@PathVariable String id) {
    String maker = workflow.makerOf(id); // the drafter — enforces maker≠checker before the role grant
    return pipeline.execute(
        BackOfficeAction.CONFIRM_SALES_PAYMENT,
        AuthorizationKind.CHECKER,
        "sales-order:" + id,
        maker,
        actor -> new StateResponse(workflow.confirmPayment(actor.employeeId(), id)),
        null);
  }

  @PostMapping("/{id}/release")
  public StateResponse release(@PathVariable String id) {
    return pipeline.execute(
        BackOfficeAction.RELEASE_SALES_ORDER,
        AuthorizationKind.CHECKER,
        "sales-order:" + id,
        null,
        actor -> new StateResponse(workflow.release(actor.employeeId(), id)),
        null);
  }

  @PostMapping("/{id}/complete")
  public StateResponse complete(@PathVariable String id) {
    return pipeline.execute(
        BackOfficeAction.COMPLETE_SALES_ORDER,
        AuthorizationKind.PERFORM,
        "sales-order:" + id,
        null,
        actor -> new StateResponse(workflow.complete(actor.employeeId(), id)),
        null);
  }

  @PostMapping("/{id}/cancel")
  public StateResponse cancel(@PathVariable String id) {
    return pipeline.execute(
        BackOfficeAction.TAKE_SALES_ORDER,
        AuthorizationKind.MAKER,
        "sales-order:" + id,
        null,
        actor -> {
          workflow.cancel(actor.employeeId(), id);
          return new StateResponse("CANCELLED");
        },
        null);
  }

  private DraftResponse toResponse(SalesOrderWorkflow.DraftResult r) {
    return new DraftResponse(r.salesOrderId(), r.state(), r.reservedAll());
  }
}
