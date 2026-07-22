package com.kita.operations.sales;

import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.UomConversionService;
import com.kita.operations.common.AuditWriter;
import com.kita.operations.common.DomainException;
import com.kita.operations.party.PartyClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sales-order lifecycle: create → confirm (reserve) → fulfill (issue) / cancel (release). */
@Service
public class SalesOrderService {

  /** A requested order line (quantity in the given UoM, converted to the item's base on create). */
  public record LineRequest(UUID itemId, BigDecimal quantity, String uom, BigDecimal unitPrice) {}

  private final SalesOrderRepository orders;
  private final CatalogService catalog;
  private final UomConversionService uomConversion;
  private final ReservationService reservations;
  private final PartyClient party;
  private final AuditWriter audit;

  public SalesOrderService(
      SalesOrderRepository orders,
      CatalogService catalog,
      UomConversionService uomConversion,
      ReservationService reservations,
      PartyClient party,
      AuditWriter audit) {
    this.orders = orders;
    this.catalog = catalog;
    this.uomConversion = uomConversion;
    this.reservations = reservations;
    this.party = party;
    this.audit = audit;
  }

  @Transactional
  public SalesOrder create(String customerRef, List<LineRequest> lineRequests) {
    validateCustomer(customerRef);
    if (lineRequests == null || lineRequests.isEmpty()) {
      throw new DomainException.Validation("A sales order must have at least one line");
    }
    SalesOrder order = new SalesOrder(customerRef);
    for (LineRequest lr : lineRequests) {
      Item item = catalog.requireItem(lr.itemId());
      BigDecimal baseQty =
          lr.uom() == null
              ? lr.quantity()
              : uomConversion.convert(lr.quantity(), lr.uom(), item.getBaseUom().getCode());
      if (baseQty.signum() <= 0) {
        throw new DomainException.Validation("Line quantity must be positive");
      }
      order.addLine(new SalesOrderLine(item, baseQty, lr.unitPrice()));
    }
    return orders.save(order);
  }

  @Transactional
  public SalesOrder confirm(UUID orderId) {
    SalesOrder order = require(orderId);
    if (order.getStatus() != OrderStatus.DRAFT) {
      throw new DomainException.Validation("Only a DRAFT order can be confirmed");
    }
    validateCustomer(order.getCustomerRef());
    // Reserve every line; if any is short the whole transaction rolls back (no partial reserve).
    for (SalesOrderLine line : order.getLines()) {
      reservations.reserve(line);
    }
    order.setStatus(OrderStatus.CONFIRMED);
    order.setConfirmedAt(Instant.now());
    SalesOrder saved = orders.save(order);
    // FR-021 asks for the event history; 003 specifies no caller identity, and this service has no
    // security model, so the actor stays null rather than being fabricated.
    audit.record(null, "ORDER_CONFIRMED", orderId.toString(), "lines=" + order.getLines().size());
    return saved;
  }

  @Transactional
  public SalesOrder fulfill(UUID orderId) {
    SalesOrder order = require(orderId);
    if (order.getStatus() != OrderStatus.CONFIRMED) {
      throw new DomainException.Validation("Only a CONFIRMED order can be fulfilled");
    }
    for (SalesOrderLine line : order.getLines()) {
      reservations.fulfill(line);
    }
    order.setStatus(OrderStatus.FULFILLED);
    order.setFulfilledAt(Instant.now());
    SalesOrder saved = orders.save(order);
    audit.record(null, "ORDER_FULFILLED", orderId.toString(), "lines=" + order.getLines().size());
    return saved;
  }

  @Transactional
  public SalesOrder cancel(UUID orderId) {
    SalesOrder order = require(orderId);
    if (order.getStatus() == OrderStatus.FULFILLED || order.getStatus() == OrderStatus.CLOSED) {
      throw new DomainException.Validation("A fulfilled/closed order cannot be cancelled");
    }
    for (SalesOrderLine line : order.getLines()) {
      reservations.release(line);
    }
    order.setStatus(OrderStatus.CANCELLED);
    SalesOrder saved = orders.save(order);
    audit.record(null, "ORDER_CANCELLED", orderId.toString(), "lines=" + order.getLines().size());
    return saved;
  }

  @Transactional(readOnly = true)
  public SalesOrder get(UUID orderId) {
    SalesOrder order = require(orderId);
    order.getLines().size(); // initialize lazy lines within the transaction
    return order;
  }

  @Transactional(readOnly = true)
  public List<SalesOrder> list() {
    List<SalesOrder> all = orders.findAll();
    all.forEach(o -> o.getLines().size()); // initialize lazy lines within the transaction
    return all;
  }

  private SalesOrder require(UUID orderId) {
    return orders
        .findById(orderId)
        .orElseThrow(() -> new DomainException.NotFound("Sales order not found: " + orderId));
  }

  private void validateCustomer(String customerRef) {
    if (!party.validateCustomer(customerRef).isValid()) {
      throw new DomainException.Validation("Unknown or inactive customer: " + customerRef);
    }
  }
}
