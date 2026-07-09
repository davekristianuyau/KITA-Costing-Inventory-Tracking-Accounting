package com.kita.operations.inventory;

import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.UomConversionService;
import com.kita.operations.common.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Location management, stock adjustments, and availability/movement queries. */
@Service
public class InventoryService {

  private final StockLocationRepository locations;
  private final StockLevelRepository levels;
  private final StockMovementRepository movements;
  private final LotRepository lots;
  private final CatalogService catalog;
  private final UomConversionService uomConversion;
  private final StockLedgerService ledger;

  public InventoryService(
      StockLocationRepository locations,
      StockLevelRepository levels,
      StockMovementRepository movements,
      LotRepository lots,
      CatalogService catalog,
      UomConversionService uomConversion,
      StockLedgerService ledger) {
    this.locations = locations;
    this.levels = levels;
    this.movements = movements;
    this.lots = lots;
    this.catalog = catalog;
    this.uomConversion = uomConversion;
    this.ledger = ledger;
  }

  @Transactional
  public StockLocation createLocation(String code, String name) {
    if (locations.findByCode(code).isPresent()) {
      throw new DomainException.Validation("Location already exists: " + code);
    }
    return locations.save(new StockLocation(code, name));
  }

  private StockLocation requireLocation(UUID id) {
    return locations
        .findById(id)
        .orElseThrow(() -> new DomainException.NotFound("Location not found: " + id));
  }

  /** Post a signed adjustment (converted to the item's base UoM if a unit is supplied). */
  @Transactional
  public StockMovement postAdjustment(
      UUID itemId, UUID locationId, UUID lotId, BigDecimal quantity, String uom, String reason) {
    Item item = catalog.requireItem(itemId);
    StockLocation location = requireLocation(locationId);
    Lot lot = lotId == null ? null : lots.findById(lotId).orElse(null);
    BigDecimal baseQty =
        uom == null ? quantity : uomConversion.convert(quantity, uom, item.getBaseUom().getCode());
    return ledger.apply(
        item, location, lot, MovementType.ADJUSTMENT, baseQty, BigDecimal.ZERO, reason, "ADJUSTMENT",
        null);
  }

  public List<StockLevel> availabilityForItem(UUID itemId) {
    return levels.findByItem(catalog.requireItem(itemId));
  }

  public List<StockMovement> movementsForItem(UUID itemId, Instant from, Instant to) {
    Item item = catalog.requireItem(itemId);
    if (from != null && to != null) {
      return movements.findByItemAndOccurredAtBetweenOrderByOccurredAtAsc(item, from, to);
    }
    return movements.findByItemOrderByOccurredAtAsc(item);
  }
}
