package com.kita.workflow.api;

import com.kita.workflow.actor.BackOfficePipeline;
import com.kita.workflow.api.dto.PurchaseOrderDtos.RaiseRequest;
import com.kita.workflow.api.dto.PurchaseOrderDtos.RaiseResponse;
import com.kita.workflow.api.dto.PurchaseOrderDtos.StatusResponse;
import com.kita.workflow.authorization.AuthorizationKind;
import com.kita.workflow.authorization.BackOfficeAction;
import com.kita.workflow.workflow.PurchaseOrderWorkflow;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Purchase-order lifecycle endpoints (US3), each run through the {@link BackOfficePipeline}. */
@RestController
@RequestMapping("/api/workflow/purchase-orders")
public class PurchaseOrderController {

  private final BackOfficePipeline pipeline;
  private final PurchaseOrderWorkflow workflow;

  public PurchaseOrderController(BackOfficePipeline pipeline, PurchaseOrderWorkflow workflow) {
    this.pipeline = pipeline;
    this.workflow = workflow;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RaiseResponse raise(@Valid @RequestBody RaiseRequest body) {
    return pipeline.execute(
        BackOfficeAction.RAISE_PURCHASE_ORDER,
        AuthorizationKind.PERFORM,
        null,
        null,
        actor ->
            toResponse(
                workflow.raise(
                    actor.employeeId(),
                    new PurchaseOrderWorkflow.RaiseRequest(body.supplierId(), body.toPoLines()))),
        r -> "po:" + r.purchaseOrderId());
  }

  @PostMapping("/{id}/approve")
  public StatusResponse approve(@PathVariable String id) {
    return pipeline.execute(
        BackOfficeAction.APPROVE_PURCHASE_ORDER,
        AuthorizationKind.PERFORM,
        "po:" + id,
        null,
        actor -> new StatusResponse(workflow.approve(actor.employeeId(), id)),
        null);
  }

  @PostMapping("/{id}/send")
  public StatusResponse send(@PathVariable String id) {
    return pipeline.execute(
        BackOfficeAction.SEND_PURCHASE_ORDER,
        AuthorizationKind.PERFORM,
        "po:" + id,
        null,
        actor -> new StatusResponse(workflow.send(actor.employeeId(), id)),
        null);
  }

  private RaiseResponse toResponse(PurchaseOrderWorkflow.RaiseResult r) {
    return new RaiseResponse(r.purchaseOrderId(), r.status(), r.total());
  }
}
