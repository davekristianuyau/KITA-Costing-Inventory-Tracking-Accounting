package com.kita.procurement.supplier;

import java.time.Instant;

public record SupplierHistoryResponse(
    String itemRef, String field, String oldValue, String newValue, String actor, Instant changedAt) {

  public static SupplierHistoryResponse from(SupplierChangeHistory h) {
    return new SupplierHistoryResponse(
        h.getItemRef(),
        h.getField(),
        h.getOldValue(),
        h.getNewValue(),
        h.getActor(),
        h.getChangedAt());
  }
}
