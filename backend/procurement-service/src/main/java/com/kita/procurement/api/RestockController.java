package com.kita.procurement.api;

import com.kita.procurement.common.security.CallerContext;
import com.kita.procurement.common.security.Role;
import com.kita.procurement.purchaseorder.PurchaseOrder;
import com.kita.procurement.purchaseorder.PurchaseOrderService;
import com.kita.procurement.purchaseorder.dto.PurchaseOrderResponse;
import com.kita.procurement.restock.RestockService;
import com.kita.procurement.restock.dto.RestockSuggestionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/procurement/restock")
public class RestockController {

  private final RestockService restock;
  private final PurchaseOrderService orders;
  private final CallerContext caller;

  public RestockController(
      RestockService restock, PurchaseOrderService orders, CallerContext caller) {
    this.restock = restock;
    this.orders = orders;
    this.caller = caller;
  }

  /** Read the current reorder signals and produce supplier-grouped suggestions. */
  @PostMapping("/suggestions/generate")
  public List<RestockSuggestionResponse> generate() {
    caller.require(Role.PROCUREMENT_ADMIN);
    return restock.generate(caller.actor()).stream()
        .map(s -> RestockSuggestionResponse.from(s, restock.linesOf(s.getId())))
        .toList();
  }

  @GetMapping("/suggestions")
  public List<RestockSuggestionResponse> listOpen() {
    caller.require(Role.PROCUREMENT_ADMIN, Role.APPROVER);
    return restock.listOpen().stream()
        .map(s -> RestockSuggestionResponse.from(s, restock.linesOf(s.getId())))
        .toList();
  }

  /** Raise a PO from a suggestion. Stays DRAFT unless every item opted into auto-submit. */
  @PostMapping("/suggestions/{id}/convert")
  public PurchaseOrderResponse convert(@PathVariable UUID id) {
    caller.require(Role.PROCUREMENT_ADMIN);
    PurchaseOrder po = restock.convert(id, caller.actor());
    return PurchaseOrderResponse.from(po, orders.lines(po.getId()));
  }

  @PostMapping("/suggestions/{id}/dismiss")
  public RestockSuggestionResponse dismiss(@PathVariable UUID id) {
    caller.require(Role.PROCUREMENT_ADMIN);
    return RestockSuggestionResponse.from(restock.dismiss(id, caller.actor()), restock.linesOf(id));
  }
}
