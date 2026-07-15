package com.kita.procurement.receiving;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.procurement.purchaseorder.PurchaseOrderLine;
import com.kita.procurement.purchaseorder.PurchaseOrderOrigin;
import com.kita.procurement.purchaseorder.PurchaseOrderService;
import com.kita.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.kita.procurement.receiving.dto.RecordReceiptRequest;
import com.kita.procurement.supplier.CreateSupplierRequest;
import com.kita.procurement.supplier.SupplierService;
import com.kita.procurement.support.AbstractProcurementIT;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T027: concurrent receipts must not let more stock in than was ordered. */
class ReceivingConcurrencyIT extends AbstractProcurementIT {

  @Autowired private SupplierService suppliers;
  @Autowired private PurchaseOrderService orders;
  @Autowired private ReceivingService receiving;

  /** A SENT order for exactly 10 × ITEM-1. */
  private UUID sentOrder(String code) {
    UUID supplierId =
        suppliers
            .create(new CreateSupplierRequest(code, "Acme", null, null, null, null, null), "buyer")
            .getId();
    UUID id =
        orders
            .create(
                new CreatePurchaseOrderRequest(
                    null,
                    supplierId,
                    PurchaseOrderOrigin.MANUAL,
                    List.of(
                        new CreatePurchaseOrderRequest.LineRequest(
                            "ITEM-1", new BigDecimal("10"), new BigDecimal("25.00")))),
                "buyer")
            .getId();
    orders.approve(id, true, "approver");
    orders.send(id, "buyer");
    return id;
  }

  /**
   * Eight threads each try to receive the full 10. Only one can legitimately succeed — the rest are
   * over-receipts. Without the row lock, several would read qtyReceived=0 and all be allowed.
   */
  @Test
  void concurrentFullReceiptsAllowExactlyOne() throws Exception {
    UUID id = sentOrder("RCC-1");
    int threads = 8;

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    AtomicInteger succeeded = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();

    List<Callable<Void>> tasks =
        Collections.nCopies(
            threads,
            () -> {
              try {
                receiving.record(
                    id,
                    new RecordReceiptRequest(
                        List.of(
                            new RecordReceiptRequest.ReceiptLineRequest(
                                "ITEM-1", new BigDecimal("10")))),
                    "racer");
                succeeded.incrementAndGet();
              } catch (RuntimeException e) {
                rejected.incrementAndGet(); // over-receipt or lock contention — it did not win
              }
              return null;
            });

    for (Future<Void> f : pool.invokeAll(tasks)) {
      f.get(30, TimeUnit.SECONDS);
    }
    pool.shutdown();

    assertThat(succeeded.get()).as("exactly one receipt wins").isEqualTo(1);
    assertThat(rejected.get()).isEqualTo(threads - 1);

    // The ledger must agree: never more received than ordered, and exactly one post.
    PurchaseOrderLine line = orders.lines(id).get(0);
    assertThat(line.getQtyReceived()).isEqualByComparingTo("10");
    assertThat(line.qtyOutstanding()).isEqualByComparingTo("0");
    assertThat(operations.postedReceipts()).hasSize(1);
  }
}
