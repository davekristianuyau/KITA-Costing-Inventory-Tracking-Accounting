package com.kita.procurement.purchaseorder;

/** Lifecycle of a purchase order. CLOSED and CANCELLED are terminal. */
public enum PurchaseOrderStatus {
  DRAFT,
  APPROVED,
  SENT,
  PARTIALLY_RECEIVED,
  FULLY_RECEIVED,
  CLOSED,
  CANCELLED
}
