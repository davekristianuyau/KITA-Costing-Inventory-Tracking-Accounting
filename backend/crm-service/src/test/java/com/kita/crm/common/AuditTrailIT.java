package com.kita.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.crm.customer.CreateCustomerRequest;
import com.kita.crm.customer.CustomerService;
import com.kita.crm.customer.CustomerStatus;
import com.kita.crm.customer.CustomerType;
import com.kita.crm.customer.UpdateCustomerRequest;
import com.kita.crm.discount.DiscountComputationKind;
import com.kita.crm.discount.DiscountOrigin;
import com.kita.crm.discount.DiscountRule;
import com.kita.crm.discount.DiscountRuleService;
import com.kita.crm.discount.StackingMode;
import com.kita.crm.discount.VatTreatment;
import com.kita.crm.entitlement.Entitlement;
import com.kita.crm.entitlement.EntitlementKind;
import com.kita.crm.entitlement.EntitlementService;
import com.kita.crm.support.AbstractCrmIT;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T039 (FR-016/SC-006): rule, entitlement, and policy changes are attributable to a user + time. */
class AuditTrailIT extends AbstractCrmIT {

  @Autowired private CustomerService customers;
  @Autowired private EntitlementService entitlements;
  @Autowired private DiscountRuleService rules;
  @Autowired private AuditEventRepository audit;

  private UUID customer(String code) {
    return customers
        .create(new CreateCustomerRequest(code, CustomerType.INDIVIDUAL, "Ana", null, null, null), "alice")
        .getId();
  }

  private List<AuditEvent> events() {
    return audit.findAll();
  }

  @Test
  void customerAndEntitlementChangesAreAudited() {
    UUID id = customer("AU-1");
    customers.update(id, new UpdateCustomerRequest("Ana Cruz", null, null, null, null), "alice");
    entitlements.add(
        id,
        new Entitlement(id, EntitlementKind.SENIOR, "SC-999", LocalDate.of(2024, 1, 1), null),
        "bob");

    assertThat(events()).extracting(AuditEvent::getAction)
        .contains("CUSTOMER_CHANGED", "ENTITLEMENT_CHANGED");
    assertThat(events()).allSatisfy(e -> assertThat(e.getEntityRef()).isNotBlank());
  }

  /** FR-003/016: the supporting ID must not leak into the audit trail. */
  @Test
  void auditDetailNeverCarriesASupportingIdReference() {
    UUID id = customer("AU-2");
    entitlements.add(
        id,
        new Entitlement(id, EntitlementKind.SENIOR, "SC-SECRET-12345", LocalDate.of(2024, 1, 1), null),
        "bob");

    assertThat(events()).noneSatisfy(
        e -> assertThat(String.valueOf(e.getEntityRef())).contains("SC-SECRET-12345"));
    // The detail column is scrubbed by AuditWriter; assert the raw value appears nowhere.
    assertThat(audit.findAll().toString()).doesNotContain("SC-SECRET-12345");
  }

  @Test
  void ruleAndPolicyChangesAreAudited() {
    rules.create(
        new DiscountRule(
            "P10", DiscountOrigin.PROMOTIONAL, DiscountComputationKind.PERCENT,
            new BigDecimal("0.10"), VatTreatment.NONE, 1, LocalDate.of(2026, 1, 1)),
        "carol");
    rules.setPolicy(StackingMode.STATUTORY_ONLY, "carol");

    assertThat(events()).extracting(AuditEvent::getAction).contains("RULE_CHANGED", "POLICY_CHANGED");
  }

  /** A no-op update must not manufacture an audit event. */
  @Test
  void unchangedUpdateIsNotAudited() {
    UUID id = customer("AU-3");
    long before = events().size();
    customers.update(id, new UpdateCustomerRequest("Ana", null, null, null, null), "alice");
    assertThat(events()).hasSize((int) before);
  }

  @Test
  void statusChangeIsAudited() {
    UUID id = customer("AU-4");
    customers.update(id, new UpdateCustomerRequest(null, null, null, null, CustomerStatus.INACTIVE), "alice");
    assertThat(events()).extracting(AuditEvent::getAction).contains("CUSTOMER_CHANGED");
  }
}
