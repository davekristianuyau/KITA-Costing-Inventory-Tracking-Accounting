package com.kita.operations.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.operations.bom.BomService.ComponentSpec;
import com.kita.operations.bom.BomService;
import com.kita.operations.bom.BomType;
import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.ItemType;
import com.kita.operations.catalog.UomFamily;
import com.kita.operations.common.DomainException;
import com.kita.operations.inventory.InventoryService;
import com.kita.operations.inventory.StockLocation;
import com.kita.operations.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T050/T051: production build consumes components and produces stock, atomically. */
class BuildIntegrationTest extends AbstractIntegrationTest {

  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired BomService bom;
  @Autowired BuildService builds;

  private StockLocation loc;

  private Item raw(String sku, String uom) {
    return catalog.createItem(sku, sku, ItemType.RAW_MATERIAL, uom, null, false);
  }

  private BigDecimal onHand(Item item) {
    return inventory.availabilityForItem(item.getId()).get(0).getOnHand();
  }

  private Item setupDressBom() {
    catalog.createUom("m", UomFamily.LENGTH);
    catalog.createUom("spool", UomFamily.COUNT);
    catalog.createUom("pc", UomFamily.COUNT);
    loc = inventory.createLocation("FACTORY", "Factory");
    Item cloth = raw("CLOTH", "m");
    Item thread = raw("THREAD", "spool");
    Item dress = catalog.createItem("DRESS", "Dress", ItemType.FINISHED_GOOD, "pc", null, false);
    bom.create(dress.getId(), BomType.MANUFACTURED, BigDecimal.ONE,
        List.of(new ComponentSpec(cloth.getId(), new BigDecimal("1.7"), "m"),
            new ComponentSpec(thread.getId(), new BigDecimal("0.5"), "spool")));
    return dress;
  }

  @Test
  void buildConsumesComponentsAndProducesFinishedStock() {
    Item dress = setupDressBom();
    Item cloth = catalog.requireItem(catalog.listItems().stream().filter(i -> i.getSku().equals("CLOTH")).findFirst().get().getId());
    Item thread = catalog.listItems().stream().filter(i -> i.getSku().equals("THREAD")).findFirst().get();
    inventory.postAdjustment(cloth.getId(), loc.getId(), null, new BigDecimal("10"), null, "seed");
    inventory.postAdjustment(thread.getId(), loc.getId(), null, new BigDecimal("5"), null, "seed");

    builds.build(dress.getId(), loc.getId(), new BigDecimal("5"));

    assertThat(onHand(cloth)).isEqualByComparingTo("1.5");   // 10 - 8.5
    assertThat(onHand(thread)).isEqualByComparingTo("2.5");  // 5 - 2.5
    assertThat(onHand(dress)).isEqualByComparingTo("5");
  }

  @Test
  void buildWithShortComponentFailsWholesale() {
    Item dress = setupDressBom();
    Item cloth = catalog.listItems().stream().filter(i -> i.getSku().equals("CLOTH")).findFirst().get();
    Item thread = catalog.listItems().stream().filter(i -> i.getSku().equals("THREAD")).findFirst().get();
    inventory.postAdjustment(cloth.getId(), loc.getId(), null, new BigDecimal("5"), null, "seed"); // need 8.5
    inventory.postAdjustment(thread.getId(), loc.getId(), null, new BigDecimal("5"), null, "seed");

    assertThatThrownBy(() -> builds.build(dress.getId(), loc.getId(), new BigDecimal("5")))
        .isInstanceOf(DomainException.Conflict.class);

    // no partial consumption
    assertThat(onHand(cloth)).isEqualByComparingTo("5");
    assertThat(onHand(thread)).isEqualByComparingTo("5");
    assertThat(inventory.availabilityForItem(dress.getId())).isEmpty();
  }
}
