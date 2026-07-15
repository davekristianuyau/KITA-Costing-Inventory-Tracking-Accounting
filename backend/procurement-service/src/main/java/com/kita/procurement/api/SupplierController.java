package com.kita.procurement.api;

import com.kita.procurement.common.security.CallerContext;
import com.kita.procurement.common.security.Role;
import com.kita.procurement.supplier.CreateSupplierRequest;
import com.kita.procurement.supplier.SupplierHistoryResponse;
import com.kita.procurement.supplier.SupplierItemRequest;
import com.kita.procurement.supplier.SupplierItemResponse;
import com.kita.procurement.supplier.SupplierResponse;
import com.kita.procurement.supplier.SupplierService;
import com.kita.procurement.supplier.UpdateSupplierRequest;
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
@RequestMapping("/api/procurement/suppliers")
public class SupplierController {

  private final SupplierService suppliers;
  private final CallerContext caller;

  public SupplierController(SupplierService suppliers, CallerContext caller) {
    this.suppliers = suppliers;
    this.caller = caller;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SupplierResponse create(@Valid @RequestBody CreateSupplierRequest req) {
    caller.require(Role.PROCUREMENT_ADMIN);
    return SupplierResponse.from(suppliers.create(req, caller.actor()));
  }

  @GetMapping
  public List<SupplierResponse> list() {
    caller.require(Role.PROCUREMENT_ADMIN, Role.APPROVER, Role.RECEIVER);
    return suppliers.list().stream().map(SupplierResponse::from).toList();
  }

  /** Also the party-validation lookup used by operations-service. */
  @GetMapping("/{id}")
  public SupplierResponse get(@PathVariable UUID id) {
    caller.require(Role.PROCUREMENT_ADMIN, Role.APPROVER, Role.RECEIVER);
    return SupplierResponse.from(suppliers.get(id));
  }

  @PatchMapping("/{id}")
  public SupplierResponse update(@PathVariable UUID id, @RequestBody UpdateSupplierRequest req) {
    caller.require(Role.PROCUREMENT_ADMIN);
    return SupplierResponse.from(suppliers.update(id, req, caller.actor()));
  }

  @PostMapping("/{id}/items")
  @ResponseStatus(HttpStatus.CREATED)
  public SupplierItemResponse upsertItem(
      @PathVariable UUID id, @Valid @RequestBody SupplierItemRequest req) {
    caller.require(Role.PROCUREMENT_ADMIN);
    return SupplierItemResponse.from(suppliers.upsertItem(id, req, caller.actor()));
  }

  @GetMapping("/{id}/items")
  public List<SupplierItemResponse> items(@PathVariable UUID id) {
    caller.require(Role.PROCUREMENT_ADMIN, Role.APPROVER, Role.RECEIVER);
    return suppliers.items(id).stream().map(SupplierItemResponse::from).toList();
  }

  @GetMapping("/{id}/history")
  public List<SupplierHistoryResponse> history(@PathVariable UUID id) {
    caller.require(Role.PROCUREMENT_ADMIN, Role.APPROVER);
    return suppliers.history(id).stream().map(SupplierHistoryResponse::from).toList();
  }
}
