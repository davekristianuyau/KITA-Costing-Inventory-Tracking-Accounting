package com.kita.procurement.receiving.dto;

import com.kita.procurement.purchaseorder.PurchaseOrderStatus;
import com.kita.procurement.receiving.GoodsReceipt;
import com.kita.procurement.receiving.GoodsReceiptLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptResponse(
    UUID id,
    UUID purchaseOrderId,
    Instant receivedAt,
    boolean postedToOperations,
    PurchaseOrderStatus orderStatus,
    List<LineResponse> lines) {

  public record LineResponse(String itemRef, BigDecimal qtyReceived, BigDecimal unitCost) {

    static LineResponse from(GoodsReceiptLine l) {
      return new LineResponse(l.getItemRef(), l.getQtyReceived(), l.getUnitCost());
    }
  }

  public static GoodsReceiptResponse from(
      GoodsReceipt r, List<GoodsReceiptLine> lines, PurchaseOrderStatus orderStatus) {
    return new GoodsReceiptResponse(
        r.getId(),
        r.getPurchaseOrderId(),
        r.getReceivedAt(),
        r.isPostedToOperations(),
        orderStatus,
        lines.stream().map(LineResponse::from).toList());
  }
}
