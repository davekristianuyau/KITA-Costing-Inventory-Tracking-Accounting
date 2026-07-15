package com.kita.procurement.supplier;

import java.math.BigDecimal;
import java.util.UUID;

public record SupplierItemResponse(
    UUID id,
    UUID supplierId,
    String itemRef,
    BigDecimal supplierPrice,
    Integer leadTimeDays,
    BigDecimal minOrderQty,
    boolean preferred) {

  public static SupplierItemResponse from(SupplierItem i) {
    return new SupplierItemResponse(
        i.getId(),
        i.getSupplierId(),
        i.getItemRef(),
        i.getSupplierPrice(),
        i.getLeadTimeDays(),
        i.getMinOrderQty(),
        i.isPreferred());
  }
}
