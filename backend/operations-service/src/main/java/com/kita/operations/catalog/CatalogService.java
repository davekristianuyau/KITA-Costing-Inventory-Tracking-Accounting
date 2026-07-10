package com.kita.operations.catalog;

import com.kita.operations.common.DomainException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog operations: items, units of measure, and conversions. */
@Service
public class CatalogService {

  private final ItemRepository items;
  private final UnitOfMeasureRepository uoms;
  private final UomConversionRepository conversions;

  public CatalogService(
      ItemRepository items, UnitOfMeasureRepository uoms, UomConversionRepository conversions) {
    this.items = items;
    this.uoms = uoms;
    this.conversions = conversions;
  }

  @Transactional
  public Item createItem(
      String sku,
      String name,
      ItemType type,
      String baseUomCode,
      ValuationMethod valuationMethod,
      boolean perishable) {
    if (items.existsBySku(sku)) {
      throw new DomainException.Validation("Item with SKU already exists: " + sku);
    }
    UnitOfMeasure baseUom =
        uoms.findByCode(baseUomCode)
            .orElseThrow(
                () -> new DomainException.Validation("Unknown base unit of measure: " + baseUomCode));
    Item item = new Item(sku, name, type, baseUom);
    if (valuationMethod != null) {
      item.setValuationMethod(valuationMethod);
    }
    item.setPerishable(perishable);
    return items.save(item);
  }

  public List<Item> listItems() {
    return items.findAll();
  }

  public Item requireItem(java.util.UUID id) {
    return items.findById(id).orElseThrow(() -> new DomainException.NotFound("Item not found: " + id));
  }

  @Transactional
  public UnitOfMeasure createUom(String code, UomFamily family) {
    if (uoms.existsByCode(code)) {
      throw new DomainException.Validation("Unit of measure already exists: " + code);
    }
    return uoms.save(new UnitOfMeasure(code, family));
  }

  @Transactional
  public UomConversion createConversion(String fromCode, String toCode, BigDecimal factor) {
    UnitOfMeasure from =
        uoms.findByCode(fromCode)
            .orElseThrow(() -> new DomainException.Validation("Unknown unit: " + fromCode));
    UnitOfMeasure to =
        uoms.findByCode(toCode)
            .orElseThrow(() -> new DomainException.Validation("Unknown unit: " + toCode));
    if (from.getFamily() != to.getFamily()) {
      throw new DomainException.Validation("Conversion must be within one unit family");
    }
    if (factor.signum() <= 0) {
      throw new DomainException.Validation("Conversion factor must be positive");
    }
    return conversions.save(new UomConversion(from, to, factor));
  }
}
