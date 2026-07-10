package com.kita.operations.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.ItemType;
import com.kita.operations.catalog.UomFamily;
import com.kita.operations.procurement.GoodsReceiptService;
import com.kita.operations.procurement.GoodsReceiptService.ReceiptLineSpec;
import com.kita.operations.sales.SalesOrderService;
import com.kita.operations.sales.SalesOrderService.LineRequest;
import com.kita.operations.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T062: availability reconciles with the movement ledger and reservations. */
class AvailabilityReconciliationTest extends AbstractIntegrationTest {

  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired GoodsReceiptService receipts;
  @Autowired SalesOrderService sales;
  @Autowired StockMovementRepository movements;

  @Test
  void onHandEqualsMovementsAndAvailableIsOnHandMinusReserved() {
    catalog.createUom("ea", UomFamily.COUNT);
    Item item = catalog.createItem("SKU-REC", "W", ItemType.FINISHED_GOOD, "ea", null, false);
    StockLocation loc = inventory.createLocation("WH", "WH");
    receipts.post("acme", loc.getId(),
        List.of(new ReceiptLineSpec(item.getId(), null, null, new BigDecimal("20"), null, new BigDecimal("5"))));

    UUID order =
        sales.create("acme", List.of(new LineRequest(item.getId(), new BigDecimal("5"), null, new BigDecimal("1")))).getId();
    sales.confirm(order);

    StockLevel level = inventory.availabilityForItem(item.getId()).get(0);
    assertThat(level.getOnHand()).isEqualByComparingTo("20");
    assertThat(level.getReserved()).isEqualByComparingTo("5");
    assertThat(level.getAvailable()).isEqualByComparingTo("15");

    BigDecimal sumMovements =
        movements.findByItemOrderByOccurredAtAsc(item).stream()
            .map(StockMovement::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(level.getOnHand()).isEqualByComparingTo(sumMovements);
  }
}
