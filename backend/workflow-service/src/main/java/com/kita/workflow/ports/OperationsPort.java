package com.kita.workflow.ports;

import java.math.BigDecimal;

/**
 * Boundary to operations-service (FR-004, FR-006). MVP covers the sales-order lifecycle; builds (US5)
 * are added later. operations-service owns stock, reservations and costing — this port only drives it.
 */
public interface OperationsPort {

  /** Create an empty sales order for the customer; returns the operations order id (durable anchor). */
  String createSalesOrder(String customerId);

  /** Add a line to a draft order. */
  void addSalesOrderLine(String salesOrderId, SalesLine line);

  /** Confirm the order, reserving stock. Throws on oversell (surfaced as 422). */
  void confirmSalesOrder(String salesOrderId);

  /** Fulfill the order, committing reserved stock (on release). */
  void fulfillSalesOrder(String salesOrderId);

  /** Cancel the order, releasing any reservation (compensation). */
  void cancelSalesOrder(String salesOrderId);

  /** Current availability for an item (optional pre-check). */
  Availability availability(String itemId);

  record SalesLine(String itemId, BigDecimal quantity, BigDecimal unitPrice) {}

  record Availability(String itemId, BigDecimal onHand, BigDecimal available) {}
}
