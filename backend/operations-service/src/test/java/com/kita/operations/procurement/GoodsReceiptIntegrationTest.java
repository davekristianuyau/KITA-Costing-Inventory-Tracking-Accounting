package com.kita.operations.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.ItemType;
import com.kita.operations.catalog.UomFamily;
import com.kita.operations.common.DomainException;
import com.kita.operations.inventory.InventoryService;
import com.kita.operations.inventory.StockLocation;
import com.kita.operations.procurement.GoodsReceiptService.ReceiptLineSpec;
import com.kita.operations.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T044: receipts increase on-hand, recompute AVCO, and reject invalid suppliers. */
class GoodsReceiptIntegrationTest extends AbstractIntegrationTest {

  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired GoodsReceiptService receipts;

  @Test
  void receiptIncreasesStockAndRecomputesAverageCost() {
    catalog.createUom("ea", UomFamily.COUNT);
    Item item = catalog.createItem("SKU-RCV", "W", ItemType.RAW_MATERIAL, "ea", null, false);
    StockLocation loc = inventory.createLocation("WH", "WH");

    receipts.post("acme-supplier", loc.getId(),
        List.of(new ReceiptLineSpec(item.getId(), null, null, new BigDecimal("10"), null, new BigDecimal("5"))));
    assertThat(inventory.availabilityForItem(item.getId()).get(0).getOnHand()).isEqualByComparingTo("10");
    assertThat(catalog.requireItem(item.getId()).getStandardCost()).isEqualByComparingTo("5");

    receipts.post("acme-supplier", loc.getId(),
        List.of(new ReceiptLineSpec(item.getId(), null, null, new BigDecimal("10"), null, new BigDecimal("7"))));
    assertThat(inventory.availabilityForItem(item.getId()).get(0).getOnHand()).isEqualByComparingTo("20");
    assertThat(catalog.requireItem(item.getId()).getStandardCost()).isEqualByComparingTo("6");
  }

  @Test
  void invalidSupplierRejected() {
    catalog.createUom("ea", UomFamily.COUNT);
    Item item = catalog.createItem("SKU-RCV2", "W", ItemType.RAW_MATERIAL, "ea", null, false);
    StockLocation loc = inventory.createLocation("WH", "WH");
    assertThatThrownBy(
            () ->
                receipts.post("invalid", loc.getId(),
                    List.of(new ReceiptLineSpec(item.getId(), null, null, BigDecimal.ONE, null, BigDecimal.ONE))))
        .isInstanceOf(DomainException.Validation.class);
  }
}
