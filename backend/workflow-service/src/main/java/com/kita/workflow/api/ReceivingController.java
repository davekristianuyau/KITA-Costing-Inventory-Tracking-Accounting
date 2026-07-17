package com.kita.workflow.api;

import com.kita.workflow.actor.BackOfficePipeline;
import com.kita.workflow.api.dto.ReceivingDtos.ConfirmResponse;
import com.kita.workflow.api.dto.ReceivingDtos.RecordRequest;
import com.kita.workflow.api.dto.ReceivingDtos.RecordResponse;
import com.kita.workflow.authorization.AuthorizationKind;
import com.kita.workflow.authorization.BackOfficeAction;
import com.kita.workflow.workflow.ReceivingWorkflow;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receiving endpoints (US4): the maker records a receipt (transient pending), a distinct checker
 * confirms (commits atomically). Both run through the {@link BackOfficePipeline}.
 */
@RestController
public class ReceivingController {

  private final BackOfficePipeline pipeline;
  private final ReceivingWorkflow workflow;

  public ReceivingController(BackOfficePipeline pipeline, ReceivingWorkflow workflow) {
    this.pipeline = pipeline;
    this.workflow = workflow;
  }

  @PostMapping("/api/workflow/purchase-orders/{id}/receipts")
  @ResponseStatus(HttpStatus.CREATED)
  public RecordResponse record(@PathVariable String id, @Valid @RequestBody RecordRequest body) {
    return pipeline.execute(
        BackOfficeAction.RECORD_DELIVERY_RECEIPT,
        AuthorizationKind.MAKER,
        "po:" + id,
        null,
        actor ->
            toRecordResponse(
                workflow.record(
                    actor.employeeId(),
                    new ReceivingWorkflow.RecordRequest(id, body.toReceiptLines()))),
        r -> "receipt:" + r.pendingReceiptId());
  }

  @PostMapping("/api/workflow/receipts/{pendingReceiptId}/confirm")
  @ResponseStatus(HttpStatus.CREATED)
  public ConfirmResponse confirm(@PathVariable String pendingReceiptId) {
    String maker = workflow.makerOf(pendingReceiptId); // for attribution of the confirmation
    return pipeline.execute(
        BackOfficeAction.CONFIRM_DELIVERY_RECEIPT,
        AuthorizationKind.CHECKER,
        "receipt:" + pendingReceiptId,
        maker,
        actor -> {
          var result = workflow.confirm(actor.employeeId(), pendingReceiptId);
          return new ConfirmResponse(result.receiptId(), result.poStatus());
        },
        null);
  }

  private RecordResponse toRecordResponse(ReceivingWorkflow.RecordResult r) {
    return new RecordResponse(r.pendingReceiptId(), r.state());
  }
}
