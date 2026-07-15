package com.kita.procurement.purchaseorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.procurement.common.ForbiddenException;
import com.kita.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.kita.procurement.supplier.CreateSupplierRequest;
import com.kita.procurement.supplier.SupplierService;
import com.kita.procurement.support.AbstractProcurementIT;
import java.math.BigDecimal;
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

/** T019 (SC-002): exactly one approval wins under concurrency; the threshold gates who may approve. */
class PurchaseOrderConcurrencyIT extends AbstractProcurementIT {

  @Autowired private SupplierService suppliers;
  @Autowired private PurchaseOrderService orders;

  private UUID supplier(String code) {
    return suppliers
        .create(new CreateSupplierRequest(code, "Acme", null, null, null, null, null), "buyer")
        .getId();
  }

  private UUID order(UUID supplierId, String qty, String price) {
    return orders
        .create(
            new CreatePurchaseOrderRequest(
                null,
                supplierId,
                PurchaseOrderOrigin.MANUAL,
                List.of(
                    new CreatePurchaseOrderRequest.LineRequest(
                        "ITEM-1", new BigDecimal(qty), new BigDecimal(price)))),
            "buyer")
        .getId();
  }

  /** Two callers approve the same order at once: one succeeds, the other is rejected. */
  @Test
  void concurrentApproveAllowsExactlyOneSuccess() throws Exception {
    UUID id = order(supplier("CC-1"), "1", "100.00");

    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    AtomicInteger succeeded = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();

    List<Callable<Void>> tasks =
        java.util.Collections.nCopies(
            threads,
            () -> {
              try {
                orders.approve(id, true, "racer");
                succeeded.incrementAndGet();
              } catch (RuntimeException e) {
                rejected.incrementAndGet(); // conflict or lock timeout — either way, it did not win
              }
              return null;
            });

    for (Future<Void> f : pool.invokeAll(tasks)) {
      f.get(30, TimeUnit.SECONDS);
    }
    pool.shutdown();

    assertThat(succeeded.get()).as("exactly one approval wins").isEqualTo(1);
    assertThat(rejected.get()).isEqualTo(threads - 1);
    assertThat(orders.get(id).getStatus()).isEqualTo(PurchaseOrderStatus.APPROVED);
  }

  /** FR-006: at or below the threshold, an ordinary buyer may approve. */
  @Test
  void orderAtOrBelowThresholdIsApprovedWithoutAnAuthorizedApprover() {
    UUID id = order(supplier("CC-2"), "1", "50000.00"); // exactly the configured threshold

    PurchaseOrder approved = orders.approve(id, false, "buyer");

    assertThat(approved.getStatus()).isEqualTo(PurchaseOrderStatus.APPROVED);
    assertThat(approved.getApprovedBy()).isEqualTo("buyer");
  }

  /** FR-006: above the threshold, an ordinary buyer cannot self-approve. */
  @Test
  void orderAboveThresholdRequiresAnAuthorizedApprover() {
    UUID id = order(supplier("CC-3"), "1", "50000.01");

    assertThatThrownBy(() -> orders.approve(id, false, "buyer"))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("threshold");

    // Still DRAFT: a refused approval must not half-apply.
    assertThat(orders.get(id).getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);

    PurchaseOrder approved = orders.approve(id, true, "approver");
    assertThat(approved.getStatus()).isEqualTo(PurchaseOrderStatus.APPROVED);
  }
}
