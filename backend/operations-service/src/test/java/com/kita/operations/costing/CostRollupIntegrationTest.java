package com.kita.operations.costing;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.operations.bom.BomService.ComponentSpec;
import com.kita.operations.bom.BomService;
import com.kita.operations.bom.BomType;
import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.ItemType;
import com.kita.operations.catalog.UomFamily;
import com.kita.operations.inventory.InventoryService;
import com.kita.operations.inventory.StockLocation;
import com.kita.operations.procurement.GoodsReceiptService;
import com.kita.operations.procurement.GoodsReceiptService.ReceiptLineSpec;
import com.kita.operations.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T055: multi-level BOM cost roll-up + margin. */
class CostRollupIntegrationTest extends AbstractIntegrationTest {

  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired GoodsReceiptService receipts;
  @Autowired BomService bom;
  @Autowired CostingService costing;

  @Test
  void multiLevelRollupAndMargin() {
    catalog.createUom("ea", UomFamily.COUNT);
    StockLocation loc = inventory.createLocation("WH", "WH");
    Item b = catalog.createItem("B", "B", ItemType.RAW_MATERIAL, "ea", null, false);
    Item a = catalog.createItem("A", "A", ItemType.COMPONENT, "ea", null, false);
    Item p = catalog.createItem("P", "P", ItemType.FINISHED_GOOD, "ea", null, false);
    // Set B unit cost = 10 via a receipt (AVCO)
    receipts.post("acme", loc.getId(),
        List.of(new ReceiptLineSpec(b.getId(), null, null, BigDecimal.ONE, null, new BigDecimal("10"))));
    bom.create(a.getId(), BomType.MANUFACTURED, BigDecimal.ONE,
        List.of(new ComponentSpec(b.getId(), new BigDecimal("3"), "ea")));
    bom.create(p.getId(), BomType.MANUFACTURED, BigDecimal.ONE,
        List.of(new ComponentSpec(a.getId(), new BigDecimal("2"), "ea")));

    assertThat(costing.rolledUpCost(p.getId())).isEqualByComparingTo("60"); // 2 * 3 * 10

    var margin = costing.margin(p.getId(), new BigDecimal("100"));
    assertThat(margin.profit()).isEqualByComparingTo("40");
    assertThat(margin.profitPercent()).isEqualByComparingTo("0.4");
  }
}
