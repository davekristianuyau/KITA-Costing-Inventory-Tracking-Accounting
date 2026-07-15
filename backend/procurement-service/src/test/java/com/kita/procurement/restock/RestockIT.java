package com.kita.procurement.restock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.procurement.common.ConflictException;
import com.kita.procurement.operations.OperationsPort;
import com.kita.procurement.purchaseorder.PurchaseOrder;
import com.kita.procurement.purchaseorder.PurchaseOrderOrigin;
import com.kita.procurement.purchaseorder.PurchaseOrderService;
import com.kita.procurement.purchaseorder.PurchaseOrderStatus;
import com.kita.procurement.supplier.CreateSupplierRequest;
import com.kita.procurement.supplier.SupplierItem;
import com.kita.procurement.supplier.SupplierItemRepository;
import com.kita.procurement.supplier.SupplierItemRequest;
import com.kita.procurement.supplier.SupplierService;
import com.kita.procurement.support.AbstractProcurementIT;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T033 (SC-005): suggestion sizing, per-supplier consolidation, and the auto-submit gate. */
class RestockIT extends AbstractProcurementIT {

  @Autowired private SupplierService suppliers;
  @Autowired private SupplierItemRepository supplierItems;
  @Autowired private RestockService restock;
  @Autowired private PurchaseOrderService orders;

  private UUID supplier(String code) {
    return suppliers
        .create(new CreateSupplierRequest(code, "Acme", null, null, null, null, null), "buyer")
        .getId();
  }

  /** Give the supplier an item they are the preferred source for. */
  private void supplies(UUID supplierId, String itemRef, String price, String minOrderQty) {
    suppliers.upsertItem(
        supplierId,
        new SupplierItemRequest(
            itemRef, new BigDecimal(price), 7,
            minOrderQty == null ? null : new BigDecimal(minOrderQty), true),
        "buyer");
  }

  private void signal(String itemRef, String onHand, String reorderPoint, String target) {
    operations.seedSignal(
        new OperationsPort.ReorderSignal(
            itemRef, new BigDecimal(onHand), new BigDecimal(reorderPoint), new BigDecimal(target)));
  }

  @Test
  void lowStockItemsBecomeSuggestionsSizedToTarget() {
    UUID s = supplier("RS-1");
    supplies(s, "ITEM-1", "25.00", null);
    signal("ITEM-1", "2", "5", "10");

    List<RestockSuggestion> generated = restock.generate("buyer");

    assertThat(generated).hasSize(1);
    List<RestockSuggestionLine> lines = restock.linesOf(generated.get(0).getId());
    assertThat(lines).hasSize(1);
    assertThat(lines.get(0).getItemRef()).isEqualTo("ITEM-1");
    assertThat(lines.get(0).getSuggestedQty()).isEqualByComparingTo("8"); // 10 − 2
  }

  @Test
  void suggestedQuantityRespectsTheSuppliersMinimumOrder() {
    UUID s = supplier("RS-2");
    supplies(s, "ITEM-1", "25.00", "12");
    signal("ITEM-1", "2", "5", "10");

    RestockSuggestion suggestion = restock.generate("buyer").get(0);

    assertThat(restock.linesOf(suggestion.getId()).get(0).getSuggestedQty())
        .isEqualByComparingTo("12"); // shortfall 8 rounded up to one case of 12
  }

  /** FR-013: one suggestion per supplier, covering every item they are preferred for. */
  @Test
  void itemsAreConsolidatedPerSupplier() {
    UUID a = supplier("RS-3a");
    UUID b = supplier("RS-3b");
    supplies(a, "ITEM-1", "25.00", null);
    supplies(a, "ITEM-2", "10.00", null);
    supplies(b, "ITEM-3", "5.00", null);
    signal("ITEM-1", "1", "5", "10");
    signal("ITEM-2", "0", "5", "20");
    signal("ITEM-3", "2", "5", "8");

    List<RestockSuggestion> generated = restock.generate("buyer");

    assertThat(generated).hasSize(2); // one per supplier, not one per item
    RestockSuggestion forA =
        generated.stream().filter(x -> x.getSupplierId().equals(a)).findFirst().orElseThrow();
    assertThat(restock.linesOf(forA.getId())).hasSize(2);
    RestockSuggestion forB =
        generated.stream().filter(x -> x.getSupplierId().equals(b)).findFirst().orElseThrow();
    assertThat(restock.linesOf(forB.getId())).hasSize(1);
  }

  @Test
  void itemsAboveTheirReorderPointAreNotSuggested() {
    UUID s = supplier("RS-4");
    supplies(s, "ITEM-1", "25.00", null);
    signal("ITEM-1", "9", "5", "10"); // above the reorder point

    assertThat(restock.generate("buyer")).isEmpty();
  }

  /** An item nobody is the preferred source for cannot be ordered, so it is skipped. */
  @Test
  void itemsWithNoPreferredSupplierAreSkipped() {
    supplier("RS-5"); // supplier exists but supplies nothing
    signal("ORPHAN", "0", "5", "10");

    assertThat(restock.generate("buyer")).isEmpty();
  }

  /** FR-014: converting leaves a DRAFT — replenishment must not quietly spend money. */
  @Test
  void convertingProducesADraftPoByDefault() {
    UUID s = supplier("RS-6");
    supplies(s, "ITEM-1", "25.00", null);
    signal("ITEM-1", "2", "5", "10");
    RestockSuggestion suggestion = restock.generate("buyer").get(0);

    PurchaseOrder po = restock.convert(suggestion.getId(), "buyer");

    assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
    assertThat(po.getOrigin()).isEqualTo(PurchaseOrderOrigin.RESTOCK);
    assertThat(po.getOrderTotal()).isEqualByComparingTo("200.00"); // 8 × 25.00
    assertThat(orders.lines(po.getId())).hasSize(1);
  }

  /** Opting an item into auto-submit sends the order without a human touching it. */
  @Test
  void autoSubmitSendsTheOrderWhenEveryItemOptsIn() {
    UUID s = supplier("RS-7");
    supplies(s, "ITEM-1", "25.00", null);
    SupplierItem item = supplierItems.findBySupplierIdAndItemRef(s, "ITEM-1").orElseThrow();
    item.setAutoSubmit(true);
    supplierItems.save(item);
    signal("ITEM-1", "2", "5", "10");
    RestockSuggestion suggestion = restock.generate("buyer").get(0);

    PurchaseOrder po = restock.convert(suggestion.getId(), "buyer");

    assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.SENT);
  }

  /** A single non-opted item keeps the whole order in DRAFT. */
  @Test
  void oneItemWithoutAutoSubmitKeepsTheOrderDraft() {
    UUID s = supplier("RS-8");
    supplies(s, "ITEM-1", "25.00", null);
    supplies(s, "ITEM-2", "10.00", null);
    SupplierItem opted = supplierItems.findBySupplierIdAndItemRef(s, "ITEM-1").orElseThrow();
    opted.setAutoSubmit(true);
    supplierItems.save(opted);
    signal("ITEM-1", "2", "5", "10");
    signal("ITEM-2", "0", "5", "5");
    RestockSuggestion suggestion = restock.generate("buyer").get(0);

    assertThat(restock.convert(suggestion.getId(), "buyer").getStatus())
        .isEqualTo(PurchaseOrderStatus.DRAFT);
  }

  @Test
  void aSuggestionCanOnlyBeConvertedOnce() {
    UUID s = supplier("RS-9");
    supplies(s, "ITEM-1", "25.00", null);
    signal("ITEM-1", "2", "5", "10");
    RestockSuggestion suggestion = restock.generate("buyer").get(0);
    restock.convert(suggestion.getId(), "buyer");

    assertThatThrownBy(() -> restock.convert(suggestion.getId(), "buyer"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("CONVERTED");
  }

  @Test
  void dismissedSuggestionsLeaveTheOpenList() {
    UUID s = supplier("RS-10");
    supplies(s, "ITEM-1", "25.00", null);
    signal("ITEM-1", "2", "5", "10");
    RestockSuggestion suggestion = restock.generate("buyer").get(0);

    restock.dismiss(suggestion.getId(), "buyer");

    assertThat(restock.listOpen()).isEmpty();
    assertThatThrownBy(() -> restock.convert(suggestion.getId(), "buyer"))
        .isInstanceOf(ConflictException.class);
  }
}
