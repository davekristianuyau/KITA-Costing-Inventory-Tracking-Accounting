package com.kita.workflow.api;

import com.kita.workflow.actor.BackOfficePipeline;
import com.kita.workflow.api.dto.PartyDtos.CustomerRequest;
import com.kita.workflow.api.dto.PartyDtos.CustomerResponse;
import com.kita.workflow.api.dto.PartyDtos.SupplierRequest;
import com.kita.workflow.api.dto.PartyDtos.SupplierResponse;
import com.kita.workflow.api.dto.PartyDtos.SuppliedItemsRequest;
import com.kita.workflow.authorization.AuthorizationKind;
import com.kita.workflow.authorization.BackOfficeAction;
import com.kita.workflow.workflow.PartyWorkflow;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Customer & supplier maintenance endpoints (US6), run through the {@link BackOfficePipeline}. */
@RestController
@RequestMapping("/api/workflow")
public class PartyController {

  private final BackOfficePipeline pipeline;
  private final PartyWorkflow workflow;

  public PartyController(BackOfficePipeline pipeline, PartyWorkflow workflow) {
    this.pipeline = pipeline;
    this.workflow = workflow;
  }

  @PostMapping("/customers")
  @ResponseStatus(HttpStatus.CREATED)
  public CustomerResponse createCustomer(@Valid @RequestBody CustomerRequest body) {
    return pipeline.execute(
        BackOfficeAction.MAINTAIN_CUSTOMER,
        AuthorizationKind.PERFORM,
        null,
        null,
        actor -> new CustomerResponse(workflow.createCustomer(actor.employeeId(), body.toInput())),
        r -> "customer:" + r.customerId());
  }

  @PatchMapping("/customers/{id}")
  public CustomerResponse updateCustomer(
      @PathVariable String id, @Valid @RequestBody CustomerRequest body) {
    return pipeline.execute(
        BackOfficeAction.MAINTAIN_CUSTOMER,
        AuthorizationKind.PERFORM,
        "customer:" + id,
        null,
        actor -> {
          workflow.updateCustomer(actor.employeeId(), id, body.toInput());
          return new CustomerResponse(id);
        },
        null);
  }

  @PostMapping("/suppliers")
  @ResponseStatus(HttpStatus.CREATED)
  public SupplierResponse createSupplier(@Valid @RequestBody SupplierRequest body) {
    return pipeline.execute(
        BackOfficeAction.MAINTAIN_SUPPLIER,
        AuthorizationKind.PERFORM,
        null,
        null,
        actor -> new SupplierResponse(workflow.createSupplier(actor.employeeId(), body.toInput())),
        r -> "supplier:" + r.supplierId());
  }

  @PatchMapping("/suppliers/{id}")
  public SupplierResponse updateSupplier(
      @PathVariable String id, @Valid @RequestBody SupplierRequest body) {
    return pipeline.execute(
        BackOfficeAction.MAINTAIN_SUPPLIER,
        AuthorizationKind.PERFORM,
        "supplier:" + id,
        null,
        actor -> {
          workflow.updateSupplier(actor.employeeId(), id, body.toInput());
          return new SupplierResponse(id);
        },
        null);
  }

  @PutMapping("/suppliers/{id}/items")
  public SupplierResponse setSuppliedItems(
      @PathVariable String id, @Valid @RequestBody SuppliedItemsRequest body) {
    return pipeline.execute(
        BackOfficeAction.MAINTAIN_SUPPLIER,
        AuthorizationKind.PERFORM,
        "supplier:" + id,
        null,
        actor -> {
          workflow.setSuppliedItems(actor.employeeId(), id, body.toSuppliedItems());
          return new SupplierResponse(id);
        },
        null);
  }
}
