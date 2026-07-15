package com.kita.procurement.receiving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.procurement.common.ConflictException;
import com.kita.procurement.operations.OperationsPort;
import com.kita.procurement.purchaseorder.PurchaseOrderOrigin;
import com.kita.procurement.purchaseorder.PurchaseOrderService;
import com.kita.procurement.purchaseorder.PurchaseOrderStatus;
import com.kita.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.kita.procurement.receiving.dto.RecordReceiptRequest;
import com.kita.procurement.supplier.CreateSupplierRequest;
import com.kita.procurement.supplier.SupplierService;
import com.kita.procurement.support.AbstractProcurementIT;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T026 (SC-003/004): partial→full→closed, the over-receipt guard, and exactly-once posting. */
class ReceivingIT extends AbstractProcurementIT {

  @Autowired private SupplierService suppliers;
  @Autowired private PurchaseOrderService orders;
  @Autowired private ReceivingService receiving;

  /** A SENT order for 10 × ITEM-1 @ 25.00 and 4 × ITEM-2 @ 50.00. */
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
                            "ITEM-1", new BigDecimal("10"), new BigDecimal("25.00")),
                        new CreatePurchaseOrderRequest.LineRequest(
                            "ITEM-2", new BigDecimal("4"), new BigDecimal("50.00")))),
                "buyer")
            .getId();
    orders.approve(id, true, "approver");
    orders.send(id, "buyer");
    return id;
  }

  private RecordReceiptRequest receipt(String itemRef, String qty) {
    return new RecordReceiptRequest(
        List.of(new RecordReceiptRequest.ReceiptLineRequest(itemRef, new BigDecimal(qty))));
  }

  @Test
  void partialThenFullReceiptMovesTheOrderThroughToClosed() {
    UUID id = sentOrder("RC-1");

    receiving.record(id, receipt("ITEM-1", "10"), "receiver");
    assertThat(orders.get(id).getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);

    receiving.record(id, receipt("ITEM-2", "4"), "receiver");
    assertThat(orders.get(id).getStatus()).isEqualTo(PurchaseOrderStatus.FULLY_RECEIVED);

    receiving.close(id, "buyer");
    assertThat(orders.get(id).getStatus()).isEqualTo(PurchaseOrderStatus.CLOSED);
  }

  @Test
  void partialQuantityOnALineLeavesTheOrderPartiallyReceived() {
    UUID id = sentOrder("RC-2");

    receiving.record(id, receipt("ITEM-1", "4"), "receiver");

    assertThat(orders.get(id).getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
    assertThat(orders.lines(id).stream().filter(l -> l.getItemRef().equals("ITEM-1")).findFirst().orElseThrow()
            .qtyOutstanding())
        .isEqualByComparingTo("6");
  }

  /** FR-010: no silent over-receipt. */
  @Test
  void overReceiptIsRejected() {
    UUID id = sentOrder("RC-3");

    assertThatThrownBy(() -> receiving.record(id, receipt("ITEM-1", "11"), "receiver"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("over-receipt");

    // Nothing applied: the line is untouched and no receipt was posted.
    assertThat(orders.lines(id).stream().filter(l -> l.getItemRef().equals("ITEM-1")).findFirst().orElseThrow()
            .getQtyReceived())
        .isEqualByComparingTo("0");
    assertThat(operations.postedReceipts()).isEmpty();
  }

  @Test
  void cumulativeOverReceiptAcrossTwoDeliveriesIsRejected() {
    UUID id = sentOrder("RC-4");
    receiving.record(id, receipt("ITEM-1", "8"), "receiver");

    assertThatThrownBy(() -> receiving.record(id, receipt("ITEM-1", "3"), "receiver"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("over-receipt");
  }

  /** A bad line must not half-apply the rest of the delivery. */
  @Test
  void aDeliveryWithOneBadLineAppliesNothing() {
    UUID id = sentOrder("RC-5");

    RecordReceiptRequest mixed =
        new RecordReceiptRequest(
            List.of(
                new RecordReceiptRequest.ReceiptLineRequest("ITEM-1", new BigDecimal("5")),
                new RecordReceiptRequest.ReceiptLineRequest("ITEM-2", new BigDecimal("99"))));

    assertThatThrownBy(() -> receiving.record(id, mixed, "receiver"))
        .isInstanceOf(ConflictException.class);

    assertThat(orders.lines(id)).allSatisfy(l -> assertThat(l.getQtyReceived()).isEqualByComparingTo("0"));
    assertThat(orders.get(id).getStatus()).isEqualTo(PurchaseOrderStatus.SENT);
  }

  @Test
  void receivingAnItemNotOnTheOrderIsRejected() {
    UUID id = sentOrder("RC-6");
    assertThatThrownBy(() -> receiving.record(id, receipt("GHOST", "1"), "receiver"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("not on this order");
  }

  /** SC-003/004: exactly one goods-receipt event per receipt, valued at the PO's agreed price. */
  @Test
  void eachReceiptPostsExactlyOnceAtTheAgreedPrice() {
    UUID id = sentOrder("RC-7");

    receiving.record(id, receipt("ITEM-1", "10"), "receiver");
    receiving.record(id, receipt("ITEM-2", "4"), "receiver");

    List<OperationsPort.GoodsReceiptPost> posted = operations.postedReceipts();
    assertThat(posted).hasSize(2);
    assertThat(posted.stream().map(OperationsPort.GoodsReceiptPost::idempotencyKey).distinct())
        .as("each receipt carries its own idempotency key")
        .hasSize(2);

    assertThat(posted.get(0).lines()).hasSize(1);
    assertThat(posted.get(0).lines().get(0).itemRef()).isEqualTo("ITEM-1");
    assertThat(posted.get(0).lines().get(0).quantity()).isEqualByComparingTo("10");
    assertThat(posted.get(0).lines().get(0).unitCost()).isEqualByComparingTo("25.00");
  }

  /** A retry of an already-posted key must not double-count stock. */
  @Test
  void repostingTheSameKeyIsIgnoredByTheOperationsPort() {
    UUID id = sentOrder("RC-8");
    receiving.record(id, receipt("ITEM-1", "10"), "receiver");

    OperationsPort.GoodsReceiptPost original = operations.postedReceipts().get(0);
    boolean acceptedAgain = operations.postGoodsReceipt(original);

    assertThat(acceptedAgain).as("a replayed key is refused").isFalse();
    assertThat(operations.postedReceipts()).hasSize(1);
  }

  @Test
  void receiptsAreRecordedAndReadableForTheOrder() {
    UUID id = sentOrder("RC-9");
    receiving.record(id, receipt("ITEM-1", "10"), "receiver");

    assertThat(receiving.forOrder(id)).hasSize(1);
    assertThat(receiving.forOrder(id).get(0).isPostedToOperations()).isTrue();
    assertThat(receiving.linesOf(receiving.forOrder(id).get(0).getId())).hasSize(1);
  }

  @Test
  void closingAnOrderThatIsNotFullyReceivedIsRejected() {
    UUID id = sentOrder("RC-10");
    receiving.record(id, receipt("ITEM-1", "4"), "receiver");

    assertThatThrownBy(() -> receiving.close(id, "buyer"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("FULLY_RECEIVED");
  }
}
