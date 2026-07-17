package com.kita.workflow.authorization;

/** The named back-office actions this service governs (data-model.md). */
public enum BackOfficeAction {
  TAKE_SALES_ORDER,
  CONFIRM_SALES_PAYMENT,
  RELEASE_SALES_ORDER,
  COMPLETE_SALES_ORDER,
  RAISE_PURCHASE_ORDER,
  APPROVE_PURCHASE_ORDER,
  SEND_PURCHASE_ORDER,
  RECORD_DELIVERY_RECEIPT,
  CONFIRM_DELIVERY_RECEIPT,
  BUILD_PRODUCT,
  MAINTAIN_CUSTOMER,
  MAINTAIN_SUPPLIER
}
