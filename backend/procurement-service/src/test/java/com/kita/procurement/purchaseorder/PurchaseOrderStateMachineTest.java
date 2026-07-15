package com.kita.procurement.purchaseorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.procurement.common.ConflictException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * T018: every legal transition succeeds and every illegal one is rejected.
 *
 * <p>Each case is driven off the full enum rather than a hand-listed set, so a status added later
 * cannot quietly slip through untested.
 */
class PurchaseOrderStateMachineTest {

  private static Set<PurchaseOrderStatus> allExcept(PurchaseOrderStatus... allowed) {
    EnumSet<PurchaseOrderStatus> set = EnumSet.allOf(PurchaseOrderStatus.class);
    Arrays.asList(allowed).forEach(set::remove);
    return set;
  }

  @Test
  void approveIsAllowedOnlyFromDraft() {
    assertThatCode(() -> PurchaseOrderStateMachine.assertCanApprove(PurchaseOrderStatus.DRAFT))
        .doesNotThrowAnyException();

    for (PurchaseOrderStatus s : allExcept(PurchaseOrderStatus.DRAFT)) {
      assertThatThrownBy(() -> PurchaseOrderStateMachine.assertCanApprove(s))
          .as("approve from %s", s)
          .isInstanceOf(ConflictException.class);
    }
  }

  @Test
  void sendIsAllowedOnlyFromApproved() {
    assertThatCode(() -> PurchaseOrderStateMachine.assertCanSend(PurchaseOrderStatus.APPROVED))
        .doesNotThrowAnyException();

    for (PurchaseOrderStatus s : allExcept(PurchaseOrderStatus.APPROVED)) {
      assertThatThrownBy(() -> PurchaseOrderStateMachine.assertCanSend(s))
          .as("send from %s", s)
          .isInstanceOf(ConflictException.class);
    }
  }

  @Test
  void receiveIsAllowedOnlyFromSentOrPartiallyReceived() {
    for (PurchaseOrderStatus s :
        EnumSet.of(PurchaseOrderStatus.SENT, PurchaseOrderStatus.PARTIALLY_RECEIVED)) {
      assertThatCode(() -> PurchaseOrderStateMachine.assertCanReceive(s))
          .as("receive from %s", s)
          .doesNotThrowAnyException();
    }
    for (PurchaseOrderStatus s :
        allExcept(PurchaseOrderStatus.SENT, PurchaseOrderStatus.PARTIALLY_RECEIVED)) {
      assertThatThrownBy(() -> PurchaseOrderStateMachine.assertCanReceive(s))
          .as("receive from %s", s)
          .isInstanceOf(ConflictException.class);
    }
  }

  /** A DRAFT order cannot be received against — the headline illegal transition. */
  @Test
  void receivingADraftIsRejected() {
    assertThatThrownBy(() -> PurchaseOrderStateMachine.assertCanReceive(PurchaseOrderStatus.DRAFT))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("DRAFT");
  }

  @Test
  void cancelIsAllowedOnlyBeforeAnyReceipt() {
    for (PurchaseOrderStatus s :
        EnumSet.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.SENT)) {
      assertThatCode(() -> PurchaseOrderStateMachine.assertCanCancel(s))
          .as("cancel from %s", s)
          .doesNotThrowAnyException();
    }
    for (PurchaseOrderStatus s :
        allExcept(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.SENT)) {
      assertThatThrownBy(() -> PurchaseOrderStateMachine.assertCanCancel(s))
          .as("cancel from %s", s)
          .isInstanceOf(ConflictException.class);
    }
  }

  /** An order with stock already in the building must be closed, not erased. */
  @Test
  void cancellingAnOrderWithReceiptsIsRejected() {
    assertThatThrownBy(
            () -> PurchaseOrderStateMachine.assertCanCancel(PurchaseOrderStatus.PARTIALLY_RECEIVED))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("receipts");
  }

  /** FR-007: lines are immutable once the order has gone to the supplier. */
  @Test
  void linesAreEditableOnlyWhileDraft() {
    assertThatCode(() -> PurchaseOrderStateMachine.assertLinesEditable(PurchaseOrderStatus.DRAFT))
        .doesNotThrowAnyException();

    for (PurchaseOrderStatus s : allExcept(PurchaseOrderStatus.DRAFT)) {
      assertThatThrownBy(() -> PurchaseOrderStateMachine.assertLinesEditable(s))
          .as("edit lines in %s", s)
          .isInstanceOf(ConflictException.class);
    }
  }

  @Test
  void terminalStatesAllowNothing() {
    for (PurchaseOrderStatus terminal :
        EnumSet.of(PurchaseOrderStatus.CLOSED, PurchaseOrderStatus.CANCELLED)) {
      assertThatThrownBy(() -> PurchaseOrderStateMachine.assertCanApprove(terminal))
          .isInstanceOf(ConflictException.class);
      assertThatThrownBy(() -> PurchaseOrderStateMachine.assertCanSend(terminal))
          .isInstanceOf(ConflictException.class);
      assertThatThrownBy(() -> PurchaseOrderStateMachine.assertCanReceive(terminal))
          .isInstanceOf(ConflictException.class);
      assertThatThrownBy(() -> PurchaseOrderStateMachine.assertCanCancel(terminal))
          .isInstanceOf(ConflictException.class);
    }
  }

  @Test
  void receiptOutcomeDependsOnWhetherEveryLineIsSatisfied() {
    assertThat(PurchaseOrderStateMachine.afterReceipt(true)).isEqualTo(PurchaseOrderStatus.FULLY_RECEIVED);
    assertThat(PurchaseOrderStateMachine.afterReceipt(false))
        .isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
  }
}
