package com.kita.operations.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.operations.bom.BomService;
import com.kita.operations.bom.BomService.ComponentSpec;
import com.kita.operations.bom.BomType;
import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.ItemType;
import com.kita.operations.catalog.UomFamily;
import com.kita.operations.inventory.InventoryService;
import com.kita.operations.inventory.StockLocation;
import com.kita.operations.procurement.GoodsReceiptService;
import com.kita.operations.procurement.GoodsReceiptService.ReceiptLineSpec;
import com.kita.operations.production.BuildService;
import com.kita.operations.sales.SalesOrderService;
import com.kita.operations.sales.SalesOrderService.LineRequest;
import com.kita.operations.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * FR-021: an auditable history of significant business events — orders confirmed/fulfilled/cancelled,
 * receipts, adjustments, BOM changes and builds.
 *
 * <p>The movement ledger already covers stock changes (FR-003), but FR-021 is broader and only stock
 * had any coverage: T022 wired a logger into StockLedgerService and nothing else. Log lines are not a
 * durable, queryable history, and orders/BOM/builds had none at all.
 *
 * <p>003 specifies no caller identity and this service has no security model, so events record what
 * happened and when — the actor is deliberately not fabricated.
 */
class AuditTrailIntegrationTest extends AbstractIntegrationTest {

  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired BomService bom;
  @Autowired BuildService builds;
  @Autowired SalesOrderService sales;
  @Autowired GoodsReceiptService receipts;
  @Autowired AuditEventRepository audit;

  private StockLocation loc;

  private List<AuditEvent> eventsOf(String action) {
    return audit.findAll().stream().filter(e -> e.getAction().equals(action)).toList();
  }

  private Item component(String sku) {
    return catalog.createItem(sku, sku, ItemType.COMPONENT, "ea", null, false);
  }

  private void setUpCatalog() {
    catalog.createUom("ea", UomFamily.COUNT);
    loc = inventory.createLocation("WH", "WH");
  }

  private void seed(Item item, String qty) {
    inventory.postAdjustment(item.getId(), loc.getId(), null, new BigDecimal(qty), "ea", "seed");
  }

  /** FR-021: a stock adjustment is recorded as a business event, not only as a log line. */
  @Test
  void adjustmentIsAudited() {
    setUpCatalog();
    Item bolt = component("BOLT");
    Instant before = Instant.now().minusSeconds(1);

    inventory.postAdjustment(bolt.getId(), loc.getId(), null, new BigDecimal("10"), "ea", "count");

    assertThat(eventsOf("STOCK_ADJUSTED")).hasSize(1);
    AuditEvent e = eventsOf("STOCK_ADJUSTED").get(0);
    assertThat(e.getEntityRef()).isNotBlank();
    assertThat(e.getAt()).isAfter(before);
    assertThat(e.getDetail()).contains("BOLT").contains("count");
  }

  /** FR-021: the full order lifecycle — confirmed, fulfilled — is recorded. */
  @Test
  void orderConfirmAndFulfilAreAudited() {
    setUpCatalog();
    Item bolt = component("BOLT");
    seed(bolt, "10");

    UUID order =
        sales
            .create("CUST-1", List.of(new LineRequest(bolt.getId(), BigDecimal.ONE, null, BigDecimal.ONE)))
            .getId();
    sales.confirm(order);
    sales.fulfill(order);

    assertThat(eventsOf("ORDER_CONFIRMED")).hasSize(1);
    assertThat(eventsOf("ORDER_CONFIRMED").get(0).getEntityRef()).isEqualTo(order.toString());
    assertThat(eventsOf("ORDER_FULFILLED")).hasSize(1);
    assertThat(eventsOf("ORDER_FULFILLED").get(0).getEntityRef()).isEqualTo(order.toString());
  }

  /** FR-021 names cancellation explicitly — a cancelled order must leave a trace. */
  @Test
  void orderCancellationIsAudited() {
    setUpCatalog();
    Item bolt = component("BOLT");
    seed(bolt, "10");

    UUID order =
        sales
            .create("CUST-1", List.of(new LineRequest(bolt.getId(), BigDecimal.ONE, null, BigDecimal.ONE)))
            .getId();
    sales.confirm(order);
    sales.cancel(order);

    assertThat(eventsOf("ORDER_CANCELLED")).hasSize(1);
    assertThat(eventsOf("ORDER_CANCELLED").get(0).getEntityRef()).isEqualTo(order.toString());
    assertThat(eventsOf("ORDER_FULFILLED")).isEmpty();
  }

  @Test
  void goodsReceiptIsAudited() {
    setUpCatalog();
    Item bolt = component("BOLT");

    receipts.post(
        "SUP-1",
        loc.getId(),
        List.of(
            new ReceiptLineSpec(
                bolt.getId(), null, null, new BigDecimal("5"), "ea", new BigDecimal("2.00"))));

    assertThat(eventsOf("GOODS_RECEIPT_POSTED")).hasSize(1);
    assertThat(eventsOf("GOODS_RECEIPT_POSTED").get(0).getDetail()).contains("lines=1");
  }

  @Test
  void bomChangeIsAudited() {
    setUpCatalog();
    Item enclosure = component("ENCLOSURE");
    Item set = catalog.createItem("SET", "Set", ItemType.KIT, "ea", null, false);

    bom.create(
        set.getId(), BomType.KIT, BigDecimal.ONE,
        List.of(new ComponentSpec(enclosure.getId(), BigDecimal.ONE, "ea")));

    assertThat(eventsOf("BOM_CHANGED")).hasSize(1);
    assertThat(eventsOf("BOM_CHANGED").get(0).getDetail()).contains("SET").contains("KIT");
  }

  @Test
  void buildIsAudited() {
    setUpCatalog();
    Item part = component("PART");
    seed(part, "20");
    Item widget = catalog.createItem("WIDGET", "Widget", ItemType.FINISHED_GOOD, "ea", null, false);
    bom.create(
        widget.getId(), BomType.MANUFACTURED, BigDecimal.ONE,
        List.of(new ComponentSpec(part.getId(), new BigDecimal("2"), "ea")));

    builds.build(widget.getId(), loc.getId(), new BigDecimal("3"));

    assertThat(eventsOf("BUILD_COMPLETED")).hasSize(1);
    assertThat(eventsOf("BUILD_COMPLETED").get(0).getDetail()).contains("WIDGET").contains("qty=3");
  }

  /** Every recorded event says what happened, to what, and when. */
  @Test
  void everyEventCarriesAnActionEntityAndTimestamp() {
    setUpCatalog();
    Item bolt = component("BOLT");
    seed(bolt, "5");

    assertThat(audit.findAll()).isNotEmpty();
    assertThat(audit.findAll())
        .allSatisfy(
            e -> {
              assertThat(e.getAction()).isNotBlank();
              assertThat(e.getEntityRef()).as("entity for %s", e.getAction()).isNotBlank();
              assertThat(e.getAt()).as("timestamp for %s", e.getAction()).isNotNull();
            });
  }
}
