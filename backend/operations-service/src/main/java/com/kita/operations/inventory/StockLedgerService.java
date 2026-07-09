package com.kita.operations.inventory;

import com.kita.operations.catalog.Item;
import com.kita.operations.common.DomainException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records stock movements and keeps the cached {@link StockLevel} in sync, in one transaction.
 * Enforces the no-negative-stock invariant.
 */
@Service
public class StockLedgerService {

  private final StockLevelRepository levels;
  private final StockMovementRepository movements;
  private final LotRepository lots;

  public StockLedgerService(
      StockLevelRepository levels, StockMovementRepository movements, LotRepository lots) {
    this.levels = levels;
    this.movements = movements;
    this.lots = lots;
  }

  /**
   * Apply a signed quantity change to (item, location, lot) and record a movement. Rejects a change
   * that would drive on-hand negative.
   */
  @Transactional
  public StockMovement apply(
      Item item,
      StockLocation location,
      Lot lot,
      MovementType type,
      BigDecimal signedQuantity,
      BigDecimal unitCost,
      String reason,
      String sourceType,
      String sourceId) {

    StockLevel level = findOrCreateLevel(item, location, lot);
    BigDecimal newOnHand = level.getOnHand().add(signedQuantity);
    if (newOnHand.signum() < 0) {
      throw new DomainException.Conflict(
          "Insufficient stock for item "
              + item.getSku()
              + " at location "
              + location.getCode()
              + " (on hand "
              + level.getOnHand()
              + ", change "
              + signedQuantity
              + ")");
    }
    level.setOnHand(newOnHand);
    levels.save(level);

    StockMovement movement =
        new StockMovement(
            item, location, lot, type, signedQuantity, unitCost, reason, sourceType, sourceId);
    return movements.save(movement);
  }

  private StockLevel findOrCreateLevel(Item item, StockLocation location, Lot lot) {
    List<StockLevel> locked =
        levels.lockByItemLocationLot(item, location, lot == null ? null : lot.getId());
    if (!locked.isEmpty()) {
      return locked.get(0);
    }
    return levels.save(new StockLevel(item, location, lot));
  }
}
