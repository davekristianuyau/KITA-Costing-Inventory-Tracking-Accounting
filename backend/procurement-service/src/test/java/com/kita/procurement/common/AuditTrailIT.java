package com.kita.procurement.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.procurement.purchaseorder.PurchaseOrderOrigin;
import com.kita.procurement.purchaseorder.PurchaseOrderService;
import com.kita.procurement.purchaseorder.PurchaseOrderStatus;
import com.kita.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.kita.procurement.receiving.ReceivingService;
import com.kita.procurement.receiving.dto.RecordReceiptRequest;
import com.kita.procurement.supplier.CreateSupplierRequest;
import com.kita.procurement.supplier.SupplierService;
import com.kita.procurement.supplier.UpdateSupplierRequest;
import com.kita.procurement.support.AbstractProcurementIT;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * FR-016 / SC-006: PO approvals, sends, receipts and supplier changes are attributable to a user AND
 * a timestamp.
 *
 * <p>The service wrote these from the start, but nothing verified them — and an audit trail is only
 * ever consulted at the moment it has to be right.
 */
class AuditTrailIT extends AbstractProcurementIT {

  @Autowired private SupplierService suppliers;
  @Autowired private PurchaseOrderService orders;
  @Autowired private ReceivingService receiving;
  @Autowired private AuditEventRepository audit;

  private UUID supplier(String code) {
    return suppliers
        .create(new CreateSupplierRequest(code, "Acme", null, null, null, null, null), "buyer-alice")
        .getId();
  }

  private UUID order(UUID supplierId) {
    return orders
        .create(
            new CreatePurchaseOrderRequest(
                null,
                supplierId,
                PurchaseOrderOrigin.MANUAL,
                List.of(
                    new CreatePurchaseOrderRequest.LineRequest(
                        "ITEM-1", new BigDecimal("10"), new BigDecimal("25.00")))),
            "buyer-alice")
        .getId();
  }

  private List<AuditEvent> eventsOf(String action) {
    return audit.findAll().stream().filter(e -> e.getAction().equals(action)).toList();
  }

  /** SC-006: the whole PO lifecycle names who did each step, and when. */
  @Test
  void poApprovalAndSendAreAttributableToAUserAndTimestamp() {
    UUID id = order(supplier("AU-1"));
    Instant before = Instant.now().minusSeconds(1);

    orders.approve(id, true, "approver-bob");
    orders.send(id, "buyer-carol");

    assertThat(eventsOf("PO_APPROVED")).hasSize(1);
    assertThat(eventsOf("PO_APPROVED").get(0).getActor()).isEqualTo("approver-bob");
    assertThat(eventsOf("PO_APPROVED").get(0).getEntityRef()).isEqualTo(id.toString());
    assertThat(eventsOf("PO_APPROVED").get(0).getAt()).isAfter(before);

    assertThat(eventsOf("PO_SENT")).hasSize(1);
    assertThat(eventsOf("PO_SENT").get(0).getActor()).isEqualTo("buyer-carol");
  }

  /** SC-006: a receipt names the receiver. */
  @Test
  void goodsReceiptIsAttributableToAUserAndTimestamp() {
    UUID id = order(supplier("AU-2"));
    orders.approve(id, true, "approver-bob");
    orders.send(id, "buyer-alice");
    Instant before = Instant.now().minusSeconds(1);

    receiving.record(
        id,
        new RecordReceiptRequest(
            List.of(new RecordReceiptRequest.ReceiptLineRequest("ITEM-1", new BigDecimal("10")))),
        "receiver-dave");

    assertThat(eventsOf("GOODS_RECEIVED")).hasSize(1);
    assertThat(eventsOf("GOODS_RECEIVED").get(0).getActor()).isEqualTo("receiver-dave");
    assertThat(eventsOf("GOODS_RECEIVED").get(0).getAt()).isAfter(before);
  }

  /** SC-006: supplier changes, including a price change, name who made them. */
  @Test
  void supplierChangesAreAttributable() {
    UUID id = supplier("AU-3");
    suppliers.update(
        id,
        new UpdateSupplierRequest("Acme Corp", null, null, null, null, null, null),
        "buyer-erin");

    assertThat(eventsOf("SUPPLIER_CHANGED")).isNotEmpty();
    assertThat(eventsOf("SUPPLIER_CHANGED"))
        .anySatisfy(e -> assertThat(e.getActor()).isEqualTo("buyer-erin"));
  }

  /**
   * FR-008: a cancelled PO has no inventory or cost effect.
   *
   * <p>Structurally guaranteed — cancel is only legal before any receipt, and only a receipt posts —
   * but the invariant itself was never asserted, so a future change to either rule could break it
   * silently.
   */
  @Test
  void aCancelledOrderIsAuditedAndHasNoInventoryEffect() {
    UUID id = order(supplier("AU-4"));
    orders.approve(id, true, "approver-bob");
    orders.send(id, "buyer-alice");

    orders.cancel(id, "buyer-frank");

    assertThat(orders.get(id).getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
    assertThat(eventsOf("PO_CANCELLED")).hasSize(1);
    assertThat(eventsOf("PO_CANCELLED").get(0).getActor()).isEqualTo("buyer-frank");

    // The actual requirement: nothing was ever posted to operations-service.
    assertThat(operations.postedReceipts()).as("a cancelled PO must not touch inventory").isEmpty();
    // And no line shows stock against it.
    assertThat(orders.lines(id))
        .allSatisfy(l -> assertThat(l.getQtyReceived()).isEqualByComparingTo("0"));
  }

  /** Every event carries who and when — an unattributed row would defeat the trail's purpose. */
  @Test
  void everyRecordedEventCarriesAnActorAndTimestamp() {
    UUID id = order(supplier("AU-5"));
    orders.approve(id, true, "approver-bob");

    assertThat(audit.findAll()).isNotEmpty();
    assertThat(audit.findAll())
        .allSatisfy(
            e -> {
              assertThat(e.getActor()).as("actor for %s", e.getAction()).isNotBlank();
              assertThat(e.getAt()).as("timestamp for %s", e.getAction()).isNotNull();
              assertThat(e.getEntityRef()).as("entity for %s", e.getAction()).isNotBlank();
            });
  }
}
