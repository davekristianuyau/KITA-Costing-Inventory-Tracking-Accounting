package com.kita.workflow.ports.fake;

import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.OperationsPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link OperationsPort} that genuinely enforces availability, so oversell/reservation
 * behaviour is exercised for real in isolated tests. Seed stock with {@link #seedStock}.
 */
@Component
@ConditionalOnProperty(
    name = "workflow.operations.adapter",
    havingValue = "fake",
    matchIfMissing = true)
public class InMemoryOperationsAdapter implements OperationsPort {

  private enum State {
    DRAFT,
    CONFIRMED,
    FULFILLED,
    CANCELLED
  }

  private static final class Order {
    private State state = State.DRAFT;
    private final List<SalesLine> lines = new ArrayList<>();
  }

  private final ConcurrentMap<String, Order> orders = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, BigDecimal> available = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, java.util.Map<String, BigDecimal>> boms =
      new ConcurrentHashMap<>();

  @Override
  public String createSalesOrder(String customerId) {
    String id = UUID.randomUUID().toString();
    orders.put(id, new Order());
    return id;
  }

  @Override
  public void addSalesOrderLine(String salesOrderId, SalesLine line) {
    order(salesOrderId).lines.add(line);
  }

  @Override
  public synchronized void confirmSalesOrder(String salesOrderId) {
    Order order = order(salesOrderId);
    for (SalesLine line : order.lines) {
      BigDecimal have = available.getOrDefault(line.itemId(), BigDecimal.ZERO);
      if (have.compareTo(line.quantity()) < 0) {
        throw new ValidationException(
            "insufficient stock for item " + line.itemId() + " (available " + have + ")");
      }
    }
    for (SalesLine line : order.lines) {
      available.merge(line.itemId(), line.quantity().negate(), BigDecimal::add);
    }
    order.state = State.CONFIRMED;
  }

  @Override
  public void fulfillSalesOrder(String salesOrderId) {
    order(salesOrderId).state = State.FULFILLED;
  }

  @Override
  public synchronized void cancelSalesOrder(String salesOrderId) {
    Order order = order(salesOrderId);
    if (order.state == State.CONFIRMED) {
      for (SalesLine line : order.lines) {
        available.merge(line.itemId(), line.quantity(), BigDecimal::add);
      }
    }
    order.state = State.CANCELLED;
  }

  @Override
  public Availability availability(String itemId) {
    BigDecimal have = available.getOrDefault(itemId, BigDecimal.ZERO);
    return new Availability(itemId, have, have);
  }

  @Override
  public synchronized BuildResult build(String itemId, BigDecimal quantity) {
    java.util.Map<String, BigDecimal> bom = boms.get(itemId);
    if (bom == null || bom.isEmpty()) {
      throw new ValidationException("no bill of materials for item " + itemId);
    }
    // Check all components BEFORE consuming any (all-or-nothing, FR-013).
    for (var entry : bom.entrySet()) {
      BigDecimal needed = entry.getValue().multiply(quantity);
      BigDecimal have = available.getOrDefault(entry.getKey(), BigDecimal.ZERO);
      if (have.compareTo(needed) < 0) {
        throw new ValidationException("insufficient components: " + entry.getKey());
      }
    }
    for (var entry : bom.entrySet()) {
      available.merge(entry.getKey(), entry.getValue().multiply(quantity).negate(), BigDecimal::add);
    }
    available.merge(itemId, quantity, BigDecimal::add);
    return new BuildResult(UUID.randomUUID().toString(), quantity);
  }

  // --- test seams -------------------------------------------------------------------------------

  public void seedStock(String itemId, BigDecimal quantity) {
    available.put(itemId, quantity);
  }

  /** Seed a bill of materials: finished item → component→quantity-per-unit. */
  public void seedBom(String finishedItemId, java.util.Map<String, BigDecimal> components) {
    boms.put(finishedItemId, components);
  }

  public BigDecimal availableQty(String itemId) {
    return available.getOrDefault(itemId, BigDecimal.ZERO);
  }

  public boolean isCancelled(String salesOrderId) {
    Order o = orders.get(salesOrderId);
    return o != null && o.state == State.CANCELLED;
  }

  public void reset() {
    orders.clear();
    available.clear();
  }

  private Order order(String salesOrderId) {
    Order order = orders.get(salesOrderId);
    if (order == null) {
      throw new ValidationException("unknown sales order " + salesOrderId);
    }
    return order;
  }
}
