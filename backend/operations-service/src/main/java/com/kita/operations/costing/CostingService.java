package com.kita.operations.costing;

import com.kita.operations.bom.BillOfMaterials;
import com.kita.operations.bom.BillOfMaterialsRepository;
import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.UomConversionService;
import com.kita.operations.common.DomainException;
import com.kita.operations.inventory.Lot;
import com.kita.operations.inventory.LotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BOM cost roll-up and margin. */
@Service
public class CostingService {

  public record CostMargin(
      UUID itemId, BigDecimal unitCost, BigDecimal salePrice, BigDecimal profit, BigDecimal profitPercent) {}

  private final CatalogService catalog;
  private final BillOfMaterialsRepository boms;
  private final LotRepository lots;
  private final UomConversionService uomConversion;

  public CostingService(
      CatalogService catalog,
      BillOfMaterialsRepository boms,
      LotRepository lots,
      UomConversionService uomConversion) {
    this.catalog = catalog;
    this.boms = boms;
    this.uomConversion = uomConversion;
    this.lots = lots;
  }

  public static BigDecimal profit(BigDecimal salePrice, BigDecimal cost) {
    return salePrice.subtract(cost);
  }

  public static BigDecimal profitPercent(BigDecimal salePrice, BigDecimal cost) {
    if (salePrice.signum() == 0) {
      throw new DomainException.Validation("Sale price must be non-zero to compute margin percent");
    }
    return salePrice.subtract(cost).divide(salePrice, 6, RoundingMode.HALF_UP);
  }

  @Transactional(readOnly = true)
  public BigDecimal rolledUpCost(UUID itemId) {
    Item item = catalog.requireItem(itemId);
    Optional<BillOfMaterials> bom = boms.findByParentItemAndActiveTrue(item);
    if (bom.isEmpty()) {
      return unitCostFor(item);
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (var c : bom.get().getComponents()) {
      BigDecimal baseQty =
          uomConversion.convert(
              c.getQuantity(), c.getUom(), c.getComponentItem().getBaseUom().getCode());
      sum = sum.add(baseQty.multiply(rolledUpCost(c.getComponentItem().getId())));
    }
    return sum.divide(bom.get().getOutputQuantity(), 6, RoundingMode.HALF_UP);
  }

  @Transactional(readOnly = true)
  public CostMargin margin(UUID itemId, BigDecimal salePrice) {
    BigDecimal cost = rolledUpCost(itemId);
    if (salePrice == null) {
      return new CostMargin(itemId, cost, null, null, null);
    }
    return new CostMargin(itemId, cost, salePrice, profit(salePrice, cost), profitPercent(salePrice, cost));
  }

  /** AVCO items use the running average; FIFO items use the earliest-expiry lot's cost. */
  private BigDecimal unitCostFor(Item item) {
    if (item.getStandardCost() != null) {
      return item.getStandardCost();
    }
    return lots.findByItem(item).stream()
        .filter(l -> l.getUnitCost() != null)
        .min(Comparator.comparing(Lot::getExpiryDate, Comparator.nullsLast(LocalDate::compareTo)))
        .map(Lot::getUnitCost)
        .orElse(BigDecimal.ZERO);
  }
}
