package com.kita.operations.inventory;

/** The kind of stock change a movement represents. */
public enum MovementType {
  RECEIPT,
  ISSUE,
  ADJUSTMENT,
  TRANSFER_OUT,
  TRANSFER_IN,
  BUILD_CONSUME,
  BUILD_PRODUCE,
  SALE_ISSUE
}
