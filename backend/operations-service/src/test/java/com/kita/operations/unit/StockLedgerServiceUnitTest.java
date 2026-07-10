package com.kita.operations.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.ItemType;
import com.kita.operations.catalog.UnitOfMeasure;
import com.kita.operations.catalog.UomFamily;
import com.kita.operations.common.DomainException;
import com.kita.operations.inventory.LotRepository;
import com.kita.operations.inventory.StockLevelRepository;
import com.kita.operations.inventory.StockMovementRepository;
import com.kita.operations.inventory.MovementType;
import com.kita.operations.inventory.StockLedgerService;
import com.kita.operations.inventory.StockLevel;
import com.kita.operations.inventory.StockLocation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Docker-free unit test of the stock ledger: on-hand update + no-negative-stock (T015 logic). */
@ExtendWith(MockitoExtension.class)
class StockLedgerServiceUnitTest {

  @Mock StockLevelRepository levels;
  @Mock StockMovementRepository movements;
  @Mock LotRepository lots;

  private Item item() {
    return new Item("SKU-1", "Widget", ItemType.FINISHED_GOOD, new UnitOfMeasure("pcs", UomFamily.COUNT));
  }

  @Test
  void increasesOnHandAndRecordsMovement() {
    StockLedgerService svc = new StockLedgerService(levels, movements, lots);
    Item item = item();
    StockLocation loc = new StockLocation("L1", "Main");
    StockLevel level = new StockLevel(item, loc, null);
    when(levels.lockByItemLocationLot(eq(item), eq(loc), isNull())).thenReturn(List.of(level));

    svc.apply(item, loc, null, MovementType.ADJUSTMENT, new BigDecimal("5"), BigDecimal.ZERO, "seed",
        "ADJUSTMENT", null);

    assertThat(level.getOnHand()).isEqualByComparingTo("5");
    verify(movements).save(any());
  }

  @Test
  void rejectsChangeThatWouldGoNegative() {
    StockLedgerService svc = new StockLedgerService(levels, movements, lots);
    Item item = item();
    StockLocation loc = new StockLocation("L1", "Main");
    StockLevel level = new StockLevel(item, loc, null);
    level.setOnHand(new BigDecimal("3"));
    when(levels.lockByItemLocationLot(eq(item), eq(loc), isNull())).thenReturn(List.of(level));

    assertThatThrownBy(
            () ->
                svc.apply(item, loc, null, MovementType.ISSUE, new BigDecimal("-10"), BigDecimal.ZERO,
                    "over", "TEST", null))
        .isInstanceOf(DomainException.Conflict.class);
    verify(movements, never()).save(any());
    assertThat(level.getOnHand()).isEqualByComparingTo("3");
  }
}
