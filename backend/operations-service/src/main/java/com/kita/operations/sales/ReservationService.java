package com.kita.operations.sales;

import com.kita.operations.bom.BillOfMaterials;
import com.kita.operations.bom.BillOfMaterialsRepository;
import com.kita.operations.bom.BomService;
import com.kita.operations.bom.BomType;
import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.common.DomainException;
import com.kita.operations.inventory.MovementType;
import com.kita.operations.inventory.StockLedgerService;
import com.kita.operations.inventory.StockLevel;
import com.kita.operations.inventory.StockLevelRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserve / release / fulfill stock for sales lines. A KIT line consumes its exploded components
 * (not a finished-good count); a stocked line reserves the item itself. Pessimistic locks prevent
 * overselling (SC-002).
 */
@Service
public class ReservationService {

  private final StockLevelRepository levels;
  private final ReservationRepository reservations;
  private final StockLedgerService ledger;
  private final BillOfMaterialsRepository boms;
  private final BomService bomService;
  private final CatalogService catalog;

  public ReservationService(
      StockLevelRepository levels,
      ReservationRepository reservations,
      StockLedgerService ledger,
      BillOfMaterialsRepository boms,
      BomService bomService,
      CatalogService catalog) {
    this.levels = levels;
    this.reservations = reservations;
    this.ledger = ledger;
    this.boms = boms;
    this.bomService = bomService;
    this.catalog = catalog;
  }

  @Transactional
  public void reserve(SalesOrderLine line) {
    Item item = line.getItem();
    BigDecimal need = line.getQuantity();
    Optional<BillOfMaterials> bom = boms.findByParentItemAndActiveTrue(item);
    if (bom.isPresent() && bom.get().getType() == BomType.KIT) {
      for (BomService.ComponentRequirement r : bomService.explode(item.getId(), need)) {
        reserveItemQuantity(line, catalog.requireItem(r.componentItemId()), r.requiredQuantity());
      }
    } else {
      reserveItemQuantity(line, item, need);
    }
    line.setReservedQty(need);
  }

  private void reserveItemQuantity(SalesOrderLine line, Item item, BigDecimal need) {
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
  }

  @Transactional
  public void release(SalesOrderLine line) {
    for (Reservation r : reservations.findByOrderLine(line)) {
      decrementReserved(r);
      reservations.delete(r);
    }
    line.setReservedQty(BigDecimal.ZERO);
  }

  @Transactional
  public void fulfill(SalesOrderLine line) {
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
      reservations.delete(r);
    }
    line.setFulfilledQty(line.getFulfilledQty().add(line.getReservedQty()));
    line.setReservedQty(BigDecimal.ZERO);
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
