package com.kita.operations.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.ItemType;
import com.kita.operations.catalog.UomFamily;
import com.kita.operations.inventory.InventoryService;
import com.kita.operations.inventory.StockLocation;
import com.kita.operations.sales.SalesOrderService.LineRequest;
import com.kita.operations.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T025 / SC-002: two concurrent confirmations for the last available unit — exactly one wins. */
class SalesOrderConcurrencyTest extends AbstractIntegrationTest {

  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired SalesOrderService sales;

  @Test
  void concurrentConfirmsForLastUnitDoNotOversell() throws Exception {
    catalog.createUom("pcs", UomFamily.COUNT);
    Item item = catalog.createItem("SKU-CONC", "W", ItemType.FINISHED_GOOD, "pcs", null, false);
    StockLocation loc = inventory.createLocation("WH", "WH");
    inventory.postAdjustment(item.getId(), loc.getId(), null, BigDecimal.ONE, null, "seed"); // 1 unit

    UUID o1 =
        sales.create("acme", List.of(new LineRequest(item.getId(), BigDecimal.ONE, null, new BigDecimal("1")))).getId();
    UUID o2 =
        sales.create("acme", List.of(new LineRequest(item.getId(), BigDecimal.ONE, null, new BigDecimal("1")))).getId();

    AtomicInteger successes = new AtomicInteger();
    AtomicInteger failures = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    for (UUID id : List.of(o1, o2)) {
      pool.submit(
          () -> {
            try {
              start.await();
              sales.confirm(id);
              successes.incrementAndGet();
            } catch (Exception e) {
              failures.incrementAndGet();
            }
          });
    }
    start.countDown();
    pool.shutdown();
    pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

    assertThat(successes.get()).isEqualTo(1);
    assertThat(failures.get()).isEqualTo(1);
    // The single unit is reserved exactly once; nothing oversold.
    BigDecimal reserved = inventory.availabilityForItem(item.getId()).get(0).getReserved();
    BigDecimal available = inventory.availabilityForItem(item.getId()).get(0).getAvailable();
    assertThat(reserved).isEqualByComparingTo("1");
    assertThat(available).isEqualByComparingTo("0");
  }
}
