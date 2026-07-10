package com.kita.operations.production;

import com.kita.operations.bom.BillOfMaterials;
import com.kita.operations.bom.BillOfMaterialsRepository;
import com.kita.operations.bom.BomService;
import com.kita.operations.bom.BomType;
import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.common.DomainException;
import com.kita.operations.costing.ValuationService;
import com.kita.operations.inventory.MovementType;
import com.kita.operations.inventory.StockLedgerService;
import com.kita.operations.inventory.StockLocation;
import com.kita.operations.inventory.StockLocationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Produce a manufactured item: consume its BOM components and add finished stock, atomically. */
@Service
public class BuildService {

  private final BuildRepository builds;
  private final BillOfMaterialsRepository boms;
  private final BomService bomService;
  private final CatalogService catalog;
  private final StockLocationRepository locations;
  private final StockLedgerService ledger;
  private final ValuationService valuation;

  public BuildService(
      BuildRepository builds,
      BillOfMaterialsRepository boms,
      BomService bomService,
      CatalogService catalog,
      StockLocationRepository locations,
      StockLedgerService ledger,
      ValuationService valuation) {
    this.builds = builds;
    this.boms = boms;
    this.bomService = bomService;
    this.catalog = catalog;
    this.locations = locations;
    this.ledger = ledger;
    this.valuation = valuation;
  }

  @Transactional
  public Build build(UUID finishedItemId, UUID locationId, BigDecimal quantity) {
    Item finished = catalog.requireItem(finishedItemId);
    Optional<BillOfMaterials> bom = boms.findByParentItemAndActiveTrue(finished);
    if (bom.isEmpty() || bom.get().getType() != BomType.MANUFACTURED) {
      throw new DomainException.Validation(
          "No active manufactured BOM for item " + finished.getSku());
    }
    if (quantity.signum() <= 0) {
      throw new DomainException.Validation("Build quantity must be positive");
    }
    StockLocation location =
        locations
            .findById(locationId)
            .orElseThrow(() -> new DomainException.NotFound("Location not found: " + locationId));

    Build build = builds.save(new Build(finished, location, quantity, BuildStatus.COMPLETED));

    // Consume components. If any is short, ledger throws and the whole tx rolls back (no partial).
    BigDecimal totalCost = BigDecimal.ZERO;
    for (BomService.ComponentRequirement r : bomService.explode(finishedItemId, quantity)) {
      Item component = catalog.requireItem(r.componentItemId());
      BigDecimal unitCost =
          component.getStandardCost() == null ? BigDecimal.ZERO : component.getStandardCost();
      ledger.apply(
          component, location, null, MovementType.BUILD_CONSUME, r.requiredQuantity().negate(),
          unitCost, "build consume", "BUILD", build.getId().toString());
      totalCost = totalCost.add(r.requiredQuantity().multiply(unitCost));
    }

    BigDecimal finishedUnitCost = totalCost.divide(quantity, 6, RoundingMode.HALF_UP);
    valuation.applyReceiptCost(finished, quantity, finishedUnitCost); // AVCO roll-in of finished
    ledger.apply(
        finished, location, null, MovementType.BUILD_PRODUCE, quantity, finishedUnitCost,
        "build produce", "BUILD", build.getId().toString());

    return build;
  }
}
