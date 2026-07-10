package com.kita.operations.sales;

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
import com.kita.operations.sales.SalesOrderService.LineRequest;
import com.kita.operations.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T039/T040/T041: selling a KIT consumes exploded components (not finished stock). */
class KitSaleIntegrationTest extends AbstractIntegrationTest {

  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired BomService bom;
  @Autowired SalesOrderService sales;

  private StockLocation loc;

  private Item item(String sku, String baseUom) {
    return catalog.createItem(sku, sku, ItemType.COMPONENT, baseUom, null, false);
  }

  private void seed(Item item, String qty, String uom) {
    inventory.postAdjustment(item.getId(), loc.getId(), null, new BigDecimal(qty), uom, "seed");
  }

  private BigDecimal onHand(Item item) {
    return inventory.availabilityForItem(item.getId()).get(0).getOnHand();
  }

  private void sellOne(String customer, UUID kitItemId) {
    UUID order =
        sales.create(customer, List.of(new LineRequest(kitItemId, BigDecimal.ONE, null, new BigDecimal("1")))).getId();
    sales.confirm(order);
    sales.fulfill(order);
  }

  @Test
  void electricalSetDeductsComponents() {
    catalog.createUom("ea", UomFamily.COUNT);
    loc = inventory.createLocation("WH", "WH");
    Item enclosure = item("ENCLOSURE", "ea");
    Item breaker = item("BREAKER", "ea");
    Item set = catalog.createItem("SET", "Set", ItemType.KIT, "ea", null, false);
    bom.create(set.getId(), BomType.KIT, BigDecimal.ONE,
        List.of(new ComponentSpec(enclosure.getId(), BigDecimal.ONE, "ea"),
            new ComponentSpec(breaker.getId(), new BigDecimal("4"), "ea")));
    seed(enclosure, "10", null);
    seed(breaker, "20", null);

    sellOne("acme", set.getId());

    assertThat(onHand(enclosure)).isEqualByComparingTo("9");
    assertThat(onHand(breaker)).isEqualByComparingTo("16");
    assertThat(inventory.availabilityForItem(set.getId())).isEmpty(); // kit itself not stocked
  }

  @Test
  void tapsilogRecipeDeductsIngredientsWithUomConversion() {
    catalog.createUom("g", UomFamily.MASS);
    catalog.createUom("kg", UomFamily.MASS);
    catalog.createConversion("kg", "g", new BigDecimal("1000"));
    catalog.createUom("pcs", UomFamily.COUNT);
    catalog.createUom("tray", UomFamily.COUNT);
    catalog.createConversion("tray", "pcs", new BigDecimal("30"));
    loc = inventory.createLocation("KITCHEN", "Kitchen");
    Item rice = item("RICE", "g");
    Item tapa = item("TAPA", "g");
    Item egg = item("EGG", "pcs");
    Item tapsilog = catalog.createItem("TAPSILOG", "Tapsilog", ItemType.KIT, "pcs", null, false);
    bom.create(tapsilog.getId(), BomType.KIT, BigDecimal.ONE,
        List.of(new ComponentSpec(rice.getId(), new BigDecimal("250"), "g"),
            new ComponentSpec(tapa.getId(), new BigDecimal("200"), "g"),
            new ComponentSpec(egg.getId(), BigDecimal.ONE, "pcs")));
    seed(rice, "1000", "g");
    seed(tapa, "1", "kg");   // 1 kg -> 1000 g
    seed(egg, "1", "tray");  // 1 tray -> 30 pcs

    sellOne("acme", tapsilog.getId());

    assertThat(onHand(rice)).isEqualByComparingTo("750");
    assertThat(onHand(tapa)).isEqualByComparingTo("800");
    assertThat(onHand(egg)).isEqualByComparingTo("29");
  }

  @Test
  void kitSaleRejectedWhenComponentShort() {
    catalog.createUom("ea", UomFamily.COUNT);
    loc = inventory.createLocation("WH", "WH");
    Item enclosure = item("ENC2", "ea");
    Item breaker = item("BRK2", "ea");
    Item set = catalog.createItem("SET2", "Set", ItemType.KIT, "ea", null, false);
    bom.create(set.getId(), BomType.KIT, BigDecimal.ONE,
        List.of(new ComponentSpec(enclosure.getId(), BigDecimal.ONE, "ea"),
            new ComponentSpec(breaker.getId(), new BigDecimal("4"), "ea")));
    seed(enclosure, "10", null);
    seed(breaker, "2", null); // only 2, need 4

    UUID order =
        sales.create("acme", List.of(new LineRequest(set.getId(), BigDecimal.ONE, null, new BigDecimal("1")))).getId();
    assertThatThrownBy(() -> sales.confirm(order)).isInstanceOf(DomainException.Conflict.class);
  }
}
