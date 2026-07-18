package com.kita.operations.catalog;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cache-friendly read model for a catalog item (008-docker-cache-database). Held in the shared cache as
 * a copy of the item's read fields — never authoritative. {@link Serializable} so the default
 * (JDK-serialization) Redis cache round-trips it cleanly.
 */
public record ItemView(
    UUID id,
    String sku,
    String name,
    ItemType type,
    String baseUomCode,
    ValuationMethod valuationMethod,
    boolean perishable,
    BigDecimal standardCost)
    implements Serializable {

  public static ItemView of(Item item) {
    return new ItemView(
        item.getId(),
        item.getSku(),
        item.getName(),
        item.getType(),
        item.getBaseUom().getCode(),
        item.getValuationMethod(),
        item.isPerishable(),
        item.getStandardCost());
  }
}
