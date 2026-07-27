package com.kita.workflow.ports;

import java.math.BigDecimal;
import java.util.List;

/**
 * Boundary to operations-service (FR-004, FR-006). operations-service owns stock, reservations and
 * costing — this port only drives it. NB: operations creates a sales order <em>atomically with its
 * lines</em> (there is no add-line endpoint), and items are identified by UUID on the wire — the http
 * adapter resolves each {@link SalesLine#itemId()} ref (sku) to its UUID (018 FR-001/002).
 */
public interface OperationsPort {

  /**
   * Create the sales order for the customer with all its lines in one call (atomic); returns the
   * operations order id (durable anchor). {@code customerRef} is the customer reference operations
   * stores; each line's item ref is resolved to a UUID by the adapter.
   */
  String createSalesOrder(String customerRef, List<SalesLine> lines);

  /** Confirm the order, reserving stock. Throws on oversell (surfaced as 422). */
  void confirmSalesOrder(String salesOrderId);

  /** Fulfill the order, committing reserved stock (on release). */
  void fulfillSalesOrder(String salesOrderId);

  /** Cancel the order, releasing any reservation (compensation). */
  void cancelSalesOrder(String salesOrderId);

  /** Current availability for an item (optional pre-check). */
  Availability availability(String itemId);

  /**
   * Build a finished item: operations atomically explodes the BOM, consumes components and raises
   * finished stock. Insufficient components → 422 with nothing consumed (US5, FR-012/013).
   */
  BuildResult build(String itemId, BigDecimal quantity);

  record SalesLine(String itemId, BigDecimal quantity, BigDecimal unitPrice) {}

  record Availability(String itemId, BigDecimal onHand, BigDecimal available) {}

  record BuildResult(String buildId, BigDecimal produced) {}
}
