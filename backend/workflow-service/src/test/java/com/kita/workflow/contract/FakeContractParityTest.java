package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.operations.api.SalesDtos.SalesLineRequest;
import com.kita.operations.api.SalesDtos.SalesOrderCreateRequest;
import com.kita.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.kita.procurement.supplier.CreateSupplierRequest;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.OperationsPort.SalesLine;
import com.kita.workflow.ports.ProcurementPort.PoLine;
import com.kita.workflow.ports.fake.InMemoryOperationsAdapter;
import com.kita.workflow.ports.fake.InMemoryProcurementAdapter;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * US2 fake parity (FR-006): the in-memory test doubles must be held to the same contract as the real
 * services, so an isolated build cannot pass on input the real receiver would reject. The oracle is the
 * receiver's own Bean-Validation constraints — if the real DTO refuses a value, the fake must refuse it
 * too, otherwise green tests would again mean nothing about reality.
 */
class FakeContractParityTest {

  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

  private final InMemoryOperationsAdapter operations = new InMemoryOperationsAdapter();
  private final InMemoryProcurementAdapter procurement = new InMemoryProcurementAdapter();

  /** True when the receiver's real DTO would reject this body. */
  private static boolean receiverRejects(Object receiverDto) {
    return !VALIDATOR.validate(receiverDto).isEmpty();
  }

  @Test
  void receiverRejectsBlankCustomerRefAndSoMustTheFake() {
    SalesOrderCreateRequest realBody =
        new SalesOrderCreateRequest(
            "  ", List.of(new SalesLineRequest(UUID.randomUUID(), BigDecimal.ONE, null, BigDecimal.TEN)));
    assertThat(receiverRejects(realBody)).isTrue(); // @NotBlank customerRef

    operations.seedStock("item-a", new BigDecimal("10"));
    assertThatThrownBy(
            () ->
                operations.createSalesOrder(
                    "  ", List.of(new SalesLine("item-a", BigDecimal.ONE, BigDecimal.TEN))))
        .as("fake must refuse a blank customerRef, exactly as operations-service does")
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void receiverRejectsAnEmptyPurchaseOrderAndSoMustTheFake() {
    CreatePurchaseOrderRequest realBody =
        new CreatePurchaseOrderRequest(null, UUID.randomUUID(), null, List.of());
    assertThat(receiverRejects(realBody)).isTrue(); // @NotEmpty lines

    procurement.seedSupplier("sup-1");
    assertThatThrownBy(() -> procurement.createPurchaseOrder("sup-1", List.of()))
        .as("fake must refuse a PO with no lines, exactly as procurement-service does")
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void receiverRejectsNonPositiveQuantityAndSoMustTheFake() {
    CreatePurchaseOrderRequest realBody =
        new CreatePurchaseOrderRequest(
            null,
            UUID.randomUUID(),
            null,
            List.of(new CreatePurchaseOrderRequest.LineRequest("WIDGET", BigDecimal.ZERO, BigDecimal.ONE)));
    assertThat(receiverRejects(realBody)).isTrue(); // @Positive qtyOrdered

    procurement.seedSupplier("sup-1");
    assertThatThrownBy(
            () ->
                procurement.createPurchaseOrder(
                    "sup-1", List.of(new PoLine("WIDGET", BigDecimal.ZERO, BigDecimal.ONE))))
        .as("fake must refuse a zero-quantity line, exactly as procurement-service does")
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void receiverRejectsBlankSupplierNameAndSoMustTheFake() {
    // The real create requires BOTH a code and a name; the caller derives the code from the name,
    // so a blank name cannot produce a valid receiver body — the fake must reject it as well.
    CreateSupplierRequest realBody =
        new CreateSupplierRequest("", "  ", null, null, null, null, null);
    assertThat(receiverRejects(realBody)).isTrue(); // @NotBlank supplierCode + name

    assertThatThrownBy(
            () ->
                procurement.createSupplier(
                    new com.kita.workflow.ports.ProcurementPort.SupplierInput("  ", true)))
        .as("fake must refuse a blank supplier name, exactly as procurement-service does")
        .isInstanceOf(ValidationException.class);
  }
}
