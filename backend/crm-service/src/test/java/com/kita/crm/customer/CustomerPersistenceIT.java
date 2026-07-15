package com.kita.crm.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.crm.entitlement.Entitlement;
import com.kita.crm.entitlement.EntitlementKind;
import com.kita.crm.entitlement.EntitlementService;
import com.kita.crm.support.AbstractCrmIT;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T012: customer + entitlement persistence, append-only history, entitlement validity windows. */
class CustomerPersistenceIT extends AbstractCrmIT {

  @Autowired private CustomerService customers;
  @Autowired private EntitlementService entitlements;

  private UUID customer(String code) {
    return customers
        .create(
            new CreateCustomerRequest(code, CustomerType.INDIVIDUAL, "Ana", null, null, null), "crm")
        .getId();
  }

  @Test
  void attributeChangesAreRetainedAsAppendOnlyHistory() {
    UUID id = customer("CP-1");

    customers.update(id, new UpdateCustomerRequest("Ana Cruz", null, null, null, null), "crm");
    customers.update(
        id, new UpdateCustomerRequest("Ana Reyes", "ana@x.com", null, null, null), "crm");

    List<CustomerAttributeHistory> history = customers.history(id);
    assertThat(history).extracting(CustomerAttributeHistory::getField)
        .containsExactly("name", "name", "email");
    assertThat(history.get(0).getOldValue()).isEqualTo("Ana");
    assertThat(history.get(0).getNewValue()).isEqualTo("Ana Cruz");
    assertThat(history.get(1).getOldValue()).isEqualTo("Ana Cruz");
    assertThat(history.get(1).getNewValue()).isEqualTo("Ana Reyes");
    // Current value reflects the latest change; prior values survive in history.
    assertThat(customers.get(id).getName()).isEqualTo("Ana Reyes");
  }

  @Test
  void unchangedFieldsRecordNoHistory() {
    UUID id = customer("CP-2");
    customers.update(id, new UpdateCustomerRequest("Ana", null, null, null, null), "crm");
    assertThat(customers.history(id)).isEmpty();
  }

  @Test
  void statusChangeIsRecordedWithItsPredecessor() {
    UUID id = customer("CP-3");
    customers.update(id, new UpdateCustomerRequest(null, null, null, null, CustomerStatus.INACTIVE), "crm");

    List<CustomerAttributeHistory> history = customers.history(id);
    assertThat(history).hasSize(1);
    assertThat(history.get(0).getField()).isEqualTo("status");
    assertThat(history.get(0).getOldValue()).isEqualTo("ACTIVE");
    assertThat(history.get(0).getNewValue()).isEqualTo("INACTIVE");
  }

  @Test
  void entitlementValidityHonoursItsWindow() {
    UUID id = customer("CP-4");
    entitlements.add(
        id,
        new Entitlement(
            id, EntitlementKind.SENIOR, "SC-1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
        "crm");

    assertThat(entitlements.validOn(id, LocalDate.of(2026, 3, 1))).hasSize(1);
    assertThat(entitlements.validOn(id, LocalDate.of(2025, 12, 31))).isEmpty(); // before validFrom
    assertThat(entitlements.validOn(id, LocalDate.of(2026, 7, 1))).isEmpty(); // after validTo
    // Boundaries are inclusive.
    assertThat(entitlements.validOn(id, LocalDate.of(2026, 1, 1))).hasSize(1);
    assertThat(entitlements.validOn(id, LocalDate.of(2026, 6, 30))).hasSize(1);
  }

  @Test
  void openEndedEntitlementStaysValid() {
    UUID id = customer("CP-5");
    entitlements.add(
        id, new Entitlement(id, EntitlementKind.PWD, "PWD-9", LocalDate.of(2026, 1, 1), null), "crm");
    assertThat(entitlements.validOn(id, LocalDate.of(2099, 1, 1))).hasSize(1);
  }

  @Test
  void entitlementWithoutSupportingIdIsFlaggedAsSuch() {
    UUID id = customer("CP-6");
    Entitlement saved =
        entitlements.add(
            id, new Entitlement(id, EntitlementKind.SENIOR, null, LocalDate.of(2026, 1, 1), null), "crm");
    assertThat(saved.hasSupportingId()).isFalse();
  }
}
