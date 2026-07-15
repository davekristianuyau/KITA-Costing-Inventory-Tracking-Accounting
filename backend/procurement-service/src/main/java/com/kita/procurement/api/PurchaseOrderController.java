package com.kita.procurement.api;

import com.kita.procurement.common.security.CallerContext;
import com.kita.procurement.common.security.Role;
import com.kita.procurement.purchaseorder.PurchaseOrder;
import com.kita.procurement.purchaseorder.PurchaseOrderService;
import com.kita.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.kita.procurement.purchaseorder.dto.PurchaseOrderResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/procurement/purchase-orders")
public class PurchaseOrderController {

  private final PurchaseOrderService orders;
  private final CallerContext caller;

  public PurchaseOrderController(PurchaseOrderService orders, CallerContext caller) {
    this.orders = orders;
    this.caller = caller;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PurchaseOrderResponse create(@Valid @RequestBody CreatePurchaseOrderRequest req) {
    caller.require(Role.PROCUREMENT_ADMIN);
    PurchaseOrder po = orders.create(req, caller.actor());
    return PurchaseOrderResponse.from(po, orders.lines(po.getId()));
  }

  @GetMapping
  public List<PurchaseOrderResponse> list() {
    caller.require(Role.PROCUREMENT_ADMIN, Role.APPROVER, Role.RECEIVER);
    return orders.list().stream()
        .map(po -> PurchaseOrderResponse.from(po, orders.lines(po.getId())))
        .toList();
  }

  @GetMapping("/{id}")
  public PurchaseOrderResponse get(@PathVariable UUID id) {
    caller.require(Role.PROCUREMENT_ADMIN, Role.APPROVER, Role.RECEIVER);
    PurchaseOrder po = orders.get(id);
    return PurchaseOrderResponse.from(po, orders.lines(id));
  }

  /** Over-threshold orders require the APPROVER role; the service enforces the threshold itself. */
  @PostMapping("/{id}/approve")
  public PurchaseOrderResponse approve(@PathVariable UUID id) {
    caller.require(Role.PROCUREMENT_ADMIN, Role.APPROVER);
    PurchaseOrder po = orders.approve(id, caller.hasAnyRole(Role.APPROVER), caller.actor());
    return PurchaseOrderResponse.from(po, orders.lines(id));
  }

  @PostMapping("/{id}/send")
  public PurchaseOrderResponse send(@PathVariable UUID id) {
    caller.require(Role.PROCUREMENT_ADMIN);
    PurchaseOrder po = orders.send(id, caller.actor());
    return PurchaseOrderResponse.from(po, orders.lines(id));
  }

  @PostMapping("/{id}/cancel")
  public PurchaseOrderResponse cancel(@PathVariable UUID id) {
    caller.require(Role.PROCUREMENT_ADMIN);
    PurchaseOrder po = orders.cancel(id, caller.actor());
    return PurchaseOrderResponse.from(po, orders.lines(id));
  }
}
