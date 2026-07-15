package com.kita.procurement.purchaseorder.dto;

import com.kita.procurement.purchaseorder.PurchaseOrder;
import com.kita.procurement.purchaseorder.PurchaseOrderLine;
import com.kita.procurement.purchaseorder.PurchaseOrderOrigin;
import com.kita.procurement.purchaseorder.PurchaseOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
    UUID id,
    String poNo,
    UUID supplierId,
    PurchaseOrderStatus status,
    PurchaseOrderOrigin origin,
    BigDecimal orderTotal,
    String approvedBy,
    Instant approvedAt,
    Instant sentAt,
    List<LineResponse> lines) {

  public record LineResponse(
      UUID id,
      String itemRef,
      BigDecimal qtyOrdered,
      BigDecimal agreedPrice,
      BigDecimal qtyReceived,
      BigDecimal qtyOutstanding,
      BigDecimal lineTotal) {

    public static LineResponse from(PurchaseOrderLine l) {
      return new LineResponse(
          l.getId(),
          l.getItemRef(),
          l.getQtyOrdered(),
          l.getAgreedPrice(),
          l.getQtyReceived(),
          l.qtyOutstanding(),
          l.getLineTotal());
    }
  }

  public static PurchaseOrderResponse from(PurchaseOrder po, List<PurchaseOrderLine> lines) {
    return new PurchaseOrderResponse(
        po.getId(),
        po.getPoNo(),
        po.getSupplierId(),
        po.getStatus(),
        po.getOrigin(),
        po.getOrderTotal(),
        po.getApprovedBy(),
        po.getApprovedAt(),
        po.getSentAt(),
        lines.stream().map(LineResponse::from).toList());
  }
}
