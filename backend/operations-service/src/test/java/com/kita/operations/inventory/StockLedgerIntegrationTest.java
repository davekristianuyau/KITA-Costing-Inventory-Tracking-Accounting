package com.kita.operations.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.ItemType;
import com.kita.operations.catalog.UomFamily;
import com.kita.operations.common.DomainException;
import com.kita.operations.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T013/T015: reconciliation (on-hand = Σ movements) and the no-negative-stock invariant. */
class StockLedgerIntegrationTest extends AbstractIntegrationTest {

  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired StockMovementRepository movements;

  private Item seedItemAndStock() {
    catalog.createUom("pcs", UomFamily.COUNT);
    Item item = catalog.createItem("SKU-1", "Widget", ItemType.FINISHED_GOOD, "pcs", null, false);
    inventory.createLocation("L1", "Main");
    return item;
  }

  @Test
  void onHandReconcilesWithMovements() {
    Item item = seedItemAndStock();
    var loc = inventory.createLocation("L2", "Secondary");
    inventory.postAdjustment(item.getId(), loc.getId(), null, new BigDecimal("10"), null, "seed");
    inventory.postAdjustment(item.getId(), loc.getId(), null, new BigDecimal("5"), null, "seed2");

    BigDecimal onHand = inventory.availabilityForItem(item.getId()).get(0).getOnHand();
    BigDecimal sumMovements =
        movements.findByItemOrderByOccurredAtAsc(item).stream()
            .map(StockMovement::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    assertThat(onHand).isEqualByComparingTo("15");
    assertThat(onHand).isEqualByComparingTo(sumMovements);
  }

  @Test
  void rejectsNegativeStock() {
    Item item = seedItemAndStock();
    var loc = inventory.createLocation("L3", "Third");
    inventory.postAdjustment(item.getId(), loc.getId(), null, new BigDecimal("3"), null, "seed");
    assertThatThrownBy(
            () ->
                inventory.postAdjustment(
                    item.getId(), loc.getId(), null, new BigDecimal("-10"), null, "over-issue"))
        .isInstanceOf(DomainException.Conflict.class);
  }
}
