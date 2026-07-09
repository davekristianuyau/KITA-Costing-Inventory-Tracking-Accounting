package com.kita.operations.costing;

import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.ItemRepository;
import com.kita.operations.catalog.ValuationMethod;
import com.kita.operations.inventory.StockLevel;
import com.kita.operations.inventory.StockLevelRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inventory valuation. AVCO maintains a running average unit cost on the item. */
@Service
public class ValuationService {

  private final ItemRepository items;
  private final StockLevelRepository levels;

  public ValuationService(ItemRepository items, StockLevelRepository levels) {
    this.items = items;
    this.levels = levels;
  }

  /** Weighted-average unit cost after receiving {@code qty} at {@code cost}. */
  public static BigDecimal averageCost(
      BigDecimal prevOnHand, BigDecimal prevAvg, BigDecimal qty, BigDecimal cost) {
    BigDecimal total = prevOnHand.add(qty);
    if (total.signum() <= 0) {
      return cost;
    }
    BigDecimal prev = prevAvg == null ? BigDecimal.ZERO : prevAvg;
    return prevOnHand.multiply(prev).add(qty.multiply(cost)).divide(total, 6, RoundingMode.HALF_UP);
  }

  /** Recompute the item's AVCO average on receipt (before the receipt's stock is added). */
  @Transactional
  public void applyReceiptCost(Item item, BigDecimal baseQty, BigDecimal unitCost) {
    if (item.getValuationMethod() != ValuationMethod.AVCO) {
      return; // FIFO items carry cost per lot
    }
    BigDecimal onHand =
        levels.findByItem(item).stream()
            .map(StockLevel::getOnHand)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    item.setStandardCost(averageCost(onHand, item.getStandardCost(), baseQty, unitCost));
    items.save(item);
  }
}
