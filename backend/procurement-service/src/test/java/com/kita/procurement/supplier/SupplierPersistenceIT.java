package com.kita.procurement.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.procurement.support.AbstractProcurementIT;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T012: supplier + supplied-item persistence and price-history retention (FR-003). */
class SupplierPersistenceIT extends AbstractProcurementIT {

  @Autowired private SupplierService suppliers;
  @Autowired private SupplierItemRepository itemRepo;

  private UUID supplier(String code) {
    return suppliers
        .create(
            new CreateSupplierRequest(code, "Acme", null, null, null, "NET30", "FOB"), "buyer")
        .getId();
  }

  private SupplierItemRequest item(String ref, String price, boolean preferred) {
    return new SupplierItemRequest(ref, new BigDecimal(price), 7, new BigDecimal("10"), preferred);
  }

  @Test
  void supplierWithTwoSuppliedItemsPersists() {
    UUID id = supplier("SP-1");
    suppliers.upsertItem(id, item("ITEM-1", "25.50", true), "buyer");
    suppliers.upsertItem(id, item("ITEM-2", "10.00", false), "buyer");

    assertThat(suppliers.items(id)).hasSize(2);
    assertThat(suppliers.items(id))
        .extracting(SupplierItem::getItemRef)
        .containsExactlyInAnyOrder("ITEM-1", "ITEM-2");
  }

  /** A price change must stay reconstructable after the catalog moves on. */
  @Test
  void priceChangesAreRetainedAsHistory() {
    UUID id = supplier("SP-2");
    suppliers.upsertItem(id, item("ITEM-1", "25.50", false), "buyer");
    suppliers.upsertItem(id, item("ITEM-1", "27.00", false), "buyer");
    suppliers.upsertItem(id, item("ITEM-1", "30.00", false), "buyer");

    List<SupplierChangeHistory> priceHistory =
        suppliers.history(id).stream().filter(h -> h.getField().equals("supplierPrice")).toList();

    assertThat(priceHistory).hasSize(2);
    assertThat(priceHistory.get(0).getOldValue()).isEqualTo("25.50");
    assertThat(priceHistory.get(0).getNewValue()).isEqualTo("27.00");
    assertThat(priceHistory.get(1).getOldValue()).isEqualTo("27.00");
    assertThat(priceHistory.get(1).getNewValue()).isEqualTo("30.00");
    assertThat(priceHistory).allSatisfy(h -> assertThat(h.getItemRef()).isEqualTo("ITEM-1"));

    // Upsert updates in place rather than duplicating the row.
    assertThat(suppliers.items(id)).hasSize(1);
    assertThat(suppliers.items(id).get(0).getSupplierPrice()).isEqualByComparingTo("30.00");
  }

  @Test
  void reAssertingTheSamePriceRecordsNoHistory() {
    UUID id = supplier("SP-3");
    suppliers.upsertItem(id, item("ITEM-1", "25.50", false), "buyer");
    suppliers.upsertItem(id, item("ITEM-1", "25.50", false), "buyer");

    assertThat(suppliers.history(id).stream().filter(h -> h.getField().equals("supplierPrice")))
        .isEmpty();
  }

  @Test
  void supplierAttributeChangesAreRetained() {
    UUID id = supplier("SP-4");
    suppliers.update(
        id, new UpdateSupplierRequest("Acme Corp", null, null, null, null, null, null), "buyer");
    suppliers.update(
        id,
        new UpdateSupplierRequest(null, null, null, null, "NET60", null, SupplierStatus.INACTIVE),
        "buyer");

    assertThat(suppliers.history(id))
        .extracting(SupplierChangeHistory::getField)
        .containsExactly("name", "paymentTerms", "status");
    assertThat(suppliers.get(id).getStatus()).isEqualTo(SupplierStatus.INACTIVE);
  }

  /** Only one supplier may be preferred for an item, so restock has an unambiguous default. */
  @Test
  void markingAnotherSupplierPreferredClearsThePreviousOne() {
    UUID first = supplier("SP-5");
    UUID second = supplier("SP-6");
    suppliers.upsertItem(first, item("ITEM-X", "25.00", true), "buyer");
    suppliers.upsertItem(second, item("ITEM-X", "24.00", true), "buyer");

    assertThat(itemRepo.findByItemRefAndPreferredTrue("ITEM-X")).isPresent();
    assertThat(itemRepo.findByItemRefAndPreferredTrue("ITEM-X").orElseThrow().getSupplierId())
        .isEqualTo(second);
    assertThat(itemRepo.findBySupplierIdAndItemRef(first, "ITEM-X").orElseThrow().isPreferred())
        .isFalse();
  }
}
