package com.kita.crm.discount;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.crm.customer.CreateCustomerRequest;
import com.kita.crm.customer.CustomerService;
import com.kita.crm.customer.CustomerType;
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

/**
 * T031/T032 (SC-003/FR-014): statutory tiers with VAT treatment, the stacking policy modes, and
 * withholding when the supporting ID is missing.
 *
 * <p>Rules are created here rather than relying on the V5 seed because AbstractCrmIT truncates
 * discount_rule for isolation; PhSeedIT covers the seed's own values.
 */
class StatutoryDiscountIT extends AbstractCrmIT {

  @Autowired private CustomerService customers;
  @Autowired private EntitlementService entitlements;
  @Autowired private DiscountRuleService ruleService;
  @Autowired private DiscountComputationService computation;

  private static final LocalDate SALE = LocalDate.of(2026, 3, 1);

  private UUID customer(String code) {
    return customers
        .create(new CreateCustomerRequest(code, CustomerType.INDIVIDUAL, "Ana", null, null, null), "crm")
        .getId();
  }

  /** PH senior: VAT-exempt (12%) then 20% off the VAT-exclusive base. */
  private void phSeniorRule() {
    ruleService.create(
        new DiscountRule(
            "PH_SENIOR", DiscountOrigin.STATUTORY, DiscountComputationKind.PERCENT,
            new BigDecimal("0.20"), VatTreatment.VAT_EXEMPT, new BigDecimal("0.12"),
            EntitlementKind.SENIOR, 1, LocalDate.of(2024, 1, 1)),
        "crm");
  }

  private void promoRule(String code, String value, int priority) {
    ruleService.create(
        new DiscountRule(
            code, DiscountOrigin.PROMOTIONAL, DiscountComputationKind.PERCENT,
            new BigDecimal(value), VatTreatment.NONE, priority, LocalDate.of(2024, 1, 1)),
        "crm");
  }

  private void entitle(UUID id, EntitlementKind kind, String supportingId) {
    entitlements.add(id, new Entitlement(id, kind, supportingId, LocalDate.of(2024, 1, 1), null), "crm");
  }

  private DiscountComputationService.Computation compute(UUID id, String base) {
    return computation.compute(
        id, SALE, List.of(new DiscountComputationService.LineItem("SKU", BigDecimal.ONE, new BigDecimal(base))));
  }

  /**
   * Golden: a VAT-inclusive 1120 for a senior → VAT of 120 removed, then 20% of the remaining 1000 →
   * 800. The breakdown still reconciles: 1120 − 120 − 200 = 800.
   */
  @Test
  void seniorGetsVatExemptionThenTwentyPercentOffTheVatExclusiveBase() {
    phSeniorRule();
    UUID id = customer("ST-1");
    entitle(id, EntitlementKind.SENIOR, "SC-123");

    DiscountComputationService.Computation c = compute(id, "1120.00");

    assertThat(c.finalPrice()).isEqualByComparingTo("800.00");
    assertThat(c.breakdown()).hasSize(2);
    assertThat(c.breakdown().get(0).tierCode()).isEqualTo("PH_SENIOR_VAT_EXEMPT");
    assertThat(c.breakdown().get(0).amountRemoved()).isEqualByComparingTo("120.00");
    assertThat(c.breakdown().get(1).tierCode()).isEqualTo("PH_SENIOR");
    assertThat(c.breakdown().get(1).baseApplied()).isEqualByComparingTo("1000.00");
    assertThat(c.breakdown().get(1).amountRemoved()).isEqualByComparingTo("200.00");

    BigDecimal removed = c.breakdown().stream()
        .map(CascadingEngine.BreakdownLine::amountRemoved)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(c.baseTotal().subtract(removed)).isEqualByComparingTo(c.finalPrice());
  }

  /** FR-014: entitled on paper but no supporting ID → statutory withheld and flagged. */
  @Test
  void entitlementWithoutSupportingIdIsWithheldAndFlagged() {
    phSeniorRule();
    UUID id = customer("ST-2");
    entitle(id, EntitlementKind.SENIOR, null);

    DiscountComputationService.Computation c = compute(id, "1120.00");

    assertThat(c.finalPrice()).isEqualByComparingTo("1120.00");
    assertThat(c.breakdown()).isEmpty();
    assertThat(c.flags()).contains(DiscountComputationService.FLAG_ENTITLEMENT_WITHHELD);
  }

  /** A statutory rule only fires for its own entitlement kind. */
  @Test
  void seniorRuleDoesNotApplyToAPwdOnlyCustomer() {
    phSeniorRule();
    UUID id = customer("ST-3");
    entitle(id, EntitlementKind.PWD, "PWD-9");

    DiscountComputationService.Computation c = compute(id, "1120.00");

    assertThat(c.finalPrice()).isEqualByComparingTo("1120.00");
    assertThat(c.breakdown()).isEmpty();
  }

  @Test
  void expiredEntitlementIsNotHonoured() {
    phSeniorRule();
    UUID id = customer("ST-4");
    entitlements.add(
        id,
        new Entitlement(
            id, EntitlementKind.SENIOR, "SC-1", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)),
        "crm");

    assertThat(compute(id, "1120.00").finalPrice()).isEqualByComparingTo("1120.00");
  }

  /** SC-003 / FR-013: MOST_FAVORABLE takes the better path, never both stacked. */
  @Test
  void mostFavorableTakesStatutoryWhenItBeatsThePromo() {
    phSeniorRule();
    promoRule("P5", "0.05", 50); // 5% → 1064.00, worse than the senior path's 800.00
    UUID id = customer("ST-5");
    entitle(id, EntitlementKind.SENIOR, "SC-123");

    DiscountComputationService.Computation c = compute(id, "1120.00");

    assertThat(c.stackingMode()).isEqualTo(StackingMode.MOST_FAVORABLE);
    assertThat(c.finalPrice()).isEqualByComparingTo("800.00");
    assertThat(c.breakdown())
        .extracting(CascadingEngine.BreakdownLine::tierCode)
        .containsExactly("PH_SENIOR_VAT_EXEMPT", "PH_SENIOR");
  }

  @Test
  void mostFavorableTakesThePromoWhenItBeatsStatutory() {
    phSeniorRule();
    promoRule("P50", "0.50", 50); // 50% → 560.00, better than the senior path's 800.00
    UUID id = customer("ST-6");
    entitle(id, EntitlementKind.SENIOR, "SC-123");

    DiscountComputationService.Computation c = compute(id, "1120.00");

    assertThat(c.finalPrice()).isEqualByComparingTo("560.00");
    assertThat(c.breakdown()).extracting(CascadingEngine.BreakdownLine::tierCode).containsExactly("P50");
  }

  /** STATUTORY_ONLY ignores promotional tiers entirely. */
  @Test
  void statutoryOnlyIgnoresPromotionalTiers() {
    phSeniorRule();
    promoRule("P50", "0.50", 50);
    ruleService.setPolicy(StackingMode.STATUTORY_ONLY, "crm");
    UUID id = customer("ST-7");
    entitle(id, EntitlementKind.SENIOR, "SC-123");

    DiscountComputationService.Computation c = compute(id, "1120.00");

    assertThat(c.stackingMode()).isEqualTo(StackingMode.STATUTORY_ONLY);
    assertThat(c.finalPrice()).isEqualByComparingTo("800.00");
  }

  /** STATUTORY_THEN_PROMO is one continuous cascade in that order. */
  @Test
  void statutoryThenPromoRunsBothInOrder() {
    phSeniorRule();
    promoRule("P10", "0.10", 50);
    ruleService.setPolicy(StackingMode.STATUTORY_THEN_PROMO, "crm");
    UUID id = customer("ST-8");
    entitle(id, EntitlementKind.SENIOR, "SC-123");

    DiscountComputationService.Computation c = compute(id, "1120.00");

    // 1120 −120(VAT) = 1000 −200(senior) = 800 −80(10%) = 720
    assertThat(c.finalPrice()).isEqualByComparingTo("720.00");
    assertThat(c.breakdown())
        .extracting(CascadingEngine.BreakdownLine::tierCode)
        .containsExactly("PH_SENIOR_VAT_EXEMPT", "PH_SENIOR", "P10");
  }

  /** PROMO_THEN_STATUTORY reverses the order, and the VAT fraction still works on the running base. */
  @Test
  void promoThenStatutoryRunsBothInTheOppositeOrder() {
    phSeniorRule();
    promoRule("P10", "0.10", 50);
    ruleService.setPolicy(StackingMode.PROMO_THEN_STATUTORY, "crm");
    UUID id = customer("ST-9");
    entitle(id, EntitlementKind.SENIOR, "SC-123");

    DiscountComputationService.Computation c = compute(id, "1120.00");

    // 1120 −112(10%) = 1008 −108(VAT on 1008) = 900 −180(senior 20%) = 720
    assertThat(c.breakdown())
        .extracting(CascadingEngine.BreakdownLine::tierCode)
        .containsExactly("P10", "PH_SENIOR_VAT_EXEMPT", "PH_SENIOR");
    assertThat(c.finalPrice()).isEqualByComparingTo("720.00");

    BigDecimal removed = c.breakdown().stream()
        .map(CascadingEngine.BreakdownLine::amountRemoved)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(c.baseTotal().subtract(removed)).isEqualByComparingTo(c.finalPrice());
  }

  /** A walk-in never gets a statutory discount and is never flagged for a missing ID. */
  @Test
  void walkInGetsNoStatutoryDiscountAndNoWithheldFlag() {
    phSeniorRule();
    DiscountComputationService.Computation c = compute(null, "1120.00");

    assertThat(c.finalPrice()).isEqualByComparingTo("1120.00");
    assertThat(c.flags()).isEmpty();
  }
}
