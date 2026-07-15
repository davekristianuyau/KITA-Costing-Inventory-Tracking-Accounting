package com.kita.crm.api;

import com.kita.crm.common.security.CallerContext;
import com.kita.crm.common.security.Role;
import com.kita.crm.customer.CreateCustomerRequest;
import com.kita.crm.customer.CustomerResponse;
import com.kita.crm.customer.CustomerService;
import com.kita.crm.customer.UpdateCustomerRequest;
import com.kita.crm.entitlement.Entitlement;
import com.kita.crm.entitlement.EntitlementRequest;
import com.kita.crm.entitlement.EntitlementResponse;
import com.kita.crm.entitlement.EntitlementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crm/customers")
public class CustomerController {

  private final CustomerService customers;
  private final EntitlementService entitlements;
  private final CallerContext caller;

  public CustomerController(
      CustomerService customers, EntitlementService entitlements, CallerContext caller) {
    this.customers = customers;
    this.entitlements = entitlements;
    this.caller = caller;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest req) {
    caller.require(Role.CRM_ADMIN);
    return CustomerResponse.from(customers.create(req, caller.actor()));
  }

  @GetMapping
  public List<CustomerResponse> list() {
    caller.require(Role.CRM_ADMIN, Role.SALES);
    return customers.list().stream().map(CustomerResponse::from).toList();
  }

  /** Also the party-validation lookup used by operations-service. */
  @GetMapping("/{id}")
  public CustomerResponse get(@PathVariable UUID id) {
    caller.require(Role.CRM_ADMIN, Role.SALES);
    return CustomerResponse.from(customers.get(id));
  }

  @PatchMapping("/{id}")
  public CustomerResponse update(
      @PathVariable UUID id, @RequestBody UpdateCustomerRequest req) {
    caller.require(Role.CRM_ADMIN);
    return CustomerResponse.from(customers.update(id, req, caller.actor()));
  }

  @PostMapping("/{id}/entitlements")
  @ResponseStatus(HttpStatus.CREATED)
  public EntitlementResponse addEntitlement(
      @PathVariable UUID id, @Valid @RequestBody EntitlementRequest req) {
    caller.require(Role.CRM_ADMIN);
    Entitlement e =
        new Entitlement(id, req.kind(), req.supportingIdRef(), req.validFrom(), req.validTo());
    return EntitlementResponse.from(entitlements.add(id, e, caller.actor()));
  }

  @GetMapping("/{id}/entitlements")
  public List<EntitlementResponse> listEntitlements(@PathVariable UUID id) {
    caller.require(Role.CRM_ADMIN, Role.SALES);
    customers.get(id); // 404 if unknown
    return entitlements.forCustomer(id).stream().map(EntitlementResponse::from).toList();
  }
}
