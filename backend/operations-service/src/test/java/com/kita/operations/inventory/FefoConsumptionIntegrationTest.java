package com.kita.operations.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.ItemType;
import com.kita.operations.catalog.UomFamily;
import com.kita.operations.catalog.ValuationMethod;
import com.kita.operations.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T057: FIFO item consumed earliest-expiry-first; expired lots excluded. */
class FefoConsumptionIntegrationTest extends AbstractIntegrationTest {

  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired ConsumptionService consumption;
  @Autowired LotRepository lots;
  @Autowired StockLevelRepository levels;

  @Test
  void consumesEarliestExpiryFirstSkippingExpired() {
    catalog.createUom("ea", UomFamily.COUNT);
    Item item = catalog.createItem("PERISH", "P", ItemType.RAW_MATERIAL, "ea", ValuationMethod.FIFO, true);
    StockLocation loc = inventory.createLocation("COLD", "Cold");

    Lot a = lots.save(new Lot(item, "A", LocalDate.now().plusDays(10), new BigDecimal("5")));
    Lot b = lots.save(new Lot(item, "B", LocalDate.now().plusDays(20), new BigDecimal("7")));
    Lot c = lots.save(new Lot(item, "C", LocalDate.now().minusDays(1), new BigDecimal("3"))); // expired
    inventory.postAdjustment(item.getId(), loc.getId(), a.getId(), new BigDecimal("10"), null, "seed");
    inventory.postAdjustment(item.getId(), loc.getId(), b.getId(), new BigDecimal("10"), null, "seed");
    inventory.postAdjustment(item.getId(), loc.getId(), c.getId(), new BigDecimal("5"), null, "seed");

    consumption.consumeFefo(item, loc, new BigDecimal("15"), MovementType.ISSUE, "test");

    assertThat(onHandForLot(item, a.getId())).isEqualByComparingTo("0");   // fully drawn (earliest)
    assertThat(onHandForLot(item, b.getId())).isEqualByComparingTo("5");   // 5 remaining
    assertThat(onHandForLot(item, c.getId())).isEqualByComparingTo("5");   // expired, untouched
  }

  private BigDecimal onHandForLot(Item item, UUID lotId) {
    return levels.findByItem(item).stream()
        .filter(l -> l.getLot() != null && l.getLot().getId().equals(lotId))
        .findFirst()
        .orElseThrow()
        .getOnHand();
  }
}
