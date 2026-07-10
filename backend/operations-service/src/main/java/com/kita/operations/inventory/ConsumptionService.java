package com.kita.operations.inventory;

import com.kita.operations.catalog.Item;
import com.kita.operations.common.DomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consume stock earliest-expiry-first (FEFO), costing each draw at its lot cost; skips expired. */
@Service
public class ConsumptionService {

  private final StockLevelRepository levels;
  private final StockLedgerService ledger;

  public ConsumptionService(StockLevelRepository levels, StockLedgerService ledger) {
    this.levels = levels;
    this.ledger = ledger;
  }

  @Transactional
  public void consumeFefo(
      Item item, StockLocation location, BigDecimal qty, MovementType type, String sourceId) {
    List<StockLevel> eligible =
        levels.lockByItemAndLocation(item, location).stream()
            .filter(l -> l.getOnHand().signum() > 0)
            .filter(l -> !isExpired(l))
            .sorted(
                Comparator.comparing(
                    l -> l.getLot() == null ? null : l.getLot().getExpiryDate(),
                    Comparator.nullsLast(LocalDate::compareTo)))
            .toList();

    BigDecimal available =
        eligible.stream().map(StockLevel::getAvailable).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (available.compareTo(qty) < 0) {
      throw new DomainException.Conflict(
          "Insufficient non-expired stock for " + item.getSku() + " at " + location.getCode());
    }

    BigDecimal remaining = qty;
    for (StockLevel level : eligible) {
      if (remaining.signum() <= 0) {
        break;
      }
      BigDecimal take = level.getOnHand().min(remaining);
      BigDecimal cost =
          level.getLot() != null && level.getLot().getUnitCost() != null
              ? level.getLot().getUnitCost()
              : (item.getStandardCost() == null ? BigDecimal.ZERO : item.getStandardCost());
      ledger.apply(item, location, level.getLot(), type, take.negate(), cost, "FEFO consume",
          "CONSUMPTION", sourceId);
      remaining = remaining.subtract(take);
    }
  }

  private boolean isExpired(StockLevel level) {
    return level.getLot() != null
        && level.getLot().getExpiryDate() != null
        && level.getLot().getExpiryDate().isBefore(LocalDate.now());
  }
}
