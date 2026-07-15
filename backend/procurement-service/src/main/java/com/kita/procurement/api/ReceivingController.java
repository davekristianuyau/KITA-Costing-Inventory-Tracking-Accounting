package com.kita.procurement.api;

import com.kita.procurement.common.security.CallerContext;
import com.kita.procurement.common.security.Role;
import com.kita.procurement.purchaseorder.PurchaseOrderService;
import com.kita.procurement.purchaseorder.dto.PurchaseOrderResponse;
import com.kita.procurement.receiving.GoodsReceipt;
import com.kita.procurement.receiving.ReceivingService;
import com.kita.procurement.receiving.dto.GoodsReceiptResponse;
import com.kita.procurement.receiving.dto.RecordReceiptRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReceivingController {

  private final ReceivingService receiving;
  private final PurchaseOrderService orders;
  private final CallerContext caller;

  public ReceivingController(
      ReceivingService receiving, PurchaseOrderService orders, CallerContext caller) {
    this.receiving = receiving;
    this.orders = orders;
    this.caller = caller;
  }

  @PostMapping("/api/procurement/purchase-orders/{id}/receipts")
  @ResponseStatus(HttpStatus.CREATED)
  public GoodsReceiptResponse record(
      @PathVariable UUID id, @Valid @RequestBody RecordReceiptRequest req) {
    caller.require(Role.PROCUREMENT_ADMIN, Role.RECEIVER);
    GoodsReceipt receipt = receiving.record(id, req, caller.actor());
    return GoodsReceiptResponse.from(
        receipt, receiving.linesOf(receipt.getId()), orders.get(id).getStatus());
  }

  @GetMapping("/api/procurement/purchase-orders/{id}/receipts")
  public List<GoodsReceiptResponse> list(@PathVariable UUID id) {
    caller.require(Role.PROCUREMENT_ADMIN, Role.APPROVER, Role.RECEIVER);
    var status = orders.get(id).getStatus();
    return receiving.forOrder(id).stream()
        .map(r -> GoodsReceiptResponse.from(r, receiving.linesOf(r.getId()), status))
        .toList();
  }

  @PostMapping("/api/procurement/purchase-orders/{id}/close")
  public PurchaseOrderResponse close(@PathVariable UUID id) {
    caller.require(Role.PROCUREMENT_ADMIN);
    return PurchaseOrderResponse.from(receiving.close(id, caller.actor()), orders.lines(id));
  }
}
