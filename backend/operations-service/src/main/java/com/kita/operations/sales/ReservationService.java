package com.kita.operations.sales;

import com.kita.operations.catalog.Item;
import com.kita.operations.common.DomainException;
import com.kita.operations.inventory.MovementType;
import com.kita.operations.inventory.StockLedgerService;
import com.kita.operations.inventory.StockLevel;
import com.kita.operations.inventory.StockLevelRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserve / release / fulfill stock for sales-order lines. Uses pessimistic locks so concurrent
 * confirmations cannot reserve the same unit twice (no oversell — SC-002). All methods run inside
 * the caller's transaction.
 */
@Service
public class ReservationService {

  private final StockLevelRepository levels;
  private final ReservationRepository reservations;
  private final StockLedgerService ledger;

  public ReservationService(
      StockLevelRepository levels, ReservationRepository reservations, StockLedgerService ledger) {
    this.levels = levels;
    this.reservations = reservations;
    this.ledger = ledger;
  }

  /** Hard-reserve a line's quantity across the item's locked stock rows. Rejects if short. */
  @Transactional
  public void reserve(SalesOrderLine line) {
    Item item = line.getItem();
    BigDecimal need = line.getQuantity();
    List<StockLevel> locked = levels.lockAllByItem(item);
    BigDecimal available =
        locked.stream().map(StockLevel::getAvailable).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (available.compareTo(need) < 0) {
      throw new DomainException.Conflict(
          "Insufficient available stock for item "
              + item.getSku()
              + " (need "
              + need
              + ", available "
              + available
              + ")");
    }
    BigDecimal remaining = need;
    for (StockLevel level : locked) {
      if (remaining.signum() <= 0) {
        break;
      }
      BigDecimal avail = level.getAvailable();
      if (avail.signum() <= 0) {
        continue;
      }
      BigDecimal take = avail.min(remaining);
      level.setReserved(level.getReserved().add(take));
      levels.save(level);
      reservations.save(new Reservation(line, item, level.getLocation(), level.getLot(), take));
      remaining = remaining.subtract(take);
    }
    line.setReservedQty(line.getReservedQty().add(need));
  }

  /** Release a line's reservations (on cancel): return reserved quantity to availability. */
  @Transactional
  public void release(SalesOrderLine line) {
    for (Reservation r : reservations.findByOrderLine(line)) {
      decrementReserved(r);
      reservations.delete(r);
    }
    line.setReservedQty(BigDecimal.ZERO);
  }

  /** Fulfill a line's reservations: issue stock (decrement on-hand) and clear the reservation. */
  @Transactional
  public void fulfill(SalesOrderLine line) {
    BigDecimal fulfilled = BigDecimal.ZERO;
    for (Reservation r : reservations.findByOrderLine(line)) {
      BigDecimal unitCost =
          r.getItem().getStandardCost() == null ? BigDecimal.ZERO : r.getItem().getStandardCost();
      ledger.apply(
          r.getItem(),
          r.getLocation(),
          r.getLot(),
          MovementType.SALE_ISSUE,
          r.getQuantity().negate(),
          unitCost,
          "sale fulfillment",
          "SALES_ORDER",
          line.getOrder().getId().toString());
      decrementReserved(r);
      fulfilled = fulfilled.add(r.getQuantity());
      reservations.delete(r);
    }
    line.setFulfilledQty(line.getFulfilledQty().add(fulfilled));
    line.setReservedQty(line.getReservedQty().subtract(fulfilled).max(BigDecimal.ZERO));
  }

  private void decrementReserved(Reservation r) {
    List<StockLevel> lv =
        levels.lockByItemLocationLot(
            r.getItem(), r.getLocation(), r.getLot() == null ? null : r.getLot().getId());
    if (!lv.isEmpty()) {
      StockLevel level = lv.get(0);
      level.setReserved(level.getReserved().subtract(r.getQuantity()).max(BigDecimal.ZERO));
      levels.save(level);
    }
  }
}
